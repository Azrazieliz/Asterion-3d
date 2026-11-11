package com.asterion.foundry;

import java.util.Arrays;

/**
 * Compact deterministic, CPU-only z-buffer geometry thumbnail for project cards.
 * Uses actual mesh triangles and linear vertex colour (sRGB converted for screen).
 * It does not sample PBR textures or certify fidelity, topology or rig quality.
 */
final class MeshThumbnail {
    static final String VERSION="cpu-mesh-thumbnail-v1";
    static final int SIZE=192;
    private static final int BACK=0xff152236;
    record Result(int[] argb,int width,int height,int visiblePixels,int sampledTriangles) {}
    private MeshThumbnail(){}

    static Result render(Mesh mesh,VisualHull.Budget budget)throws Exception {
        if(mesh==null)throw new IllegalArgumentException("No mesh to preview");
        mesh.validate();
        int count=mesh.vertices.size();
        if(count==0)throw new IllegalArgumentException("Cannot preview empty mesh");
        float[] bounds=mesh.bounds();
        double cx=(bounds[0]+bounds[3])/2.0,cy=(bounds[1]+bounds[4])/2.0,
                cz=(bounds[2]+bounds[5])/2.0;
        double yaw=Math.toRadians(32),pitch=Math.toRadians(18);
        double sy=Math.sin(yaw),cyaw=Math.cos(yaw),sp=Math.sin(pitch),cp=Math.cos(pitch);
        double[] x=new double[count],y=new double[count],z=new double[count];
        double xmin=Double.POSITIVE_INFINITY,xmax=Double.NEGATIVE_INFINITY;
        double ymin=Double.POSITIVE_INFINITY,ymax=Double.NEGATIVE_INFINITY;
        for(int i=0;i<count;i++){
            if((i&16383)==0)budget.check();
            float[] v=mesh.vertices.get(i);
            double dx=v[0]-cx,dy=v[1]-cy,dz=v[2]-cz;
            double rx=cyaw*dx+sy*dz;
            double rz=-sy*dx+cyaw*dz;
            x[i]=rx;
            y[i]=cp*dy-sp*rz;
            z[i]=sp*dy+cp*rz;
            xmin=Math.min(xmin,x[i]);xmax=Math.max(xmax,x[i]);
            ymin=Math.min(ymin,y[i]);ymax=Math.max(ymax,y[i]);
        }
        double maxRange=Math.max(1e-5,Math.max(xmax-xmin,ymax-ymin));
        double scale=(SIZE-26.)/maxRange;
        double ox=(SIZE-1)/2.0-(xmin+xmax)*scale/2.0;
        double oy=(SIZE-1)/2.0+(ymin+ymax)*scale/2.0;
        int[] output=new int[SIZE*SIZE];
        Arrays.fill(output,BACK);
        float[] depths=new float[SIZE*SIZE];
        Arrays.fill(depths,Float.NEGATIVE_INFINITY);
        int triangleCount=count/3;
        // Back-pressure: use a deterministic stride only for extraordinarily
        // complex geometry, and never pretend the thumbnail covers all surfaces.
        int stride=Math.max(1,(triangleCount+149999)/150000);
        int tested=0,visible=0;
        for(int t=0;t<triangleCount;t+=stride){
            if((t&1023)==0)budget.check();
            int a=3*t,b=a+1,c=a+2;
            double ax=ox+scale*x[a],ay=oy-scale*y[a];
            double bx=ox+scale*x[b],by=oy-scale*y[b];
            double dx=ox+scale*x[c],dy=oy-scale*y[c];
            double area=(bx-ax)*(dy-ay)-(by-ay)*(dx-ax);
            if(Math.abs(area)<1e-7)continue;
            int left=Math.max(0,(int)Math.floor(Math.min(ax,Math.min(bx,dx))));
            int right=Math.min(SIZE-1,(int)Math.ceil(Math.max(ax,Math.max(bx,dx))));
            int top=Math.max(0,(int)Math.floor(Math.min(ay,Math.min(by,dy))));
            int bottom=Math.min(SIZE-1,(int)Math.ceil(Math.max(ay,Math.max(by,dy))));
            if(right<left||bottom<top)continue;
            // View-space face normal: depth-aware flat lighting rather than a
            // fabricated physically-based material render.
            double ux=x[b]-x[a],uy=y[b]-y[a],uz=z[b]-z[a];
            double vx=x[c]-x[a],vy=y[c]-y[a],vz=z[c]-z[a];
            double nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx;
            double normalLength=Math.sqrt(nx*nx+ny*ny+nz*nz);
            double light=normalLength>1e-10
                ?Math.abs((-.37*nx+.58*ny+.72*nz)/normalLength):0;
            double intensity=Math.max(.34,Math.min(.96,.38+.57*light));
            float[] v0=mesh.vertices.get(a),v1=mesh.vertices.get(b),v2=mesh.vertices.get(c);
            double alpha=(v0[11]+v1[11]+v2[11])/3;
            if(alpha<.08)continue;
            int rr=screen((v0[8]+v1[8]+v2[8])/3,intensity);
            int gg=screen((v0[9]+v1[9]+v2[9])/3,intensity);
            int bb=screen((v0[10]+v1[10]+v2[10])/3,intensity);
            int colour=0xff000000|(rr<<16)|(gg<<8)|bb;
            tested++;
            for(int py=top;py<=bottom;py++)for(int px=left;px<=right;px++){
                double fx=px+.5,fy=py+.5;
                double u=((bx-fx)*(dy-fy)-(by-fy)*(dx-fx))/area;
                double v=((dx-fx)*(ay-fy)-(dy-fy)*(ax-fx))/area;
                double w=1-u-v;
                if(u<0||v<0||w<0)continue;
                double depth=u*z[a]+v*z[b]+w*z[c];
                int index=py*SIZE+px;
                if(depth>depths[index]){
                    if(depths[index]==Float.NEGATIVE_INFINITY)visible++;
                    depths[index]=(float)depth;
                    output[index]=colour;
                }
            }
        }
        return new Result(output,SIZE,SIZE,visible,tested);
    }
    private static int screen(double linear,double lighting){
        double v=Math.max(0,Math.min(1,linear*lighting));
        double srgb=v<=.0031308?12.92*v:1.055*Math.pow(v,1./2.4)-.055;
        return Math.max(0,Math.min(255,(int)Math.round(srgb*255)));
    }
}
