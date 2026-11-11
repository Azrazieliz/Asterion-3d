package com.asterion.foundry;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import org.json.JSONObject;

/** Host acceptance for checksum-bound stage reuse, invalidation and surface evidence. */
public final class StagePipelineAcceptance {
    public static void main(String[] unused) throws Exception {
        File root = Files.createTempDirectory("foundry-stages-").toFile();
        try {
            File meshFile = new File(root,"checkpoint.mesh");
            File metrics = new File(root,"reference-metrics.json");
            File base = new File(root,"base-color.png");
            File exported = new File(root,"asset.glb");
            File receipt = new File(root,"receipt.json");
            File handoff = new File(root,"import-manifest.json");

            Mesh mesh = new Mesh();
            mesh.box(0,0,0,1,1,1,new float[]{0.3f,0.6f,0.8f},0);
            mesh.save(meshFile);
            FilesUtil.write(metrics,"{}");
            StageManifest ledger = new StageManifest(root);
            String request = FilesUtil.hash("request1");
            if (!ledger.publish("geometry",Map.of("production_key",request),
                    Map.of("checkpoint.mesh",meshFile,"reference-metrics.json",metrics),3))
                throw new AssertionError("First geometry checkpoint was not committed");
            if (!ledger.has("geometry")) throw new AssertionError("Missing geometry evidence");

            int[] palette = {0xffff0000,0xff00ff00,0xff0000ff,0x00000000};
            JSONObject audit = SurfaceAudit.inspect(mesh,palette,2,2);
            if (audit.getInt("texture_sample_count")!=4
                    || audit.getInt("sampled_palette_5bit_bins")!=4
                    || audit.getDouble("sampled_transparent_fraction")!=0.25)
                throw new AssertionError("Surface audit lost sample or transparency");
            if (!"unverified".equals(audit.getString("reference_texture_fidelity")))
                throw new AssertionError("Unreviewed texture was falsely accepted");
            if(audit.getInt("uv_overlap_checked_triangles")<1
                    || "not_checked".equals(audit.getString("uv_overlap")))
                throw new AssertionError("UV overlap audit failed to emit bounded evidence");
            FilesUtil.write(base,new byte[]{1,2,3,4});
            ledger.publish("surface",Map.of("geometry_sha256",FilesUtil.hash(meshFile)),
                    Map.of("base-color.png",base),2);
            FilesUtil.write(exported,new byte[]{9,8,7});
            FilesUtil.write(receipt,"{}");
            FilesUtil.write(handoff,"{}");
            ledger.publish("export",Map.of("geometry_sha256",FilesUtil.hash(meshFile),
                            "surface_sha256",FilesUtil.hash(base)),
                    Map.of("asset.glb",exported,"receipt.json",receipt,
                            "import-manifest.json",handoff),1);
            new StageManifest(root).verifyAll();
            if (ledger.publish("geometry",Map.of("production_key",request),
                    Map.of("checkpoint.mesh",meshFile,"reference-metrics.json",metrics),99)
                    || !ledger.has("export"))
                throw new AssertionError("Unchanged geometry destroyed accepted downstream stages");
            FilesUtil.write(base,new byte[]{4,3,2,1});
            try {
                ledger.verify("surface");
                throw new AssertionError("Tampering went undetected");
            } catch (IOException expected) {
                if (!expected.getMessage().contains("checksum")) throw expected;
            }
            FilesUtil.write(metrics,"{\"new\":true}");
            if (!ledger.publish("geometry",Map.of("production_key",request),
                    Map.of("checkpoint.mesh",meshFile,"reference-metrics.json",metrics),3)
                    || ledger.has("surface") || ledger.has("export"))
                throw new AssertionError("Dependent stages remained accepted after source change");
            try {
                ledger.publish("surface",Map.of("geometry_sha256",FilesUtil.hash(meshFile)),
                        Map.of("base-color.png",new File(root,"../outside.png")),1);
                throw new AssertionError("Stage accepted unsafe output path");
            } catch (IOException expected) {
                // No stage output outside the project may be admitted.
            }
            File mapsFolder = new File(root, PartTextureMaps.FOLDER);
            Files.createDirectories(mapsFolder.toPath());
            File map = new File(root, PartTextureMaps.filename("asset","normal"));
            File materialManifest = new File(root, PartTextureMaps.MANIFEST);
            FilesUtil.write(map, new byte[]{11,12,13});
            FilesUtil.write(materialManifest, "fixture map receipt");
            ledger.publish("surface", Map.of("geometry_sha256",FilesUtil.hash(meshFile)),
                    Map.of(PartTextureMaps.MANIFEST,materialManifest,
                            PartTextureMaps.filename("asset","normal"),map),4);
            ledger.verify("surface");
            FilesUtil.write(map,new byte[]{11,12,14});
            try {
                ledger.verify("surface");
                throw new AssertionError("Tampered nested material map remained accepted");
            } catch(IOException expected){
                if(!expected.getMessage().contains("checksum"))throw expected;
            }
            try {
                ledger.publish("surface", Map.of("geometry_sha256",FilesUtil.hash(meshFile)),
                        Map.of("part-textures/../receipt.json",receipt),1);
                throw new AssertionError("Unsafe nested material output was admitted");
            } catch(IOException expected){}
            System.out.println("PASS stage evidence, safe outputs, reuse, invalidation, tamper detection and surface audit");
        } finally {
            for (File child : root.listFiles()) deleteRecursively(child);
            Files.deleteIfExists(root.toPath());
        }
    }
    private static void deleteRecursively(File file) throws Exception {
        if(file.isDirectory())for(File child:file.listFiles())deleteRecursively(child);
        Files.deleteIfExists(file.toPath());
    }
}
