package com.asterion.foundry;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.Set;
import org.json.JSONObject;

/**
 * An atomic, non-destructive ledger for Foundry's staged production.
 *
 * Checkpoints are owned by the project, not by a temporary worker session.
 * A stage is accepted only after all of its declared output files exist and
 * match their recorded hashes. Repeating an identical stage preserves later
 * stages; changing one invalidates dependent stages without deleting outputs.
 *
 * This ledger records technical evidence, not visual or NoWorld acceptance.
 */
final class StageManifest {
    static final String SCHEMA = "asterion.foundry.stages.v1";
    private static final String[] ORDER = {"geometry", "surface", "export"};
    private static final Set<String> ALLOWED_FILES = Set.of(
            "checkpoint.mesh", "reference-metrics.json", "base-color.png",
            "asset.glb", "receipt.json", "import-manifest.json",
            "motion.foundry.txt");
    private final File directory;
    private final File manifest;
    private JSONObject stages;

    StageManifest(File directory) throws Exception {
        this.directory = directory.getCanonicalFile();
        this.manifest = new File(this.directory, "stage-manifest.json");
        if (manifest.isFile()) {
            JSONObject document = new JSONObject(Files.readString(manifest.toPath()));
            if (!SCHEMA.equals(document.optString("schema")))
                throw new IOException("Unsupported stage ledger schema");
            stages = document.getJSONObject("stages");
        } else stages = new JSONObject();
    }

    private static int rank(String name) throws IOException {
        for (int i = 0; i < ORDER.length; i++) if (ORDER[i].equals(name)) return i;
        throw new IOException("Unsupported production stage " + name);
    }

    private File output(String filename) throws Exception {
        if (!ALLOWED_FILES.contains(filename)) throw new IOException("Unexpected stage output " + filename);
        File file = new File(directory, filename).getCanonicalFile();
        if (!file.getParentFile().equals(directory) || !file.isFile())
            throw new IOException("Missing or unsafe stage output " + filename);
        return file;
    }

    private static JSONObject dependencies(Map<String, String> data) throws Exception {
        JSONObject result = new JSONObject();
        for (Map.Entry<String, String> value : data.entrySet()) {
            if (!value.getKey().matches("[a-z][a-z0-9_.-]{0,63}")
                    || !value.getValue().matches("[a-f0-9]{64}"))
                throw new IOException("Invalid stage dependency or digest");
            result.put(value.getKey(), value.getValue());
        }
        return result;
    }

    /**
     * Accept a completed stage. Outputs are rehashed, never accepted on
     * filenames alone. Downstream receipts are invalidated if inputs changed.
     */
    synchronized boolean publish(String name, Map<String, String> inputs,
                                  Map<String, File> outputs, long elapsedMillis) throws Exception {
        int index = rank(name);
        if (elapsedMillis < 0 || outputs.isEmpty()) throw new IOException("Invalid stage evidence");
        for (int prior = 0; prior < index; prior++) {
            if (!stages.has(ORDER[prior])) throw new IOException("Previous stage is not checkpointed: " + ORDER[prior]);
            verify(ORDER[prior]);
        }
        JSONObject hashedFiles = new JSONObject();
        for (Map.Entry<String, File> item : outputs.entrySet()) {
            File exact = output(item.getKey());
            if (!exact.equals(item.getValue().getCanonicalFile()))
                throw new IOException("Stage file lies outside the current project");
            hashedFiles.put(item.getKey(), FilesUtil.hash(exact));
        }
        JSONObject wanted = new JSONObject().put("inputs", dependencies(inputs))
                .put("files", hashedFiles)
                .put("elapsed_ms", elapsedMillis)
                .put("evidence", "checksum_verified");
        JSONObject previous = stages.optJSONObject(name);
        if (previous != null
                && same(previous.getJSONObject("inputs"), wanted.getJSONObject("inputs"))
                && same(previous.getJSONObject("files"), hashedFiles)) {
            verify(name);
            return false;
        }
        for (int i = index; i < ORDER.length; i++) stages.remove(ORDER[i]);
        stages.put(name, wanted);
        save();
        return true;
    }

    private static boolean same(JSONObject left, JSONObject right) throws Exception {
        if (left.length() != right.length()) return false;
        java.util.Iterator<String> keys = left.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (!right.has(key) || !left.getString(key).equals(right.getString(key))) return false;
        }
        return true;
    }

    synchronized boolean has(String name) throws Exception {
        rank(name);
        return stages.has(name);
    }

    synchronized void verify(String name) throws Exception {
        rank(name);
        JSONObject item = stages.optJSONObject(name);
        if (item == null) throw new IOException("Stage checkpoint absent: " + name);
        JSONObject outputs = item.getJSONObject("files");
        java.util.Iterator<String> names = outputs.keys();
        while (names.hasNext()) {
            String filename = names.next();
            if (!FilesUtil.hash(output(filename)).equals(outputs.getString(filename)))
                throw new IOException("Stage checkpoint checksum mismatch: " + filename);
        }
    }

    synchronized void verifyAll() throws Exception {
        for (String name : ORDER) if (stages.has(name)) verify(name);
    }

    synchronized JSONObject summary() throws Exception {
        JSONObject result = new JSONObject().put("schema", SCHEMA).put("stages", new JSONObject(stages.toString()));
        result.put("visual_acceptance", "not_established");
        result.put("unreal_acceptance", false);
        return result;
    }

    private void save() throws Exception {
        FilesUtil.write(manifest, summary().toString(2));
    }
}
