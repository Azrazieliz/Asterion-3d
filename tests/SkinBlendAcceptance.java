package com.asterion.foundry;

import java.io.File;
import java.nio.FloatBuffer;

public final class SkinBlendAcceptance {
    public static void main(String[] args) throws Exception {
        Mesh mesh = new Mesh();
        mesh.bone("upper",-1,0,0,0);
        mesh.bone("lower",0,1,0,0);
        float[] seam={1,0,0}, edge={1,.08f,0}, start={0,0,0}, end={2,0,0};
        mesh.tri(start,seam,edge,Recipes.SKIN,0);
        mesh.tri(seam,end,edge,Recipes.SKIN,1);
        File legacy=new File("test-output/skin-legacy.mesh");
        mesh.save(legacy);
        Mesh blended=Mesh.load(legacy);
        if(blended.blended()||SkinBlend.apply(blended,()->{})<4)throw new AssertionError("Joint transition not generated");
        File checkpoint=new File("test-output/skin-weighted.mesh");
        blended.save(checkpoint);
        Mesh restored=Mesh.load(checkpoint);
        if(!restored.blended()||Mesh.weight(restored.vertices.get(1),1)!=.5f||mesh.blended()){
            throw new AssertionError("Skin checkpoint failed to preserve authored and blended forms separately");
        }
        SkeletalMotion motion=SkeletalMotion.parse(restored,"clip Joint transition\nmove lower 0 0 0 0\nmove lower 1 0 .4 0\n");
        var evidence=Glb.write(restored,new File("test-output/weighted_motion.glb"),null,motion);
        if(!evidence.blended()||evidence.deformationChecks()==0)throw new AssertionError("Blended deformation was not checked");
        FloatBuffer posed=FloatBuffer.allocate(restored.vertices.size()*12);
        motion.playback(restored).write(1,posed);
        if(Math.abs(posed.get(13)-.2f)>1e-5||Math.abs(posed.get(37)-.2f)>1e-5){
            throw new AssertionError("The same seam point split or failed to follow both joints");
        }
        for(String kind:new String[]{"Humanoid template","Quadruped template"}){
            Mesh template=Recipes.create(kind,1,12);
            if(SkinBlend.apply(template,()->{})==0)throw new AssertionError("No skin transitions for "+kind);
            String action=kind.startsWith("Humanoid")?"walk in place":"tail wag";
            SkeletalMotion.parse(template,SkeletalMotion.compose(template,action)).validate(template);
        }
        System.out.println("Skin blend passed legacy migration, shared seam, normalized GLB weights and clip deformation");
    }
}
