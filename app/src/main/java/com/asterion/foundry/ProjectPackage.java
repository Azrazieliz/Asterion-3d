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
import org.json.JSONArray;

/** Portable package: source role paths refer to the immutable bytes inside the ZIP. */
final class ProjectPackage {
    private ProjectPackage() {}

    static void write(Store store, String id, OutputStream output) throws Exception {
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            JSONObject row = store.job(id);
            String state=row.getString("state");
            AssetStagePolicy.requireEditable(state);
            File directory = store.project(id);
            JSONObject request = new JSONObject(row.getString("request"));
            Map<String, File> originals = new SourceFiles(store.root).originals(request);
            for (Map.Entry<String, File> role : originals.entrySet()) {
                request.put(role.getKey(), "sources/" + role.getValue().getName());
            }
            JSONObject receipt = new JSONObject(FilesUtil.readString(new File(directory, "receipt.json").toPath()));
            AssetStagePolicy.requireExportReceipt(state,receipt.optString("outcome"));
            File asset = new File(directory, "asset.glb");
            if (!FilesUtil.hash(asset).equals(receipt.getString("asset_sha256"))) {
                throw new IOException("Project asset checksum does not match its receipt");
            }
            File editable = new File(directory, "checkpoint.mesh");
            if (receipt.has("editable_mesh_sha256")
                    && !FilesUtil.hash(editable).equals(receipt.getString("editable_mesh_sha256"))) {
                throw new IOException("Editable geometry checksum does not match its receipt");
            }
            zip.putNextEntry(new ZipEntry("export-status.json"));
            zip.write(new JSONObject()
                    .put("schema","asterion.foundry.export-status.v1")
                    .put("project_state",state)
                    .put("label",AssetStagePolicy.exportLabel(state))
                    .put("reference_quality_approved",false)
                    .put("noworld_native_accepted",false)
                    .put("unreal_import_executed",false)
                    .put("production_export_admitted",
                            "complete".equals(state)&&receipt.optBoolean("production_export_admitted",false))
                    .toString(2).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("project.json"));
            zip.write(request.toString(2).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            if (receipt.has("editable_mesh_sha256") || !receipt.optBoolean("source_bytes_preserved") && editable.isFile()) {
                add(zip, "checkpoint.mesh", editable);
            }
            File stageEvidence = new File(directory, "stage-manifest.json");
            if (stageEvidence.isFile()) {
                StageManifest manifest = new StageManifest(directory);
                manifest.verifyAll();
            }
            for (String name : new String[]{"asset.glb", "receipt.json", "import-manifest.json"}) {
                add(zip, name, new File(directory, name));
            }
            if (stageEvidence.isFile()) add(zip, "stage-manifest.json", stageEvidence);
            if (receipt.has("thumbnail_sha256")) {
                File preview = new File(directory,"preview.png");
                if (!receipt.getString("thumbnail_sha256").equals(FilesUtil.hash(preview)))
                    throw new IOException("Project thumbnail checksum mismatch");
                add(zip,"preview.png",preview);
            }
            if (receipt.has("texture_sha256")) {
                File texture = new File(directory, "base-color.png");
                if (!receipt.getString("texture_sha256").equals(FilesUtil.hash(texture))) {
                    throw new IOException("Base colour checksum does not match its receipt");
                }
                add(zip,"base-color.png",texture);
            }
            if (receipt.has("part_materials_sha256")) {
                File materialFile = new File(directory, PartMaterials.FILE);
                if (!receipt.getString("part_materials_sha256").equals(FilesUtil.hash(materialFile))) {
                    throw new IOException("PBR material layer checksum does not match its receipt");
                }
                PartMaterials.load(materialFile, Mesh.load(editable));
                add(zip, PartMaterials.FILE, materialFile);
            }
            if (receipt.has("part_texture_manifest_sha256")) {
                PartTextureMaps maps = PartTextureMaps.load(directory, Mesh.load(editable),
                        receipt.getString("part_texture_manifest_sha256"));
                for (Map.Entry<String,File> entry : maps.outputs(directory).entrySet())
                    add(zip,entry.getKey(),entry.getValue());
            }
            if (receipt.has("motion")) {
                File script = new File(directory,"motion.foundry.txt");
                if (!receipt.getJSONObject("motion").getString("editable_script_sha256").equals(FilesUtil.hash(script))) {
                    throw new IOException("Editable animation checksum does not match its receipt");
                }
                add(zip,"motion.foundry.txt",script);
            }
            if(receipt.has("mesh_parts"))writeParts(zip,store.root,directory,editable,receipt);
            Set<String> written = new HashSet<>();
            for (File file : originals.values()) {
                if (written.add(file.getName())) add(zip, "sources/" + file.getName(), file);
            }
        }
    }

    private static void writeParts(ZipOutputStream zip,File root,File directory,File editable,JSONObject receipt) throws Exception {
        Mesh mesh=Mesh.load(editable);
        byte[] texture=receipt.has("texture_sha256")?Files.readAllBytes(new File(directory,"base-color.png").toPath()):null;
        SkeletalMotion motion=receipt.has("motion")?SkeletalMotion.parse(mesh,FilesUtil.readString(new File(directory,"motion.foundry.txt").toPath())):null;
        PartMaterials finishes=PartMaterials.load(receipt.has("part_materials_sha256")
                ? new File(directory,PartMaterials.FILE) : null, mesh);
        PartTextureMaps maps=PartTextureMaps.load(directory,mesh,
                receipt.optString("part_texture_manifest_sha256"));
        JSONArray entries=new JSONArray();
        File temporary=File.createTempFile("part-export-",".glb",new File(root,"cache"));
        try{
            var parts=mesh.parts();
            for(int i=0;i<parts.size();i++){
                Mesh.Part part=parts.get(i);
                String path="parts/part-"+(i+1)+".glb";
                Glb.write(mesh.extractPart(i),temporary,texture,motion,finishes.only(part.id()),maps.only(part.id()));
                JSONArray channels=new JSONArray();
                for(String channel:PartTextureMaps.CHANNELS)
                    if(maps.get(part.id(),channel)!=null)channels.put(channel);
                entries.put(new JSONObject().put("id",part.id()).put("name",part.name()).put("role",part.role())
                        .put("file",path).put("sha256",FilesUtil.hash(temporary))
                        .put("pbr_texture_channels",channels));
                add(zip,path,temporary);
            }
        }finally{
            Files.deleteIfExists(temporary.toPath());
            Files.deleteIfExists(new File(temporary+".tmp").toPath());
        }
        zip.putNextEntry(new ZipEntry("parts.json"));
        zip.write(new JSONObject().put("schema","asterion.foundry.parts.v1").put("parts",entries)
                .put("shared_skin",!mesh.bones.isEmpty()).put("native_acceptance",false).toString(2).getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static void add(ZipOutputStream zip, String name, File source) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        Files.copy(source.toPath(), zip);
        zip.closeEntry();
    }
}
