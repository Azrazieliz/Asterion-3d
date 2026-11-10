package com.asterion.foundry;

import java.util.ArrayList;
import java.util.List;

/** Bounded position-based relaxation for a pinned procedural garment on its rest body. */
final class ClothDrape {
    static final String VERSION="rest_tunic_position_constraints_v1";
    private static final double DT=1d/120;

    private record Edge(int a,int b,double length,double stiffness) {}

    static float[][] relax(float[][] rings,int sides){
        int count=rings.length*sides, collar=count-sides;
        double[][] points=new double[count][3],last=new double[count][3];
        for(int row=0;row<rings.length;row++)for(int col=0;col<sides;col++){
            double angle=2*Math.PI*col/sides;
            int i=row*sides+col;
            points[i][0]=rings[row][1]*Math.cos(angle);
            points[i][1]=rings[row][0];
            points[i][2]=rings[row][2]*Math.sin(angle);
            last[i]=points[i].clone();
        }
        List<Edge> edges=new ArrayList<>();
        for(int row=0;row<rings.length;row++)for(int col=0;col<sides;col++){
            int i=row*sides+col,next=row*sides+(col+1)%sides;
            edge(edges,points,i,next,.9);
            if(row+1<rings.length){
                edge(edges,points,i,i+sides,.9);
                edge(edges,points,i,next+sides,.55);
                edge(edges,points,next,i+sides,.55);
            }
            if(row+2<rings.length)edge(edges,points,i,i+2*sides,.18);
        }
        for(int step=0;step<120;step++){
            for(int i=0;i<collar;i++)for(int axis=0;axis<3;axis++){
                double current=points[i][axis];
                points[i][axis]+=.90*(current-last[i][axis])-(axis==1?9.81*DT*DT:0);
                last[i][axis]=current;
            }
            for(int iteration=0;iteration<6;iteration++){
                for(Edge e:edges)constrain(e,points,collar);
                for(int i=0;i<collar;i++)collide(points[i]);
            }
        }
        float[][] result=new float[count][3];
        for(int i=0;i<count;i++)for(int axis=0;axis<3;axis++)result[i][axis]=(float)points[i][axis];
        return result;
    }

    private static void edge(List<Edge> edges,double[][] points,int a,int b,double stiffness){
        double[] x=points[a],y=points[b];
        edges.add(new Edge(a,b,length(x[0]-y[0],x[1]-y[1],x[2]-y[2]),stiffness));
    }

    private static void constrain(Edge edge,double[][] points,int collar){
        int a=edge.a,b=edge.b;
        double wa=a<collar?1:0,wb=b<collar?1:0;
        if(wa+wb==0)return;
        double[] p=points[a],q=points[b];
        double length=length(p[0]-q[0],p[1]-q[1],p[2]-q[2]);
        if(length<1e-8)return;
        double correction=edge.stiffness*(length-edge.length)/(length*(wa+wb));
        for(int axis=0;axis<3;axis++){
            double delta=(q[axis]-p[axis])*correction;
            p[axis]+=wa*delta;q[axis]-=wb*delta;
        }
    }

    private static void collide(double[] p){
        // Padded analytic proxy for the template's torso and pelvis, not the visual skin.
        double section=(p[1]-1.20)/.32;
        double radius=Math.sqrt(Math.max(0,1-section*section));
        double rx=.22*radius+.012,rz=.13*radius+.012;
        if(p[1]<1.02){rx=Math.max(rx,.208);rz=Math.max(rz,.105);}
        double t=Math.hypot(p[0]/rx,p[2]/rz);
        if(t>1e-8&&t<1){p[0]/=t;p[2]/=t;}
    }

    private static double length(double x,double y,double z){return Math.sqrt(x*x+y*y+z*z);}
}
