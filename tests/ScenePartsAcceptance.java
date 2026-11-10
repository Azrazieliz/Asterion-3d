package com.asterion.foundry;

import java.io.File;
import java.util.List;

public final class ScenePartsAcceptance {
    public static void main(String[] args) throws Exception {
        File output=new File("test-output");
        output.mkdirs();
        Mesh clothed=Recipes.create("Humanoid with separate tunic",1,12);
        if(!clothed.parts().stream().map(Mesh.Part::role).toList().equals(List.of("body","garment"))){
            throw new AssertionError("Clothing was not kept independent of the body");
        }
        SkinBlend.apply(clothed,()->{});
        File checkpoint=new File(output,"garment-separate.mesh");
        clothed.save(checkpoint);
        Mesh restored=Mesh.load(checkpoint);
        Mesh tunic=restored.extractPart(1);
        if(!restored.parts().equals(clothed.parts())||tunic.vertices.size()>=restored.vertices.size()
                ||tunic.bones.size()!=restored.bones.size()||tunic.bounds()[1]>=.80f){
            throw new AssertionError("Draped garment, body separation or shared rig lost through checkpoint");
        }
        Mesh revision=MeshRevision.apply(restored,1.02f,1,1,null);
        if(!revision.parts().equals(restored.parts()))throw new AssertionError("Revision merged semantic parts");
        int rootB=restored.bones.size();
        Mesh scene=RigAssembly.merge(restored,revision,RigAssembly.defaultPartnerX(restored,revision),0,0,()->{});
        if(scene.parts().size()!=4||scene.bones.get(rootB).parent!=-1
                ||Mesh.joint(scene.vertices.get(scene.parts().get(2).firstVertex()),0)<rootB){
            throw new AssertionError("Scene lost actor identity, garment layers or rig binding");
        }
        SkeletalMotion clip=SkeletalMotion.parse(scene,"clip Scene\nmove @0 1 0 .1 0\nmove @"+rootB+" 1 0 .15 0\n");
        Glb.write(scene,new File(output,"scene_parts.glb"),null,clip);
        Glb.write(scene.extractPart(3),new File(output,"scene_part_garment.glb"),null,clip);
        Mesh dragon=Recipes.create("Articulated dragon",1,12);
        for(String action:List.of("dragon flight","dragon perch","dragon landing")){
            SkeletalMotion motion=SkeletalMotion.parse(dragon,SkeletalMotion.compose(dragon,action));
            if(action.equals("dragon flight"))Glb.write(dragon,new File(output,"dragon_flight_parts.glb"),null,motion);
            else motion.validate(dragon);
        }
        System.out.println("Scene parts passed garment drape, independent actor roots, checkpoint and shared-motion export");
    }
}
