package com.asterion.foundry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Generates local parent/child transitions from an existing editable rigid bind. */
final class SkinBlend {
    static final String VERSION = "local-joint-blend-v1";
    private record Position(float x,float y,float z) {
        static Position of(float[] vertex) {return new Position(vertex[0]+0f,vertex[1]+0f,vertex[2]+0f);}
    }

    private record Boundary(int parent, int child, double x, double y, double z,
                            double dx, double dy, double dz, double radius) {
        double distance(float[] vertex) {
            double a=vertex[0]-x,b=vertex[1]-y,c=vertex[2]-z;
            return (a*a+b*b+c*c)/(radius*radius);
        }

        float childWeight(float[] vertex) {
            double offset=(vertex[0]-x)*dx+(vertex[1]-y)*dy+(vertex[2]-z)*dz;
            double t=Math.max(0,Math.min(1,.5+.5*offset/radius));
            return (float)(t*t*(3-2*t));
        }
    }

    static int apply(Mesh mesh, VisualHull.Budget budget) throws Exception {
        mesh.validate();
        if (mesh.bones.isEmpty() || mesh.blended()) return 0;
        List<List<Boundary>> neighbours = new ArrayList<>(mesh.bones.size());
        for (Mesh.Bone ignored : mesh.bones) neighbours.add(new ArrayList<>());
        for (int i=0;i<mesh.bones.size();i++) {
            Mesh.Bone child=mesh.bones.get(i);
            if (child.parent<0) continue;
            Mesh.Bone parent=mesh.bones.get(child.parent);
            if (parent.parent<0 && parent.name.equals("root")) continue;
            double x=(double)child.x-parent.x,y=(double)child.y-parent.y,z=(double)child.z-parent.z;
            double length=Math.sqrt(x*x+y*y+z*z);
            if (length<1e-6) continue;
            Boundary boundary=new Boundary(child.parent,i,child.x,child.y,child.z,x/length,y/length,z/length,.32*length);
            neighbours.get(child.parent).add(boundary);
            neighbours.get(i).add(boundary);
        }
        Map<Position,float[]> transitions=new HashMap<>();
        for(int i=0;i<mesh.vertices.size();i++){
            if((i&4095)==0)budget.check();
            float[] vertex=mesh.vertices.get(i);
            int owner=Mesh.joint(vertex,0);
            Boundary selected=null;
            double nearest=1;
            for(Boundary boundary:neighbours.get(owner)){
                double distance=boundary.distance(vertex);
                if(distance<nearest){nearest=distance;selected=boundary;}
            }
            if(selected==null)continue;
            boolean fromParent=owner==selected.parent;
            float childWeight=selected.childWeight(vertex);
            // Fade the neighbouring influence back to the authored rigid bind at the edge of the joint zone.
            float falloff=(float)Math.min(1,2*(1-Math.sqrt(nearest)));
            float weight=falloff*(fromParent?childWeight:1-childWeight);
            if(weight>1e-5f){
                transitions.putIfAbsent(Position.of(vertex),Mesh.bindPair(vertex,owner,
                        fromParent?selected.child:selected.parent,weight));
            }
        }
        int changed=0;
        for(int i=0;i<mesh.vertices.size();i++){
            if((i&4095)==0)budget.check();
            float[] vertex=mesh.vertices.get(i);
            float[] transition=transitions.get(Position.of(vertex));
            if(transition==null)continue;
            int owner=Mesh.joint(vertex,0), first=Mesh.joint(transition,0), second=Mesh.joint(transition,1);
            if(owner!=first&&owner!=second)continue;
            mesh.vertices.set(i,Mesh.bindPair(vertex,owner,owner==first?second:first,
                    Mesh.weight(transition,owner==first?1:0)));
            changed++;
        }
        mesh.validate();
        return changed;
    }
}
