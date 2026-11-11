package com.asterion.foundry;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;

/**
 * Versioned, part-ID-addressed metallic/roughness factors for editable GLBs.
 * This is an editable PBR *factor* layer; no texture or visual fidelity claim.
 * Source geometry, shared skin, vertex colours and base-colour PNG are untouched.
 */
final class PartMaterials {
    static final String VERSION = "foundry-part-pbr-factors-v1";
    static final String FILE = "part-materials.foundry";

    record Finish(double metallic, double roughness) {
        Finish {
            if (!valid(metallic) || !valid(roughness))
                throw new IllegalArgumentException("PBR factors must be finite values in 0..1");
        }
    }

    private final TreeMap<String, Finish> entries;

    private PartMaterials(Map<String, Finish> entries) {
        this.entries = new TreeMap<>(entries);
    }

    static PartMaterials empty() { return new PartMaterials(Map.of()); }
    boolean isEmpty() { return entries.isEmpty(); }

    static Finish defaults(Mesh.Part part) {
        return part.role().equals("armor") ? new Finish(0.75, 0.35) : new Finish(0, 0.8);
    }

    Finish forPart(Mesh.Part part) {
        return entries.getOrDefault(part.id(), defaults(part));
    }

    void requireCompatible(Mesh mesh) {
        Set<String> existing = new HashSet<>();
        for (Mesh.Part part : mesh.parts()) existing.add(part.id());
        if (!existing.containsAll(entries.keySet()))
            throw new IllegalArgumentException("PBR layer contains a missing or remapped mesh part ID");
    }

    PartMaterials change(Mesh mesh, String id, double metallic, double roughness) {
        Finish desired = new Finish(metallic, roughness);
        Mesh.Part selected = null;
        for (Mesh.Part part : mesh.parts()) if (part.id().equals(id)) { selected = part; break; }
        if (selected == null) throw new IllegalArgumentException("Selected material part does not exist");
        requireCompatible(mesh);
        TreeMap<String, Finish> updated = new TreeMap<>(entries);
        if (desired.equals(defaults(selected))) updated.remove(id);
        else updated.put(id, desired);
        return new PartMaterials(updated);
    }

    PartMaterials only(String id) {
        Finish chosen = entries.get(id);
        return chosen == null ? empty() : new PartMaterials(Map.of(id, chosen));
    }

    void save(File file) throws IOException {
        if (isEmpty()) throw new IOException("Default PBR factors require no material layer file");
        StringBuilder result = new StringBuilder(VERSION).append('\n');
        for (Map.Entry<String, Finish> item : entries.entrySet()) {
            result.append(item.getKey()).append('\t')
                    .append(item.getValue().metallic()).append('\t')
                    .append(item.getValue().roughness()).append('\n');
        }
        FilesUtil.write(file, result.toString());
    }

    static PartMaterials load(File file, Mesh mesh) throws IOException {
        if (file == null) return empty();
        if (!file.isFile()) throw new IOException("Saved PBR layer file is missing");
        String[] lines = FilesUtil.readString(file.toPath()).split("\n", -1);
        if (lines.length == 0 || !VERSION.equals(lines[0]))
            throw new IOException("Unsupported PBR layer schema");
        if (lines.length > Mesh.MAX_PARTS+2) throw new IOException("PBR layer exceeds the part limit");
        TreeMap<String, Finish> parsed = new TreeMap<>();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (i == lines.length - 1 && line.isEmpty()) continue;
            String[] fields = line.split("\t", -1);
            if (fields.length != 3 || !fields[0].matches("[a-z][a-z0-9_-]{0,63}")
                    || parsed.containsKey(fields[0]))
                throw new IOException("Invalid or duplicated PBR part entry");
            try {
                parsed.put(fields[0], new Finish(Double.parseDouble(fields[1]),
                        Double.parseDouble(fields[2])));
            } catch (IllegalArgumentException invalid) {
                throw new IOException("Invalid saved PBR factor", invalid);
            }
        }
        PartMaterials result = new PartMaterials(parsed);
        try { result.requireCompatible(mesh); }
        catch (IllegalArgumentException mismatch) { throw new IOException(mismatch.getMessage(), mismatch); }
        return result;
    }

    private static boolean valid(double factor) {
        return Double.isFinite(factor) && factor >= 0 && factor <= 1;
    }
}
