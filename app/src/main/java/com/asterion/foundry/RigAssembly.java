package com.asterion.foundry;

/** Builds a two-actor editable scene while retaining actor mesh parts and rest rig ordering. */
final class RigAssembly {
    static final String VERSION = "two-actor-rig-assembly-v1";

    static Mesh merge(Mesh first, Mesh second, float partnerX, float partnerY, float partnerZ,
                      VisualHull.Budget budget) throws Exception {
        for(float amount:new float[]{partnerX,partnerY,partnerZ}){
            if(!Float.isFinite(amount)||Math.abs(amount)>1000)throw new IllegalArgumentException("Actor placement must be within ±1000 metres");
        }
        requireActor(first);
        requireActor(second);
        if(first.bones.size()+second.bones.size()>512||first.parts().size()+second.parts().size()>64
                ||first.vertices.size()+second.vertices.size()>Mesh.LIMIT){
            throw new IllegalArgumentException("Actor assembly exceeds the rig or geometry budget");
        }
        Mesh result=new Mesh();
        append(result,first,"A",0,0,0,budget);
        append(result,second,"B",partnerX,partnerY,partnerZ,budget);
        result.validate();
        return result;
    }

    static float defaultPartnerX(Mesh first,Mesh second){
        float[] a=first.bounds(),b=second.bounds();
        return a[3]-b[0]+.25f*Math.max(a[4]-a[1],b[4]-b[1]);
    }

    private static void requireActor(Mesh actor){
        actor.validate();
        if(actor.bones.isEmpty()||actor.bones.stream().filter(joint->joint.parent<0).count()!=1){
            throw new IllegalArgumentException("Choose two independent editable rigs, each with one root");
        }
    }

    private static void append(Mesh result,Mesh source,String actor,float x,float y,float z,
                               VisualHull.Budget budget) throws Exception {
        int base=result.bones.size();
        for(Mesh.Bone bone:source.bones){
            result.bone(bone.name,bone.parent<0?-1:bone.parent+base,bone.x+x,bone.y+y,bone.z+z);
        }
        var parts=source.parts();
        for(int i=0;i<parts.size();i++){
            Mesh.Part part=parts.get(i);
            String name=part.name().length()>76?part.name().substring(0,76):part.name();
            result.beginPart(actor+" "+name,part.role());
            for(int v=part.firstVertex();v<source.partEnd(i);v++){
                if((v&4095)==0)budget.check();
                float[] vertex=source.vertices.get(v).clone();
                vertex[0]+=x;vertex[1]+=y;vertex[2]+=z;
                Mesh.offsetJoints(vertex,base);
                result.vertices.add(vertex);
            }
        }
    }
}
