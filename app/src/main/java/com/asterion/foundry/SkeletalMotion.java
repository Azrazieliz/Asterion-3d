package com.asterion.foundry;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/** One editable clip on a Foundry rig or assembly with translated rest joints and linear skin binding. */
public final class SkeletalMotion {
    public static final String VERSION = "skeletal-motion-v3";
    public static final int MAX_SCRIPT_CHARS = 96 * 1024;
    private static final int MAX_JOINTS = 512, MAX_KEYS = 2048, MAX_CONTACTS = 64;
    private static final int MAX_STAGES = 256, MAX_BAKED_KEYS = 32768;
    private static final int SAMPLE_RATE = 30, MAX_SECONDS = 600;

    private record Joint(String name, int parent, float x, float y, float z) {}
    private record Target(int joint, boolean rotation) {}
    private record Contact(int a, int b, float from, float to, float tolerance) {}
    private record Stage(int line, int root, float from, float to, String action) {}
    private record Arc(int line, int root, float from, float to, float vx, float vy, float vz, float gravity) {}
    private enum DragonStage { FLIGHT, LANDING, PERCH }
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
                             boolean blended, int deformationChecks, int actors, int stages, int ballisticArcs) {
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
                    + ",\"actors\":" + actors + ",\"stages\":" + stages + ",\"ballistic_arcs\":" + ballisticArcs
                    + ",\"contact_method\":\"joint_origin_distance_at_samples\"}";
        }
    }

    private final String name;
    private final List<Joint> rig;
    private final List<Track> tracks;
    private final List<Contact> contacts;
    private final float duration;
    private final float[][] rest;
    private final List<Stage> stages;
    private final int actors;
    private final int ballisticArcs;

    private SkeletalMotion(String name, List<Joint> rig, List<Track> tracks, List<Contact> contacts,
                           float duration, float[][] rest, List<Stage> stages, int actors, int ballisticArcs) {
        this.name = name;
        this.rig = rig;
        this.tracks = tracks;
        this.contacts = contacts;
        this.duration = duration;
        this.rest = rest;
        this.stages = stages;
        this.actors = actors;
        this.ballisticArcs = ballisticArcs;
    }

    public String name() { return name; }
    public float durationSeconds() { return duration; }
    List<Track> tracks() { return tracks; }

    /** Parse authored poses, actor-scoped stages, ballistic root arcs, and optional contact checks. */
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
        Map<String, Integer> actors = new LinkedHashMap<>();
        List<Contact> contacts = new ArrayList<>();
        List<Stage> stages = new ArrayList<>();
        List<Arc> arcs = new ArrayList<>();
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
                    case "actor" -> {
                        if (parts.length != 3 || !parts[1].matches("[A-Za-z][A-Za-z0-9_-]{0,31}")) {
                            throw new IllegalArgumentException("Use actor NAME ROOT with a letter-led actor name");
                        }
                        int root = resolve(rig, parts[2]);
                        if (rig.get(root).parent >= 0) throw new IllegalArgumentException("Actor joint must be a rig root");
                        if (actors.containsKey(parts[1]) || actors.containsValue(root)) {
                            throw new IllegalArgumentException("Each actor needs a distinct name and root");
                        }
                        actors.put(parts[1], root);
                    }
                    case "stage" -> {
                        if (parts.length < 5) throw new IllegalArgumentException("Use stage ACTOR FROM TO ACTION");
                        if (stages.size() == MAX_STAGES) throw new IllegalArgumentException("Too many choreography stages");
                        Integer root = actors.get(parts[1]);
                        if (root == null) throw new IllegalArgumentException("Declare actor " + parts[1] + " before its stage");
                        float from = time(parts[2]), to = time(parts[3]);
                        if ((double) to - from < 1d / SAMPLE_RATE) {
                            throw new IllegalArgumentException("Stage needs at least 1/30 second of positive duration");
                        }
                        String action = String.join(" ", Arrays.copyOfRange(parts, 4, parts.length));
                        stages.add(new Stage(i + 1, root, from, to, action));
                        duration = Math.max(duration, to);
                    }
                    case "ballistic" -> {
                        if (parts.length != 8) {
                            throw new IllegalArgumentException("Use ballistic ROOT FROM TO VX VY VZ GRAVITY");
                        }
                        if (arcs.size() == MAX_STAGES) throw new IllegalArgumentException("Too many ballistic arcs");
                        int root = resolve(rig, actors, parts[1]);
                        if (rig.get(root).parent >= 0) throw new IllegalArgumentException("Ballistic motion needs a rig root");
                        float from = time(parts[2]), to = time(parts[3]), gravity = number(parts[7]);
                        if (to <= from || gravity <= 0) {
                            throw new IllegalArgumentException("Ballistic motion needs positive duration and downward gravity");
                        }
                        arcs.add(new Arc(i + 1, root, from, to,
                                number(parts[4]), number(parts[5]), number(parts[6]), gravity));
                        duration = Math.max(duration, to);
                    }
                    case "move", "rotate" -> {
                        boolean rotation = parts[0].equals("rotate");
                        if (parts.length != (rotation ? 7 : 6)) {
                            throw new IllegalArgumentException(rotation
                                    ? "Use rotate JOINT SECONDS AXIS_X AXIS_Y AXIS_Z DEGREES"
                                    : "Use move JOINT SECONDS OFFSET_X OFFSET_Y OFFSET_Z");
                        }
                        if (++keyCount > MAX_KEYS) throw new IllegalArgumentException("Too many motion keys");
                        int index = resolve(rig, actors, parts[1]);
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
                        int a = resolve(rig, actors, parts[1]), b = resolve(rig, actors, parts[2]);
                        float from = time(parts[3]), to = time(parts[4]), tolerance = number(parts[5]);
                        if (a == b || to < from || tolerance <= 0 || tolerance > 1000) {
                            throw new IllegalArgumentException("Contact needs distinct joints, ordered times and a positive gap up to 1000 metres");
                        }
                        contacts.add(new Contact(a, b, from, to, tolerance));
                    }
                    default -> throw new IllegalArgumentException("Unknown motion directive " + parts[0]);
                }
            } catch (IllegalArgumentException failure) {
                throw lineFailure(i + 1, failure);
            }
        }
        if (duration == 0) throw new IllegalArgumentException("Add a motion key after 0 seconds");
        for (Contact contact : contacts) {
            if (contact.to > duration) throw new IllegalArgumentException("Contact extends beyond the final motion key");
        }
        bakeChoreography(mesh, rig, rest, keys, stages, arcs);
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
        return new SkeletalMotion(name, rig, Collections.unmodifiableList(tracks), contacts,
                duration, rest, stages, actors.size(), arcs.size());
    }

    private static IllegalArgumentException lineFailure(int line, IllegalArgumentException failure) {
        return new IllegalArgumentException("Motion line " + line + ": " + failure.getMessage(), failure);
    }

    private static void bakeChoreography(Mesh mesh, List<Joint> rig, float[][] rest,
                                         Map<Target, TreeMap<Float, float[]>> keys,
                                         List<Stage> stages, List<Arc> arcs) {
        int remaining = MAX_BAKED_KEYS;
        float[] stageEnds = new float[rig.size()];
        stages.sort(Comparator.comparingDouble(Stage::from));
        for (Stage stage : stages) {
            try {
                if (stage.from < stageEnds[stage.root]) {
                    throw new IllegalArgumentException("Stages for one actor cannot overlap");
                }
                stageEnds[stage.root] = stage.to;
                remaining -= bakeStage(mesh, rig, keys, stage, remaining);
            } catch (IllegalArgumentException failure) {
                throw lineFailure(stage.line, failure);
            }
        }
        arcs.sort(Comparator.comparingDouble(Arc::from));
        float[] arcEnds = new float[rig.size()];
        for (Arc arc : arcs) {
            try {
                if (arc.from < arcEnds[arc.root]) throw new IllegalArgumentException("Ballistic arcs for one root cannot overlap");
                arcEnds[arc.root] = arc.to;
                remaining -= bakeArc(rest, keys, arc, remaining);
            } catch (IllegalArgumentException failure) {
                throw lineFailure(arc.line, failure);
            }
        }
    }

    private static int bakeStage(Mesh mesh, List<Joint> rig, Map<Target, TreeMap<Float, float[]>> keys,
                                 Stage stage, int remaining) {
        String action = stage.action.toLowerCase(Locale.ROOT).replace('-', ' ');
        SkeletalMotion preset = parse(mesh, composePreset(rig, action, stage.root));
        double span = (double) stage.to - stage.from;
        boolean startsFromAuthoredPose = preset.tracks.stream().anyMatch(track -> {
            TreeMap<Float, float[]> frames = keys.get(new Target(track.joint, true));
            return frames != null && frames.containsKey(0f);
        });
        double blend = stage.from == 0 && !startsFromAuthoredPose ? 0 : Math.min(0.2, span * 0.2);
        boolean loops = switch (action) {
            case "rest", "rest pose", "dragon landing" -> false;
            default -> true;
        };
        int cycles = loops ? Math.max(1, (int) Math.round((span - blend) / preset.duration)) : 1;
        int count = 0;
        for (Track track : preset.tracks) count += 2 + cycles * (track.times.length - 1);
        if (count > remaining) throw new IllegalArgumentException("Choreography exceeds " + MAX_BAKED_KEYS + " baked keys");
        for (Track track : preset.tracks) {
            TreeMap<Float, float[]> frames = keys.computeIfAbsent(new Target(track.joint, true), ignored -> new TreeMap<>());
            Float next = frames.higherKey(stage.from);
            if (next != null && next <= stage.to) {
                throw new IllegalArgumentException("Stage conflicts with a rotation key on " + rig.get(track.joint).name);
            }
            if (blend > 0) {
                Map.Entry<Float, float[]> previous = frames.floorEntry(stage.from);
                frames.putIfAbsent(stage.from, previous == null ? new float[]{0, 0, 0, 1} : previous.getValue());
            }
            for (int cycle = 0; cycle < cycles; cycle++) {
                for (int i = cycle == 0 ? 0 : 1; i < track.times.length; i++) {
                    double phase = (cycle + track.times[i] / (double) preset.duration) / cycles;
                    float t = phase == 1 ? stage.to : (float) (stage.from + blend + (span - blend) * phase);
                    frames.put(t, track.values[i]);
                }
            }
        }
        return count;
    }

    private static int bakeArc(float[][] rest, Map<Target, TreeMap<Float, float[]>> keys, Arc arc, int remaining) {
        TreeMap<Float, float[]> frames = keys.get(new Target(arc.root, false));
        float[] start = frames == null ? null : frames.get(arc.from);
        if (start == null) throw new IllegalArgumentException("Ballistic motion needs a move key at FROM");
        Float next = frames.higherKey(arc.from);
        if (next != null && next <= arc.to) {
            throw new IllegalArgumentException("Ballistic motion conflicts with a move key");
        }
        double duration = (double) arc.to - arc.from;
        int samples = (int) Math.ceil(duration * SAMPLE_RATE);
        if (samples > remaining) throw new IllegalArgumentException("Choreography exceeds " + MAX_BAKED_KEYS + " baked keys");
        for (int i = 1; i <= samples; i++) {
            float t = i == samples ? arc.to : (float) (arc.from + duration * i / samples);
            double elapsed = (double) t - arc.from;
            float[] value = {
                    arcPosition(start[0], rest[arc.root][0], arc.vx * elapsed),
                    arcPosition(start[1], rest[arc.root][1], (arc.vy - 0.5 * arc.gravity * elapsed) * elapsed),
                    arcPosition(start[2], rest[arc.root][2], arc.vz * elapsed)
            };
            frames.put(t, value);
        }
        return samples;
    }

    private static float arcPosition(float start, float rest, double displacement) {
        if (Math.abs((double) start - rest + displacement) > 1000) {
            throw new IllegalArgumentException("Ballistic offsets must remain within ±1000 metres");
        }
        return (float) (start + displacement);
    }

    /** Composes a rig-specific preset from one of the supported short descriptions. */
    public static String compose(Mesh mesh, String description) {
        if (description == null || description.length() > 120) {
            throw new IllegalArgumentException("Use a short motion description");
        }
        List<Joint> rig = readRig(mesh);
        String request = description.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        if (request.endsWith(".")) request = request.substring(0, request.length() - 1);
        return composePreset(rig, request, -1);
    }

    private static String composePreset(List<Joint> rig, String request, int rootScope) {
        StringBuilder script;
        switch (request) {
            case "rest", "rest pose" -> {
                script = new StringBuilder("clip Rest pose\n");
                for (int joint = 0; joint < rig.size(); joint++) {
                    if (!inScope(rig, joint, rootScope)) continue;
                    String ref = jointRef(rig, joint);
                    rotationKey(script, ref, 0, 1, 0, 0, 0);
                    rotationKey(script, ref, 1, 1, 0, 0, 0);
                }
            }
            case "idle", "idle loop", "breathing idle", "standing idle" -> {
                int joint = unique(rig, "chest", rootScope);
                if (joint < 0) joint = unique(rig, "neck", rootScope);
                if (joint < 0) joint = Math.max(0, rootScope);
                script = new StringBuilder("clip Breathing idle\n");
                String name = jointRef(rig, joint);
                rotationKey(script, name, 0, 1, 0, 0, 0);
                rotationKey(script, name, 1, 1, 0, 0, 2);
                rotationKey(script, name, 2, 1, 0, 0, 0);
            }
            case "wag", "tail wag", "wag tail", "wag the tail", "wag its tail" -> {
                int joint = unique(rig, "tail", rootScope);
                if (joint < 0 || rig.get(joint).parent < 0) {
                    throw new IllegalArgumentException("Tail wag requires a unique articulated tail joint");
                }
                script = new StringBuilder("clip Tail wag\n");
                float[] times = {0, 0.3f, 0.6f, 0.9f, 1.2f};
                int[] angles = {0, 24, 0, -24, 0};
                for (int i = 0; i < times.length; i++) rotationKey(script, jointRef(rig, joint), times[i], 0, 1, 0, angles[i]);
            }
            case "walk", "walk cycle", "walk in place", "walking in place" -> {
                script = new StringBuilder("clip Walk in place\n");
                int root = walkJoint(rig, "root", -1, rootScope), hips = walkJoint(rig, "hips", root, rootScope);
                int chest = walkJoint(rig, "chest", hips, rootScope);
                float[] times = {0, 0.3f, 0.6f, 0.9f, 1.2f};
                int[] thigh = {-22, 2, 18, -2}, knee = {4, 6, 17, 43};
                for (int side = 0; side < 2; side++) {
                    String suffix = side == 0 ? "_L" : "_R";
                    int leg = walkJoint(rig, "upper_leg" + suffix, hips, rootScope);
                    int kneeJoint = walkJoint(rig, "lower_leg" + suffix, leg, rootScope);
                    int arm = walkJoint(rig, "upper_arm" + suffix, chest, rootScope);
                    String legRef = jointRef(rig, leg), kneeRef = jointRef(rig, kneeJoint), armRef = jointRef(rig, arm);
                    for (int i = 0; i < times.length; i++) {
                        int phase = (i + 2 * side) % thigh.length;
                        rotationKey(script, legRef, times[i], 1, 0, 0, thigh[phase]);
                        rotationKey(script, kneeRef, times[i], 1, 0, 0, knee[phase]);
                        swingArm(script, armRef, times[i], -0.65 * thigh[phase], side == 0 ? 77 : -77);
                    }
                }
                for (int i = 0; i < times.length; i++) {
                    rotationKey(script, jointRef(rig, chest), times[i], 0, 1, 0, 4 * Math.cos(i * Math.PI / 2));
                }
            }
            case "dragon flight", "dragon-flight" -> script = dragonPreset(rig, rootScope, DragonStage.FLIGHT);
            case "dragon landing", "dragon-landing" -> script = dragonPreset(rig, rootScope, DragonStage.LANDING);
            case "dragon perch", "dragon-perch" -> script = dragonPreset(rig, rootScope, DragonStage.PERCH);
            default -> throw new IllegalArgumentException("Use rest, idle, walk in place, tail wag, dragon flight, dragon landing, or dragon perch");
        }
        return script.toString();
    }

    private static int walkJoint(List<Joint> rig, String name, int parent, int rootScope) {
        int index = unique(rig, name, rootScope);
        if (index < 0 || rig.get(index).parent != parent) {
            throw new IllegalArgumentException("Walk in place requires the Foundry humanoid hierarchy");
        }
        return index;
    }

    private static StringBuilder dragonPreset(List<Joint> rig, int scope, DragonStage stage) {
        int root = dragonJoint(rig, "root", -1, scope);
        int pelvis = dragonJoint(rig, "pelvis", root, scope);
        int spine = dragonJoint(rig, "spine", pelvis, scope);
        int chest = dragonJoint(rig, "chest", spine, scope);
        int neck = dragonJoint(rig, "neck_base", chest, scope);
        int neckMid = dragonJoint(rig, "neck_mid", neck, scope);
        dragonJoint(rig, "head", neckMid, scope);
        int tail = dragonJoint(rig, "tail_01", pelvis, scope);
        int tailMid = dragonJoint(rig, "tail_02", tail, scope);
        int tailEnd = dragonJoint(rig, "tail_03", tailMid, scope);

        StringBuilder script = new StringBuilder("clip Dragon ").append(stage.name().toLowerCase(Locale.ROOT)).append('\n');
        float[] times = switch (stage) {
            case FLIGHT -> new float[]{0, 0.36f, 0.72f, 1.24f, 1.8f};
            case LANDING -> new float[]{0, 0.55f, 1.3f, 2};
            case PERCH -> new float[]{0, 1.4f, 2.8f};
        };
        switch (stage) {
            case FLIGHT -> {
                dragonCurve(script, chest, times, 1, 0, 0, -4, -7, -4, 1, -4);
                dragonCurve(script, neck, times, 1, 0, 0, 3, 7, 4, -2, 3);
                dragonCurve(script, neckMid, times, 1, 0, 0, 1, 2, 1, 0, 1);
                dragonCurve(script, tail, times, 1, 0, 0, 3, 6, 2, -5, 3);
                dragonCurve(script, tailMid, times, 1, 0, 0, 1, 2, 5, -2, 1);
                dragonCurve(script, tailEnd, times, 1, 0, 0, 2, -1, 4, 1, 2);
            }
            case LANDING -> {
                dragonCurve(script, chest, times, 1, 0, 0, -4, -13, 5, 0);
                dragonCurve(script, neck, times, 1, 0, 0, 3, 12, -7, 0);
                dragonCurve(script, neckMid, times, 1, 0, 0, 1, 4, -3, 0);
                dragonCurve(script, tail, times, 1, 0, 0, 3, 8, 4, 0);
                dragonCurve(script, tailMid, times, 1, 0, 0, 1, 2, 6, 0);
                dragonCurve(script, tailEnd, times, 1, 0, 0, 2, 1, 4, 0);
            }
            case PERCH -> {
                dragonCurve(script, chest, times, 1, 0, 0, 0, 1.5, 0);
                dragonCurve(script, neck, times, 1, 0, 0, 0, -1.5, 0);
                dragonCurve(script, neckMid, times, 1, 0, 0, 0, 1, 0);
                dragonCurve(script, tail, times, 0, 1, 0, 0, 1.5, 0);
                dragonCurve(script, tailMid, times, 0, 1, 0, 0, 2, 0);
                dragonCurve(script, tailEnd, times, 0, 1, 0, 0, 2.5, 0);
            }
        }
        for (int side : new int[]{-1, 1}) {
            String suffix = side < 0 ? "_L" : "_R";
            int shoulder = dragonJoint(rig, "wing_shoulder" + suffix, chest, scope);
            int elbow = dragonJoint(rig, "wing_elbow" + suffix, shoulder, scope);
            int wrist = dragonJoint(rig, "wing_wrist" + suffix, elbow, scope);
            int front = dragonJoint(rig, "front_upper" + suffix, chest, scope);
            int frontLower = dragonJoint(rig, "front_lower" + suffix, front, scope);
            dragonJoint(rig, "front_foot" + suffix, frontLower, scope);
            int hind = dragonJoint(rig, "hind_upper" + suffix, pelvis, scope);
            int hindLower = dragonJoint(rig, "hind_lower" + suffix, hind, scope);
            dragonJoint(rig, "hind_foot" + suffix, hindLower, scope);
            switch (stage) {
                case FLIGHT -> {
                    dragonCurve(script, shoulder, times, 0, 0, side, 0, 23, 5, -31, 0);
                    dragonCurve(script, elbow, times, 0, 0, side, -8, 6, -15, 11, -8);
                    dragonCurve(script, wrist, times, 0, 0, side, -5, 9, 5, -15, -5);
                    dragonCurve(script, front, times, 1, 0, 0, 32, 29, 32, 34, 32);
                    dragonCurve(script, frontLower, times, 1, 0, 0, -43, -41, -43, -45, -43);
                    dragonCurve(script, hind, times, 1, 0, 0, -26, -25, -26, -28, -26);
                    dragonCurve(script, hindLower, times, 1, 0, 0, 43, 41, 43, 45, 43);
                }
                case LANDING -> {
                    dragonWing(script, shoulder, times, side, new double[]{0, 10, 25, 52}, new double[]{0, 12, 0, -15});
                    dragonWing(script, elbow, times, side, new double[]{0, 0, 14, 31}, new double[]{-8, -12, -4, 0});
                    dragonWing(script, wrist, times, side, new double[]{0, 0, -6, -12}, new double[]{-5, -2, 0, 0});
                    dragonCurve(script, front, times, 1, 0, 0, 32, 10, 0, 0);
                    dragonCurve(script, frontLower, times, 1, 0, 0, -43, -15, 0, 0);
                    dragonCurve(script, hind, times, 1, 0, 0, -26, -10, 0, 0);
                    dragonCurve(script, hindLower, times, 1, 0, 0, 43, 20, 0, 0);
                }
                case PERCH -> {
                    dragonWing(script, shoulder, times, side, new double[]{52, 53, 52}, new double[]{-15, -14, -15});
                    dragonCurve(script, elbow, times, 0, side, 0, 31, 30, 31);
                    dragonCurve(script, wrist, times, 0, side, 0, -12, -11, -12);
                    for (int leg : new int[]{front, frontLower, hind, hindLower}) {
                        dragonCurve(script, leg, times, 1, 0, 0, 0, 0, 0);
                    }
                }
            }
        }
        return script;
    }

    private static int dragonJoint(List<Joint> rig, String name, int parent, int scope) {
        int index = unique(rig, name, scope);
        if (index < 0 || rig.get(index).parent != parent) {
            throw new IllegalArgumentException("Dragon stages require the Foundry dragon hierarchy at " + name);
        }
        return index;
    }

    private static void dragonCurve(StringBuilder script, int joint, float[] times, int x, int y, int z, double... angles) {
        for (int i = 0; i < times.length; i++) rotationKey(script, "@" + joint, times[i], x, y, z, angles[i]);
    }

    private static void dragonWing(StringBuilder script, int joint, float[] times, int side, double[] yaw, double[] roll) {
        for (int i = 0; i < times.length; i++) {
            double y = Math.toRadians(yaw[i] * side) / 2, z = Math.toRadians(roll[i] * side) / 2;
            double qx = Math.sin(y) * Math.sin(z), qy = Math.sin(y) * Math.cos(z), qz = Math.cos(y) * Math.sin(z);
            double length = Math.sqrt(qx*qx + qy*qy + qz*qz);
            double angle = Math.toDegrees(2 * Math.atan2(length, Math.cos(y) * Math.cos(z)));
            rotationKey(script, "@" + joint, times[i], length == 0 ? 1 : qx, qy, qz, angle);
        }
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
        TreeSet<Float> stagePoses = new TreeSet<>();
        times.add(duration);
        for (int i = 0; i < Math.ceil(duration * SAMPLE_RATE); i++) times.add(i / (float) SAMPLE_RATE);
        for (Track track : tracks) for (float time : track.times) times.add(time);
        if (blended) {
            for (Stage stage : stages) {
                float span = stage.to - stage.from;
                float opening = Math.min(2, span);
                stagePoses.add(stage.from);
                stagePoses.add(stage.from + opening * 0.25f);
                stagePoses.add(stage.from + opening * 0.5f);
                stagePoses.add(stage.from + opening * 0.75f);
                stagePoses.add(stage.from + span * 0.5f);
                stagePoses.add(stage.to);
            }
            times.addAll(stagePoses);
        }
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
            if (samples++ % poseStep == 0 || time == duration || stagePoses.contains(time)) {
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
                times.size(), contacts.size(), maxGap, blended, deformationChecks, actors, stages.size(), ballisticArcs);
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

    private static int resolve(List<Joint> rig, Map<String, Integer> actors, String reference) {
        int slash = reference.indexOf('/');
        Integer root = slash < 0 ? null : actors.get(reference.substring(0, slash));
        if (root == null) return resolve(rig, reference);
        String name = reference.substring(slash + 1);
        int index = name.startsWith("@") ? resolve(rig, name) : unique(rig, name, root);
        if (index == -2) throw new IllegalArgumentException("Ambiguous joint " + reference + "; use its @index");
        if (index < 0 || !inScope(rig, index, root)) throw new IllegalArgumentException("Joint is outside actor scope " + reference);
        return index;
    }

    private static int unique(List<Joint> rig, String name) {
        return unique(rig, name, -1);
    }

    private static int unique(List<Joint> rig, String name, int root) {
        int index = -1;
        for (int i = 0; i < rig.size(); i++) {
            if (!rig.get(i).name.equals(name) || !inScope(rig, i, root)) continue;
            if (index >= 0) return -2;
            index = i;
        }
        return index;
    }

    private static boolean inScope(List<Joint> rig, int joint, int root) {
        if (root < 0) return true;
        while (joint > root) joint = rig.get(joint).parent;
        return joint == root;
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
