package com.asterion.foundry;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/** One editable clip on a Foundry rig with translated rest joints and linear skin binding. */
public final class SkeletalMotion {
    public static final String VERSION = "skeletal-motion-v2";
    public static final int MAX_SCRIPT_CHARS = 96 * 1024;
    private static final int MAX_JOINTS = 512, MAX_KEYS = 2048, MAX_CONTACTS = 64;
    private static final int SAMPLE_RATE = 30, MAX_SECONDS = 60;

    private record Joint(String name, int parent, float x, float y, float z) {}
    private record Target(int joint, boolean rotation) {}
    private record Contact(int a, int b, float from, float to, float tolerance) {}
    record Track(int joint, boolean rotation, float[] times, float[][] values) {
        int components() { return rotation ? 4 : 3; }
        String path() { return rotation ? "rotation" : "translation"; }

        void sample(float time, double[] result) {
            int index = Arrays.binarySearch(times, time);
            if (index >= 0) {
                copy(values[index], result);
                return;
            }
            int next = -index - 1;
            if (next == 0 || next == times.length) {
                copy(values[next == 0 ? 0 : next - 1], result);
                return;
            }
            float[] a = values[next - 1], b = values[next];
            double t = ((double) time - times[next - 1]) / ((double) times[next] - times[next - 1]);
            double left = 1 - t, right = t;
            if (rotation) {
                double dot = (double) a[0] * b[0] + (double) a[1] * b[1]
                        + (double) a[2] * b[2] + (double) a[3] * b[3];
                if (Math.abs(dot) < 0.9995) {
                    double angle = Math.acos(Math.abs(dot)), sine = Math.sin(angle);
                    left = Math.sin(left * angle) / sine;
                    right = Math.sin(right * angle) / sine;
                }
                if (dot < 0) right = -right;
            }
            for (int axis = 0; axis < components(); axis++) result[axis] = left * a[axis] + right * b[axis];
        }
    }

    public record Validation(String clip, float durationSeconds, int tracks, int joints,
                             int triangles, int samples, int contacts, double maxContactGapMetres,
                             boolean blended, int deformationChecks) {
        public String json() {
            return "{\"schema\":\"asterion.foundry.motion.v1\",\"clip\":" + FilesUtil.quote(clip)
                    + ",\"duration_seconds\":" + durationSeconds + ",\"tracks\":" + tracks
                    + ",\"joints\":" + joints + ",\"samples\":" + samples
                    + ",\"uniform_sample_rate_hz\":" + SAMPLE_RATE + ",\"contact_constraints\":" + contacts
                    + ",\"max_contact_gap_metres\":" + (contacts == 0 ? "null" : maxContactGapMetres)
                    + (blended ? ",\"skinned_triangles\":" : ",\"rigid_triangles\":") + triangles
                    + ",\"deformation\":\"" + (blended ? "sampled_blended_triangle_areas_checked"
                    : "per_triangle_rigid_transform_checked") + "\""
                    + ",\"blended_triangle_pose_checks\":" + deformationChecks
                    + ",\"contact_method\":\"joint_origin_distance_at_samples\"}";
        }
    }

    private final String name;
    private final List<Joint> rig;
    private final List<Track> tracks;
    private final List<Contact> contacts;
    private final float duration;
    private final float[][] rest;

    private SkeletalMotion(String name, List<Joint> rig, List<Track> tracks, List<Contact> contacts,
                           float duration, float[][] rest) {
        this.name = name;
        this.rig = rig;
        this.tracks = tracks;
        this.contacts = contacts;
        this.duration = duration;
        this.rest = rest;
    }

    public String name() { return name; }
    public float durationSeconds() { return duration; }
    List<Track> tracks() { return tracks; }

    /** Parse clip, axis-angle rotation, local displacement, and optional contact directives. */
    public static SkeletalMotion parse(Mesh mesh, String script) {
        if (script == null || script.length() > MAX_SCRIPT_CHARS) {
            throw new IllegalArgumentException("Motion script exceeds " + MAX_SCRIPT_CHARS + " characters");
        }
        List<Joint> rig = readRig(mesh);
        float[][] rest = new float[rig.size()][3];
        for (int i = 0; i < rig.size(); i++) {
            Joint joint = rig.get(i);
            Joint parent = joint.parent < 0 ? new Joint("", -1, 0, 0, 0) : rig.get(joint.parent);
            rest[i] = new float[]{joint.x - parent.x, joint.y - parent.y, joint.z - parent.z};
            for (float value : rest[i]) {
                if (!Float.isFinite(value)) throw new IllegalArgumentException("Non-finite local rest transform");
            }
        }
        Map<Target, TreeMap<Float, float[]>> keys = new LinkedHashMap<>();
        List<Contact> contacts = new ArrayList<>();
        String name = null;
        int keyCount = 0;
        float duration = 0;
        String[] lines = script.split("\\r\\n|\\n|\\r");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] parts = line.split("\\s+");
            try {
                if (name == null) {
                    if (!parts[0].equals("clip") || parts.length < 2) {
                        throw new IllegalArgumentException("Start with clip followed by a name");
                    }
                    name = line.substring(4).trim();
                    if (name.length() > 100 || name.chars().anyMatch(Character::isISOControl)) {
                        throw new IllegalArgumentException("Clip name must use at most 100 printable characters");
                    }
                    continue;
                }
                switch (parts[0]) {
                    case "move", "rotate" -> {
                        boolean rotation = parts[0].equals("rotate");
                        if (parts.length != (rotation ? 7 : 6)) {
                            throw new IllegalArgumentException(rotation
                                    ? "Use rotate JOINT SECONDS AXIS_X AXIS_Y AXIS_Z DEGREES"
                                    : "Use move JOINT SECONDS OFFSET_X OFFSET_Y OFFSET_Z");
                        }
                        if (++keyCount > MAX_KEYS) throw new IllegalArgumentException("Too many motion keys");
                        int index = resolve(rig, parts[1]);
                        float time = time(parts[2]);
                        float x = number(parts[3]), y = number(parts[4]), z = number(parts[5]);
                        float[] value;
                        if (rotation) {
                            double axisLength = Math.sqrt((double) x*x + (double) y*y + (double) z*z);
                            float angle = number(parts[6]);
                            if (axisLength == 0 || Math.abs(angle) > 360) {
                                throw new IllegalArgumentException("Rotation needs a nonzero axis and degrees within ±360");
                            }
                            double radians = Math.toRadians(angle) / 2;
                            double scale = Math.sin(radians) / axisLength;
                            value = new float[]{(float) (scale*x), (float) (scale*y), (float) (scale*z),
                                    (float) Math.cos(radians)};
                        } else {
                            if (Math.max(Math.abs(x), Math.max(Math.abs(y), Math.abs(z))) > 1000) {
                                throw new IllegalArgumentException("Local offsets must be within ±1000 metres");
                            }
                            value = new float[]{rest[index][0] + x, rest[index][1] + y, rest[index][2] + z};
                        }
                        TreeMap<Float, float[]> track = keys.computeIfAbsent(new Target(index, rotation),
                                ignored -> new TreeMap<>());
                        if (track.putIfAbsent(time, value) != null) {
                            throw new IllegalArgumentException("Duplicate time for this joint and transform");
                        }
                        duration = Math.max(duration, time);
                    }
                    case "contact" -> {
                        if (parts.length != 6) {
                            throw new IllegalArgumentException("Use contact JOINT_A JOINT_B FROM TO MAX_GAP_METRES");
                        }
                        if (contacts.size() == MAX_CONTACTS) throw new IllegalArgumentException("Too many contacts");
                        int a = resolve(rig, parts[1]), b = resolve(rig, parts[2]);
                        float from = time(parts[3]), to = time(parts[4]), tolerance = number(parts[5]);
                        if (a == b || to < from || tolerance <= 0 || tolerance > 1000) {
                            throw new IllegalArgumentException("Contact needs distinct joints, ordered times and a positive gap up to 1000 metres");
                        }
                        contacts.add(new Contact(a, b, from, to, tolerance));
                    }
                    default -> throw new IllegalArgumentException("Unknown motion directive " + parts[0]);
                }
            } catch (IllegalArgumentException failure) {
                throw new IllegalArgumentException("Motion line " + (i + 1) + ": " + failure.getMessage(), failure);
            }
        }
        if (duration == 0) throw new IllegalArgumentException("Add a motion key after 0 seconds");
        for (Contact contact : contacts) {
            if (contact.to > duration) throw new IllegalArgumentException("Contact extends beyond the final motion key");
        }
        List<Track> tracks = new ArrayList<>();
        for (Map.Entry<Target, TreeMap<Float, float[]>> entry : keys.entrySet()) {
            Target target = entry.getKey();
            TreeMap<Float, float[]> frames = entry.getValue();
            frames.putIfAbsent(0f, target.rotation ? new float[]{0, 0, 0, 1} : rest[target.joint]);
            float[] times = new float[frames.size()];
            int i = 0;
            for (float time : frames.keySet()) times[i++] = time;
            tracks.add(new Track(target.joint, target.rotation, times, frames.values().toArray(new float[0][])));
        }
        return new SkeletalMotion(name, rig, Collections.unmodifiableList(tracks), contacts, duration, rest);
    }

    /** Composes a rig-specific preset from one of the supported short descriptions. */
    public static String compose(Mesh mesh, String description) {
        if (description == null || description.length() > 120) {
            throw new IllegalArgumentException("Use a short motion description");
        }
        List<Joint> rig = readRig(mesh);
        String request = description.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        if (request.endsWith(".")) request = request.substring(0, request.length() - 1);
        StringBuilder script;
        switch (request) {
            case "idle", "idle loop", "breathing idle", "standing idle" -> {
                int joint = unique(rig, "chest");
                if (joint < 0) joint = unique(rig, "neck");
                if (joint < 0) joint = 0;
                script = new StringBuilder("clip Breathing idle\n");
                String name = jointRef(rig, joint);
                rotationKey(script, name, 0, 1, 0, 0, 0);
                rotationKey(script, name, 1, 1, 0, 0, 2);
                rotationKey(script, name, 2, 1, 0, 0, 0);
            }
            case "wag", "tail wag", "wag tail", "wag the tail", "wag its tail" -> {
                int joint = unique(rig, "tail");
                if (joint < 0 || rig.get(joint).parent < 0) {
                    throw new IllegalArgumentException("Tail wag requires a unique articulated tail joint");
                }
                script = new StringBuilder("clip Tail wag\n");
                float[] times = {0, 0.3f, 0.6f, 0.9f, 1.2f};
                int[] angles = {0, 24, 0, -24, 0};
                for (int i = 0; i < times.length; i++) rotationKey(script, "tail", times[i], 0, 1, 0, angles[i]);
            }
            case "walk", "walk cycle", "walk in place", "walking in place" -> {
                script = new StringBuilder("clip Walk in place\n");
                int root = walkJoint(rig, "root", -1), hips = walkJoint(rig, "hips", root);
                int chest = walkJoint(rig, "chest", hips);
                float[] times = {0, 0.3f, 0.6f, 0.9f, 1.2f};
                int[] thigh = {-22, 2, 18, -2}, knee = {4, 6, 17, 43};
                for (int side = 0; side < 2; side++) {
                    String suffix = side == 0 ? "_L" : "_R";
                    int leg = walkJoint(rig, "upper_leg" + suffix, hips);
                    walkJoint(rig, "lower_leg" + suffix, leg);
                    walkJoint(rig, "upper_arm" + suffix, chest);
                    for (int i = 0; i < times.length; i++) {
                        int phase = (i + 2 * side) % thigh.length;
                        rotationKey(script, "upper_leg" + suffix, times[i], 1, 0, 0, thigh[phase]);
                        rotationKey(script, "lower_leg" + suffix, times[i], 1, 0, 0, knee[phase]);
                        swingArm(script, "upper_arm" + suffix, times[i], -0.65 * thigh[phase], side == 0 ? 77 : -77);
                    }
                }
                for (int i = 0; i < times.length; i++) {
                    rotationKey(script, "chest", times[i], 0, 1, 0, 4 * Math.cos(i * Math.PI / 2));
                }
            }
            default -> throw new IllegalArgumentException("Use idle, walk in place, or tail wag");
        }
        return script.toString();
    }

    private static int walkJoint(List<Joint> rig, String name, int parent) {
        int index = unique(rig, name);
        if (index < 0 || rig.get(index).parent != parent) {
            throw new IllegalArgumentException("Walk in place requires the Foundry humanoid hierarchy");
        }
        return index;
    }

    private static String jointRef(List<Joint> rig, int index) {
        String name = rig.get(index).name;
        if (name.startsWith("@") || name.chars().anyMatch(Character::isWhitespace) || unique(rig, name) < 0) {
            return "@" + index;
        }
        return name;
    }

    private static void swingArm(StringBuilder script, String joint, float time, double pitch, double roll) {
        // Lower the arm from the T pose, then swing it forward or back about X.
        double p = Math.toRadians(pitch) / 2, r = Math.toRadians(roll) / 2;
        double x = Math.sin(p) * Math.cos(r), y = -Math.sin(p) * Math.sin(r), z = Math.cos(p) * Math.sin(r);
        double w = Math.cos(p) * Math.cos(r);
        rotationKey(script, joint, time, x, y, z, Math.toDegrees(2 * Math.atan2(Math.sqrt(x*x + y*y + z*z), w)));
    }

    private static void rotationKey(StringBuilder script, String joint, float time,
                                    double x, double y, double z, double angle) {
        script.append("rotate ").append(joint).append(' ').append(time).append(' ')
                .append(x).append(' ').append(y).append(' ').append(z).append(' ').append(angle).append('\n');
    }

    public Validation validate(Mesh mesh) {
        requireRig(mesh);
        try {
            mesh.validate();
        } catch (IllegalStateException invalid) {
            throw new IllegalArgumentException(invalid.getMessage(),invalid);
        }
        boolean blended = mesh.blended();
        double[] radii = new double[rig.size()];
        for (int i = 0; i < mesh.vertices.size(); i += 3) {
            int joint = (int) mesh.vertices.get(i)[12];
            for (int k = i; k < i + 3; k++) {
                float[] v = mesh.vertices.get(k);
                if (!blended && v[12] != joint) throw new IllegalArgumentException("Rigid motion requires one joint per triangle");
                for (int slot = 0; slot < Mesh.INFLUENCES; slot++) {
                    if (Mesh.weight(v,slot) == 0) continue;
                    int index = Mesh.joint(v,slot);
                    Joint bone = rig.get(index);
                    double x = (double) v[0] - bone.x, y = (double) v[1] - bone.y, z = (double) v[2] - bone.z;
                    radii[index] = Math.max(radii[index], Math.sqrt(x*x + y*y + z*z));
                }
            }
        }
        TreeSet<Float> times = new TreeSet<>();
        times.add(duration);
        for (int i = 0; i < Math.ceil(duration * SAMPLE_RATE); i++) times.add(i / (float) SAMPLE_RATE);
        for (Track track : tracks) for (float time : track.times) times.add(time);
        for (Contact contact : contacts) {
            times.add(contact.from);
            times.add(contact.to);
        }
        double maxGap = 0;
        Frame frame = new Frame();
        int samples = 0, deformationChecks = 0;
        int poseStep = Math.max(1,(times.size()+119)/120);
        List<Integer> transitions = blended ? transitionTriangles(mesh) : List.of();
        float[][] posed = {new float[3],new float[3],new float[3]};
        double[] scratch = new double[3];
        for (float time : times) {
            frame.sample(time);
            for (int i = 0; i < rig.size(); i++) {
                double[] q = frame.rotations[i];
                double norm = q[0]*q[0] + q[1]*q[1] + q[2]*q[2] + q[3]*q[3];
                if (!(Math.abs(norm - 1) < 1e-8)) throw new IllegalArgumentException("Invalid sampled rotation");
                for (double coordinate : frame.positions[i]) {
                    if (!(Math.abs(coordinate) + radii[i] <= Float.MAX_VALUE)) {
                        throw new IllegalArgumentException("Motion exceeds finite deformation range at " + time + " seconds");
                    }
                }
            }
            if (samples++ % poseStep == 0 || time == duration) {
                for (int triangle : transitions) {
                    for (int k = 0; k < 3; k++) {
                        transform(frame,mesh.vertices.get(triangle+k),posed[k],scratch,false);
                    }
                    double before = areaSquared(mesh.vertices.get(triangle),mesh.vertices.get(triangle+1),mesh.vertices.get(triangle+2));
                    if (before > 0 && !(areaSquared(posed[0],posed[1],posed[2]) > before*1e-6)) {
                        throw new IllegalArgumentException("Blended skin collapses a sampled triangle at " + time + " seconds");
                    }
                    deformationChecks++;
                }
            }
            for (Contact contact : contacts) {
                if (time < contact.from || time > contact.to) continue;
                double[] a = frame.positions[contact.a], b = frame.positions[contact.b];
                double x = a[0] - b[0], y = a[1] - b[1], z = a[2] - b[2];
                double gap = Math.sqrt(x*x + y*y + z*z);
                if (gap > contact.tolerance) {
                    throw new IllegalArgumentException("Contact " + rig.get(contact.a).name + " / "
                            + rig.get(contact.b).name + " misses at " + time + " seconds by " + gap
                            + " metres (limit " + contact.tolerance + ")");
                }
                maxGap = Math.max(maxGap, gap);
            }
        }
        return new Validation(name, duration, tracks.size(), rig.size(), mesh.vertices.size() / 3,
                times.size(), contacts.size(), maxGap, blended, deformationChecks);
    }

    private static List<Integer> transitionTriangles(Mesh mesh) {
        List<Integer> transitions = new ArrayList<>();
        for (int i = 0; i < mesh.vertices.size(); i += 3) {
            float[] a = mesh.vertices.get(i), b = mesh.vertices.get(i+1), c = mesh.vertices.get(i+2);
            int joint = Mesh.joint(a,0);
            if (Mesh.weight(a,0)!=1 || Mesh.weight(b,0)!=1 || Mesh.weight(c,0)!=1
                    || Mesh.joint(b,0)!=joint || Mesh.joint(c,0)!=joint) transitions.add(i);
        }
        if (transitions.size() <= 128) return transitions;
        List<Integer> samples = new ArrayList<>(128);
        for (int i = 0; i < 128; i++) samples.add(transitions.get(i * (transitions.size()-1) / 127));
        return samples;
    }

    private static double areaSquared(float[] a,float[] b,float[] c) {
        double ux=(double)b[0]-a[0],uy=(double)b[1]-a[1],uz=(double)b[2]-a[2];
        double vx=(double)c[0]-a[0],vy=(double)c[1]-a[1],vz=(double)c[2]-a[2];
        double x=uy*vz-uz*vy,y=uz*vx-ux*vz,z=ux*vy-uy*vx;
        return x*x+y*y+z*z;
    }

    /** Creates a reusable sampler for a validated clip. Each instance is confined to one thread. */
    public Playback playback(Mesh mesh) {
        requireRig(mesh);
        return new Playback(mesh);
    }

    public final class Playback {
        private final Mesh mesh;
        private final Frame frame = new Frame();
        private final double[] scratch = new double[3];
        private final float[] vertex = new float[6];

        private Playback(Mesh mesh) { this.mesh = mesh; }

        /** Writes interleaved positions, normals, UVs and colours into the supplied preview buffer. */
        public void write(float seconds, FloatBuffer output) {
            if (output.remaining() < mesh.vertices.size() * 12) throw new IllegalArgumentException("Preview buffer is too small");
            sample(seconds);
            for (float[] source : mesh.vertices) {
                transform(frame,source,vertex,scratch,true);
                output.put(vertex).put(source, 6, 6);
            }
        }

        private void sample(float seconds) {
            if (!Float.isFinite(seconds)) throw new IllegalArgumentException("Playback time must be finite");
            frame.sample(Math.max(0, Math.min(seconds, duration)));
        }

    }

    private void transform(Frame frame,float[] source,float[] output,double[] scratch,boolean normals) {
        Arrays.fill(output,0);
        for (int slot = 0; slot < Mesh.INFLUENCES; slot++) {
            float weight = Mesh.weight(source,slot);
            if (weight == 0) continue;
            int i = Mesh.joint(source,slot);
            Joint joint = rig.get(i);
            double[] rotation = frame.rotations[i], position = frame.positions[i];
            rotate(rotation,(double)source[0]-joint.x,(double)source[1]-joint.y,(double)source[2]-joint.z,scratch);
            for (int axis = 0; axis < 3; axis++) output[axis] += (float)(weight*(scratch[axis]+position[axis]));
            if (!normals) continue;
            rotate(rotation,source[3],source[4],source[5],scratch);
            for (int axis = 0; axis < 3; axis++) output[axis+3] += (float)(weight*scratch[axis]);
        }
        if (normals) {
            double length = Math.sqrt(output[3]*output[3]+output[4]*output[4]+output[5]*output[5]);
            if (length < 1e-8) System.arraycopy(source,3,output,3,3);
            else for (int axis = 3; axis < 6; axis++) output[axis] /= (float)length;
        }
    }

    private final class Frame {
        final double[][] positions = new double[rig.size()][3];
        final double[][] rotations = new double[rig.size()][4];
        private final double[] rotated = new double[3];

        void sample(float time) {
            for (int i = 0; i < rig.size(); i++) {
                copy(rest[i], positions[i]);
                Arrays.fill(rotations[i], 0);
                rotations[i][3] = 1;
            }
            for (Track track : tracks) track.sample(time,
                    track.rotation ? rotations[track.joint] : positions[track.joint]);
            for (int i = 0; i < rig.size(); i++) {
                int parent = rig.get(i).parent;
                double[] p = positions[i], q = rotations[i];
                if (parent >= 0) {
                    double[] parentRotation = rotations[parent];
                    rotate(parentRotation, p[0], p[1], p[2], rotated);
                    for (int axis = 0; axis < 3; axis++) p[axis] = rotated[axis] + positions[parent][axis];
                    multiply(parentRotation, q);
                }
                double length = Math.sqrt(q[0]*q[0] + q[1]*q[1] + q[2]*q[2] + q[3]*q[3]);
                for (int axis = 0; axis < 4; axis++) q[axis] /= length;
            }
        }
    }

    private static void copy(float[] source, double[] target) {
        for (int axis = 0; axis < source.length; axis++) target[axis] = source[axis];
    }

    private static void rotate(double[] q, double x, double y, double z, double[] out) {
        double tx = 2 * (q[1]*z - q[2]*y), ty = 2 * (q[2]*x - q[0]*z), tz = 2 * (q[0]*y - q[1]*x);
        out[0] = x + q[3]*tx + q[1]*tz - q[2]*ty;
        out[1] = y + q[3]*ty + q[2]*tx - q[0]*tz;
        out[2] = z + q[3]*tz + q[0]*ty - q[1]*tx;
    }

    /** Concatenate parent and child rotations, storing the world rotation in the child. */
    private static void multiply(double[] a, double[] b) {
        double x = b[0], y = b[1], z = b[2], w = b[3];
        b[0] = a[3]*x + a[0]*w + a[1]*z - a[2]*y;
        b[1] = a[3]*y - a[0]*z + a[1]*w + a[2]*x;
        b[2] = a[3]*z + a[0]*y - a[1]*x + a[2]*w;
        b[3] = a[3]*w - a[0]*x - a[1]*y - a[2]*z;
    }

    private void requireRig(Mesh mesh) {
        if (!rig.equals(readRig(mesh))) throw new IllegalArgumentException("Clip was compiled for a different rest rig");
    }

    private static List<Joint> readRig(Mesh mesh) {
        if (mesh.bones.isEmpty() || mesh.bones.size() > MAX_JOINTS) {
            throw new IllegalArgumentException("Motion needs a Foundry rig with 1 to " + MAX_JOINTS + " joints");
        }
        List<Joint> result = new ArrayList<>();
        for (int i = 0; i < mesh.bones.size(); i++) {
            Mesh.Bone bone = mesh.bones.get(i);
            if (bone.name == null || bone.name.trim().isEmpty() || bone.name.chars().anyMatch(Character::isISOControl)
                    || bone.parent < -1 || bone.parent >= i || !Float.isFinite(bone.x)
                    || !Float.isFinite(bone.y) || !Float.isFinite(bone.z)) {
                throw new IllegalArgumentException("Invalid rest rig at joint " + i);
            }
            result.add(new Joint(bone.name, bone.parent, bone.x, bone.y, bone.z));
        }
        return result;
    }

    private static int resolve(List<Joint> rig, String name) {
        if (name.startsWith("@")) {
            try {
                int index = Integer.parseInt(name.substring(1));
                if (index >= 0 && index < rig.size()) return index;
            } catch (NumberFormatException ignored) {}
            throw new IllegalArgumentException("Invalid zero-based joint index " + name);
        }
        int index = unique(rig, name);
        if (index == -2) throw new IllegalArgumentException("Ambiguous joint " + name + "; use its @index");
        if (index < 0) throw new IllegalArgumentException("Unknown joint " + name);
        return index;
    }

    private static int unique(List<Joint> rig, String name) {
        int index = -1;
        for (int i = 0; i < rig.size(); i++) {
            if (!rig.get(i).name.equals(name)) continue;
            if (index >= 0) return -2;
            index = i;
        }
        return index;
    }

    private static float number(String token) {
        float value = Float.parseFloat(token);
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Motion numbers must be finite");
        return value == 0 ? 0 : value;
    }

    private static float time(String token) {
        float value = number(token);
        if (value < 0 || value > MAX_SECONDS) {
            throw new IllegalArgumentException("Time must be between 0 and " + MAX_SECONDS + " seconds");
        }
        return value;
    }
}
