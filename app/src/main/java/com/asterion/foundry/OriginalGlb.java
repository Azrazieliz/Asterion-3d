package com.asterion.foundry;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;

/** Validate the self-contained GLB container without rewriting source rigs or materials. */
final class OriginalGlb {
    static JSONObject inspect(File source) throws Exception {
        JSONObject document = document(source);
        JSONArray buffers = document.optJSONArray("buffers");
        long bufferBytes = buffers == null || buffers.length() == 0
                ? 0 : buffers.getJSONObject(0).getLong("byteLength");
        JSONArray meshes = document.optJSONArray("meshes");
        if (meshes == null || meshes.length() == 0) throw new IOException("GLB contains no meshes");
        int primitives = 0;
        int morphSlots = 0;
        for (int i = 0; i < meshes.length(); i++) {
            JSONArray parts = meshes.getJSONObject(i).getJSONArray("primitives");
            primitives += parts.length();
            for (int p = 0; p < parts.length(); p++) {
                morphSlots += length(parts.getJSONObject(p).optJSONArray("targets"));
            }
        }
        if (primitives == 0) throw new IOException("GLB contains no mesh primitives");
        return new JSONObject().put("container", "GLB 2.0").put("embedded_buffer_bytes", bufferBytes)
                .put("meshes", meshes.length()).put("primitives", primitives)
                .put("skins", length(document.optJSONArray("skins")))
                .put("morph_slots_by_primitive", morphSlots)
                .put("materials", length(document.optJSONArray("materials")))
                .put("animations", length(document.optJSONArray("animations")))
                .put("extensions_used", document.optJSONArray("extensionsUsed"))
                .put("validation", "container_and_embedded_buffer_ranges");
    }

    static JSONObject document(File source) throws Exception {
        try (RandomAccessFile file = new RandomAccessFile(source, "r")) {
            if (file.length() < 20 || word(file) != 0x46546c67 || word(file) != 2
                    || word(file) != file.length()) throw new IOException("Invalid glTF 2 GLB header");
            long jsonLength = word(file);
            if (word(file) != 0x4e4f534a || jsonLength > 4L * 1024 * 1024
                    || jsonLength < 2 || (jsonLength & 3) != 0
                    || jsonLength > file.length() - file.getFilePointer()) {
                throw new IOException("Invalid or oversized GLB JSON chunk");
            }
            byte[] json = new byte[(int) jsonLength];
            file.readFully(json);
            JSONObject document = new JSONObject(new String(json, StandardCharsets.UTF_8));
            if (!"2.0".equals(document.getJSONObject("asset").getString("version"))) {
                throw new IOException("Original must use glTF 2.0");
            }
            long binaryLength = 0;
            if (file.getFilePointer() < file.length()) {
                binaryLength = word(file);
                if (word(file) != 0x004e4942 || (binaryLength & 3) != 0
                        || binaryLength != file.length() - file.getFilePointer()) {
                    throw new IOException("Invalid GLB binary chunk or additional chunks");
                }
            }
            checkUris(document, 0);
            JSONArray buffers = document.optJSONArray("buffers");
            if (buffers != null && buffers.length() > 1) {
                throw new IOException("This intake requires one embedded GLB buffer");
            }
            long bufferBytes = buffers == null || buffers.length() == 0
                    ? 0 : buffers.getJSONObject(0).getLong("byteLength");
            if (bufferBytes < 0 || bufferBytes > binaryLength || binaryLength - bufferBytes > 3) {
                throw new IOException("Embedded buffer size disagrees with GLB binary chunk");
            }
            JSONArray views = document.optJSONArray("bufferViews");
            if (views != null) for (int i = 0; i < views.length(); i++) {
                JSONObject view = views.getJSONObject(i);
                long offset = view.optLong("byteOffset", 0);
                long length = view.getLong("byteLength");
                if (view.getInt("buffer") != 0 || offset < 0 || length < 1
                        || offset > bufferBytes || length > bufferBytes - offset) {
                    throw new IOException("GLB buffer view " + i + " exceeds embedded data");
                }
            }
            return document;
        }
    }

    private static int length(JSONArray array) { return array == null ? 0 : array.length(); }
    private static long word(RandomAccessFile file) throws IOException {
        return Integer.toUnsignedLong(Integer.reverseBytes(file.readInt()));
    }

    private static void checkUris(Object value, int level) throws Exception {
        if (level > 64) throw new IOException("GLB document exceeds nesting budget");
        if (value instanceof JSONObject object) {
            java.util.Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Object member = object.get(key);
                if ("uri".equals(key) && (!(member instanceof String uri) || !uri.startsWith("data:"))) {
                    throw new IOException("Source GLB has an external dependency. Supply a self-contained GLB.");
                }
                checkUris(member, level + 1);
            }
        } else if (value instanceof JSONArray array) {
            for (int i = 0; i < array.length(); i++) checkUris(array.get(i), level + 1);
        }
    }
}
