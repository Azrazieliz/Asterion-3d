package com.asterion.foundry;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;

/** Independent garment geometry exchange, exact rig and source preservation. */
public final class PartReplacementAcceptance {
    public static void main(String[] ignored) throws Exception {
        File directory = Files.createTempDirectory("foundry-part-replace-").toFile();
        try {
            Mesh parent = Recipes.create("Humanoid with separate tunic", 1, 12);
            SkinBlend.apply(parent, () -> {});
            File source = new File(directory, "immutable.mesh");
            parent.save(source);
            String sourceHash = FilesUtil.hash(source);
            Mesh donor = Mesh.load(source);
            Mesh.Part targetPart = parent.parts().get(1);
            Mesh.Part donorPart = donor.parts().get(1);
            for (int i = donorPart.firstVertex(); i < donor.partEnd(1); i++)
                donor.vertices.get(i)[1] += 0.08f;
            if (!PartReplacement.sameRig(parent,donor))
                throw new AssertionError("Exact shared rig was rejected");
            Mesh child = PartReplacement.replace(parent,targetPart.id(),donor,donorPart.id());
            if (!parent.parts().equals(child.parts()) || child.bones.size()!=parent.bones.size())
                throw new AssertionError("Replacement lost stable parts or skeleton");
            if (child.vertices.size()!=parent.vertices.size())
                throw new AssertionError("Part triangle count changed unexpectedly");
            for (int i = 0; i < child.vertices.size(); i++) {
                float[] actual = child.vertices.get(i);
                float[] expected = i < targetPart.firstVertex() ? parent.vertices.get(i) : donor.vertices.get(i);
                if (actual == expected || !Arrays.equals(actual,expected))
                    throw new AssertionError("Part isolation, donor geometry or deep copy failed at vertex "+i);
            }
            if (!sourceHash.equals(FilesUtil.hash(source)))
                throw new AssertionError("Parent checkpoint was modified");
            File output = new File(directory, "result.glb");
            Glb.write(child,output);
            if (output.length()<150)throw new AssertionError("GLB output missing");
            Mesh roundtrip = Mesh.load(saveMesh(directory,child));
            if (!roundtrip.parts().equals(parent.parts()))
                throw new AssertionError("Part IDs lost on checkpoint reload");
            reject(() -> PartReplacement.replace(parent,targetPart.id(),donor,donor.parts().get(0).id()));
            reject(() -> PartReplacement.replace(parent,"unknown",donor,donorPart.id()));
            Mesh wrongRig=Mesh.load(source);
            wrongRig.bones.get(0).y+=0.5f;
            reject(() -> PartReplacement.replace(parent,targetPart.id(),wrongRig,donorPart.id()));
            System.out.println("PASS exact-rig part replacement, independent donor surface, preserved source and export");
        } finally {
            for(File file:directory.listFiles())Files.deleteIfExists(file.toPath());
            Files.deleteIfExists(directory.toPath());
        }
    }

    private static File saveMesh(File directory,Mesh mesh) throws Exception {
        File output = new File(directory,"child.mesh");
        mesh.save(output);
        return output;
    }
    private interface Checked {void run() throws Exception;}
    private static void reject(Checked work) throws Exception {
        try {work.run();throw new AssertionError("Incompatible donor was accepted");}
        catch (IllegalArgumentException expected) {}
    }
}
