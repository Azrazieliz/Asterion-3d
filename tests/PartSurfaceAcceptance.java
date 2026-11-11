package com.asterion.foundry;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;

/** Part-local material revision must not disturb shared rig or other parts. */
public final class PartSurfaceAcceptance {
    public static void main(String[] unused) throws Exception {
        File dir = Files.createTempDirectory("foundry-part-surface-").toFile();
        try {
            Mesh original = Recipes.create("Humanoid with separate tunic", 1, 12);
            SkinBlend.apply(original, () -> {});
            Mesh.Part body = original.parts().get(0);
            Mesh.Part tunic = original.parts().get(1);
            File immutable = new File(dir,"original.mesh");
            original.save(immutable);
            String originalSha = FilesUtil.hash(immutable);

            Mesh coloured = PartSurface.recolour(original, tunic.id(), 0xff4030, 0.75f);
            if (!coloured.parts().equals(original.parts()) || coloured.bones.size()!=original.bones.size())
                throw new AssertionError("Part revision changed part IDs or rig order");
            for(int i=0;i<original.bones.size();i++) {
                Mesh.Bone a=original.bones.get(i),b=coloured.bones.get(i);
                if (!a.name.equals(b.name)||a.parent!=b.parent
                        ||a.x!=b.x||a.y!=b.y||a.z!=b.z)
                    throw new AssertionError("Rig rest pose changed");
            }
            boolean changed=false;
            for(int i=0;i<original.vertices.size();i++) {
                float[] a=original.vertices.get(i), b=coloured.vertices.get(i);
                if (a==b || a.length!=b.length) throw new AssertionError("Vertices were shared or re-laid out");
                for(int channel=0;channel<a.length;channel++) {
                    if(i<body.firstVertex() || i>=tunic.firstVertex() && channel!=8 && channel!=9 && channel!=10
                            || i<tunic.firstVertex()) {
                        if(Float.floatToIntBits(a[channel])!=Float.floatToIntBits(b[channel]))
                            throw new AssertionError("Unrelated colour, geometry, UV, alpha or skin was modified");
                    } else if(channel>=8 && channel<=10 && a[channel]!=b[channel]) changed=true;
                }
            }
            if(!changed)throw new AssertionError("Selected part colour did not change");
            if(PartSurface.recolour(original,tunic.id(),0x00ff00,0).vertices.stream()
                    .anyMatch(v -> v == original.vertices.get(0))) {
                throw new AssertionError("A revision reused parent arrays");
            }
            File revised = new File(dir,"revised.mesh");
            coloured.save(revised);
            Mesh loaded=Mesh.load(revised);
            if(!loaded.parts().equals(original.parts()))throw new AssertionError("Saved part IDs changed");
            File glb=new File(dir,"revised.glb");
            Glb.write(loaded,glb);
            if(!glb.isFile()||glb.length()<100)throw new AssertionError("GLB export failed");
            if(!originalSha.equals(FilesUtil.hash(immutable)))throw new AssertionError("Parent bytes changed");
            expectFailure(() -> PartSurface.recolour(original,"unknown-part",0xffffff,1));
            expectFailure(() -> PartSurface.recolour(original,tunic.id(),0xffffff,Float.NaN));
            expectFailure(() -> PartSurface.recolour(original,tunic.id(),-1,1));
            expectFailure(() -> PartSurface.recolour(original,tunic.id(),0xffffff,1.1f));
            System.out.println("PASS independent part colour, rig/UV/texture-binding preservation, export and immutable parent");
        } finally {
            for(File f:dir.listFiles())Files.deleteIfExists(f.toPath());
            Files.deleteIfExists(dir.toPath());
        }
    }
    interface Checked {void run() throws Exception;}
    private static void expectFailure(Checked work) throws Exception {
        try {work.run();throw new AssertionError("Invalid revision was accepted");}
        catch(IllegalArgumentException expected) {}
    }
}
