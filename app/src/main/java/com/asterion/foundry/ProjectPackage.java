package com.asterion.foundry;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.json.JSONObject;

/** Portable package: source role paths refer to the immutable bytes inside the ZIP. */
final class ProjectPackage {
    private ProjectPackage() {}

    static void write(Store store, String id, OutputStream output) throws Exception {
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            JSONObject row = store.job(id);
            if (!"complete".equals(row.getString("state"))) throw new IOException("Project is not complete");
            File directory = store.project(id);
            JSONObject request = new JSONObject(row.getString("request"));
            Map<String, File> originals = new SourceFiles(store.root).originals(request);
            for (Map.Entry<String, File> role : originals.entrySet()) {
                request.put(role.getKey(), "sources/" + role.getValue().getName());
            }
            JSONObject receipt = new JSONObject(Files.readString(new File(directory, "receipt.json").toPath()));
            File asset = new File(directory, "asset.glb");
            if (!FilesUtil.hash(asset).equals(receipt.getString("asset_sha256"))) {
                throw new IOException("Project asset checksum does not match its receipt");
            }
            File editable = new File(directory, "checkpoint.mesh");
            if (receipt.has("editable_mesh_sha256")
                    && !FilesUtil.hash(editable).equals(receipt.getString("editable_mesh_sha256"))) {
                throw new IOException("Editable geometry checksum does not match its receipt");
            }
            zip.putNextEntry(new ZipEntry("project.json"));
            zip.write(request.toString(2).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            if (!receipt.optBoolean("source_bytes_preserved")) add(zip, "checkpoint.mesh", editable);
            for (String name : new String[]{"asset.glb", "receipt.json", "import-manifest.json"}) {
                add(zip, name, new File(directory, name));
            }
            if (receipt.has("texture_sha256")) {
                File texture = new File(directory, "base-color.png");
                if (!receipt.getString("texture_sha256").equals(FilesUtil.hash(texture))) {
                    throw new IOException("Base colour checksum does not match its receipt");
                }
                add(zip,"base-color.png",texture);
            }
            if (receipt.has("motion")) {
                File script = new File(directory,"motion.foundry.txt");
                if (!receipt.getJSONObject("motion").getString("editable_script_sha256").equals(FilesUtil.hash(script))) {
                    throw new IOException("Editable animation checksum does not match its receipt");
                }
                add(zip,"motion.foundry.txt",script);
            }
            Set<String> written = new HashSet<>();
            for (File file : originals.values()) {
                if (written.add(file.getName())) add(zip, "sources/" + file.getName(), file);
            }
        }
    }

    private static void add(ZipOutputStream zip, String name, File source) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        Files.copy(source.toPath(), zip);
        zip.closeEntry();
    }
}
