package com.asterion.foundry;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Real GLB PBR material isolation, inheritance, persistence and reject-on-mismatch. */
public final class PartMaterialsAcceptance {
    public static void main(String[] ignored) throws Exception {
        File directory = Files.createTempDirectory("foundry-part-pbr-").toFile();
        try {
            Mesh mesh = Recipes.create("Humanoid with separate tunic", 1, 12);
            SkinBlend.apply(mesh, () -> {});
            Mesh.Part body = mesh.parts().get(0);
            Mesh.Part garment = mesh.parts().get(1);
            File original = new File(directory, "original.mesh");
            mesh.save(original);
            String immutable = FilesUtil.hash(original);
            PartMaterials initial = PartMaterials.empty();
            if (initial.forPart(body).metallic() != 0 || initial.forPart(garment).roughness() != 0.8)
                throw new AssertionError("Default material factors changed");
            PartMaterials edited = initial.change(mesh, garment.id(), 0.42, 0.18);
            if (edited.forPart(body).roughness() != initial.forPart(body).roughness()
                    || edited.forPart(garment).metallic() != 0.42)
                throw new AssertionError("Part-local finish leaked to sibling");
            File layer = new File(directory, PartMaterials.FILE);
            edited.save(layer);
            PartMaterials loaded = PartMaterials.load(layer, mesh);
            if (!edited.forPart(garment).equals(loaded.forPart(garment)))
                throw new AssertionError("PBR finish lost in round trip");
            PartMaterials inherited = loaded.change(mesh, body.id(), 0.12, 0.91);
            if (inherited.forPart(garment).roughness() != 0.18)
                throw new AssertionError("Inherited finish was reset");
            if (inherited.change(mesh, body.id(), 0, 0.8).forPart(body).metallic() != 0)
                throw new AssertionError("Defaults cannot be restored");
            File asset = new File(directory, "asset.glb");
            Glb.write(mesh, asset, null, null, loaded);
            byte[] binary = Files.readAllBytes(asset.toPath());
            ByteBuffer header = ByteBuffer.wrap(binary).order(ByteOrder.LITTLE_ENDIAN);
            int jsonLength = header.getInt(12);
            String json = new String(binary, 20, jsonLength, StandardCharsets.UTF_8);
            int materials = json.indexOf("\"materials\":[");
            int garmentMaterial = json.indexOf("\"name\":" + FilesUtil.quote(garment.name()), materials);
            int bodyMaterial = json.indexOf("\"name\":" + FilesUtil.quote(body.name()), materials);
            if (materials < 0 || garmentMaterial < 0 || bodyMaterial < 0
                    || !json.substring(garmentMaterial, Math.min(json.length(), garmentMaterial + 220))
                            .contains("\"metallicFactor\":0.42,\"roughnessFactor\":0.18")
                    || !json.substring(bodyMaterial, Math.min(json.length(), bodyMaterial + 220))
                            .contains("\"metallicFactor\":0.0,\"roughnessFactor\":0.8"))
                throw new AssertionError("GLB material factors were not exported per part");
            File partAsset = new File(directory, "garment.glb");
            Glb.write(mesh.extractPart(1), partAsset, null, null, loaded.only(garment.id()));
            if (!partAsset.isFile() || partAsset.length() < 150)
                throw new AssertionError("Independent part PBR export failed");
            if (!immutable.equals(FilesUtil.hash(original)))
                throw new AssertionError("Editable PBR revision modified parent mesh");
            reject(() -> initial.change(mesh, "absent", 0.1, 0.5));
            reject(() -> initial.change(mesh, garment.id(), Double.NaN, 0.5));
            reject(() -> initial.change(mesh, garment.id(), 0.1, 1.1));
            FilesUtil.write(layer, PartMaterials.VERSION + "\n" + garment.id() + "\tInfinity\t0.5\n");
            try {
                PartMaterials.load(layer, mesh);
                throw new AssertionError("Invalid saved finish was admitted");
            } catch (java.io.IOException expected) { }
            FilesUtil.write(layer, PartMaterials.VERSION + "\nabsent\t0.2\t0.5\n");
            try {
                PartMaterials.load(layer, mesh);
                throw new AssertionError("Missing part mapping was silently discarded");
            } catch (java.io.IOException expected) { }
            System.out.println("PASS isolated editable part PBR factors, inherited revisions, GLB export, strict persistence");
        } finally {
            for (File file : directory.listFiles()) Files.deleteIfExists(file.toPath());
            Files.deleteIfExists(directory.toPath());
        }
    }

    private interface Checked { void run() throws Exception; }
    private static void reject(Checked work) throws Exception {
        try { work.run(); throw new AssertionError("Invalid edit was accepted"); }
        catch (IllegalArgumentException expected) { }
    }
}
