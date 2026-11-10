package com.asterion.foundry;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;

/** Append-only local form rules. Overrides are scoped to the exact base mesh. */
final class FormLibrary {
    private static final int MAGIC = 0x41465031;
    private final File root;

    FormLibrary(File storage) { root = new File(storage, "form-profiles"); }

    record Rule(float x, float y, float z, int tone, float strength, boolean preserveHead) {
        Rule {
            for (float value : new float[]{x, y, z}) {
                if (!Float.isFinite(value) || value < .25f || value > 4f) {
                    throw new IllegalArgumentException("Form proportions must be between 0.25 and 4");
                }
            }
            if (tone < 0 || tone > 0xffffff || !Float.isFinite(strength) || strength < 0 || strength > 1) {
                throw new IllegalArgumentException("Use a six-digit RGB colour and a tint strength of 0 to 1");
            }
        }

        Mesh apply(Mesh source) {
            if (source.bones.isEmpty()) throw new IllegalArgumentException("Forms require an editable rigged base");
            Mesh result = MeshRevision.apply(source, x, y, z, null);
            if (strength == 0) return result;
            boolean[] protectedJoint = new boolean[result.bones.size()];
            for (int i = 0; i < protectedJoint.length; i++) {
                Mesh.Bone joint = result.bones.get(i);
                String name = joint.name.toLowerCase(Locale.ROOT);
                protectedJoint[i] = preserveHead && (name.equals("head") || name.equals("face")
                        || (joint.parent >= 0 && protectedJoint[joint.parent]));
            }
            float[] factors = {factor(tone >>> 16), factor(tone >>> 8), factor(tone)};
            for (float[] vertex : result.vertices) {
                if (protectedJoint[(int) vertex[12]]) continue;
                for (int channel = 0; channel < 3; channel++) vertex[8 + channel] *= factors[channel];
            }
            result.validate();
            return result;
        }

        private float factor(int channel) {
            return 1 - strength + strength * VisualHull.linear(channel);
        }
    }

    record Profile(String name, String baseScope, int revision, Rule rule) {
        Profile {
            name = cleanName(name);
            checkScope(baseScope);
            if (revision < 1 || rule == null) throw new IllegalArgumentException("Invalid form revision");
        }
    }

    Profile save(String name, String baseScope, Rule rule) throws Exception {
        name = cleanName(name);
        File directory = profileDirectory(name, baseScope);
        synchronized (FormLibrary.class) {
            Files.createDirectories(directory.toPath());
            Profile previous = latest(directory);
            if (previous != null && previous.rule().equals(rule)) return previous;
            int revision = previous == null ? 1 : Math.addExact(previous.revision(), 1);
            Profile profile = new Profile(name, baseScope, revision, rule);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeInt(MAGIC);
                out.writeUTF(profile.name());
                out.writeUTF(baseScope);
                out.writeInt(revision);
                out.writeFloat(rule.x());
                out.writeFloat(rule.y());
                out.writeFloat(rule.z());
                out.writeInt(rule.tone());
                out.writeFloat(rule.strength());
                out.writeBoolean(rule.preserveHead());
            }
            FilesUtil.write(new File(directory, versionName(revision)), bytes.toByteArray());
            return profile;
        }
    }

    Profile find(String name, String baseScope, int revision) throws Exception {
        File file = new File(profileDirectory(name, baseScope), versionName(revision));
        Profile profile = read(file);
        if (!key(profile.name()).equals(key(name)) || !baseScope.equals(profile.baseScope())) {
            throw new IOException("Saved form profile scope does not match its location");
        }
        return profile;
    }

    List<Profile> history(String name, String baseScope) throws Exception {
        File directory = profileDirectory(name, baseScope);
        String[] versions = revisionFiles(directory);
        ArrayList<Profile> result = new ArrayList<>(versions.length);
        for (int i = versions.length - 1; i >= 0; i--) {
            int revision = Integer.parseInt(versions[i].substring(0,10));
            result.add(find(name,baseScope,revision));
        }
        return result;
    }

    List<Profile> available(String baseScope) throws Exception {
        TreeMap<String, Profile> profiles = new TreeMap<>();
        readScope("", profiles);
        if (!baseScope.isEmpty()) readScope(baseScope, profiles);
        return new ArrayList<>(profiles.values());
    }

    private void readScope(String baseScope, TreeMap<String, Profile> profiles) throws Exception {
        File[] directories = scope(baseScope).listFiles(File::isDirectory);
        if (directories == null) return;
        for (File directory : directories) {
            Profile profile = latest(directory);
            if (profile == null) continue;
            if (!baseScope.equals(profile.baseScope()) || !directory.equals(profileDirectory(profile.name(), baseScope))) {
                throw new IOException("Saved form profile is in the wrong scope");
            }
            profiles.put(key(profile.name()), profile);
        }
    }

    private static Profile latest(File directory) throws IOException {
        String[] versions = revisionFiles(directory);
        if (versions.length == 0) return null;
        return read(new File(directory, versions[versions.length - 1]));
    }

    private static String[] revisionFiles(File directory) {
        String[] versions = directory.list((dir, name) -> name.matches("[0-9]{10}\\.form"));
        if (versions == null) return new String[0];
        Arrays.sort(versions);
        return versions;
    }

    private static Profile read(File file) throws IOException {
        try (DataInputStream in = new DataInputStream(Files.newInputStream(file.toPath()))) {
            if (in.readInt() != MAGIC) throw new IOException("Unsupported form profile format");
            String name = in.readUTF();
            String baseScope = in.readUTF();
            int revision = in.readInt();
            Rule rule = new Rule(in.readFloat(), in.readFloat(), in.readFloat(), in.readInt(),
                    in.readFloat(), in.readBoolean());
            if (in.read() != -1 || !versionName(revision).equals(file.getName())) {
                throw new IOException("Saved form profile failed its format checks");
            }
            return new Profile(name, baseScope, revision, rule);
        }
    }

    private File profileDirectory(String name, String baseScope) throws Exception {
        return new File(scope(baseScope), FilesUtil.hash(key(name)));
    }

    private File scope(String baseScope) {
        checkScope(baseScope);
        return new File(root, baseScope.isEmpty() ? "shared" : baseScope);
    }

    private static void checkScope(String sha) {
        if (sha == null || (!sha.isEmpty() && !sha.matches("[0-9a-f]{64}"))) {
            throw new IllegalArgumentException("Invalid form base checksum");
        }
    }

    private static String cleanName(String name) {
        String clean = name.trim();
        if (clean.isEmpty() || clean.length() > 80 || clean.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Use a form name between 1 and 80 characters");
        }
        return clean;
    }

    private static String key(String name) { return cleanName(name).toLowerCase(Locale.ROOT); }

    private static String versionName(int revision) {
        if (revision < 1) throw new IllegalArgumentException("Invalid form revision");
        return String.format(Locale.ROOT, "%010d.form", revision);
    }
}
