package com.asterion.foundry;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * Separate, conservative print export path. Foundry GLB and NoWorld original data
 * are not modified. Converts metres/Y-up to mm/Z-up, with explicit target height.
 * A full-edge orientation/manifold audit is mandatory before binary STL writing.
 *
 * Passing the geometry gate does NOT prove printable walls, supportability,
 * self-intersection freedom, closed internal voids, watertight material details
 * or any actual printer/slicer acceptance. No automatic mesh repair or smoothing
 * is performed; they could destroy reference-critical horns or wing membranes.
 */
public final class PrintExport {
    public static final String VERSION="foundry-binary-stl-strict-v1";
    private static final double QUANTIZATION_MM=.01;
    private static final double MIN_VOLUME_MM3=.001;
    private static final int MAX_COORD_BIN=2_097_151; // 21 bits per axis, collision-free
    private static final long MASK=(1L<<21)-1;
    private static final long MAX_EDGES=Mesh.LIMIT;
    private static final int HEADER_BYTES=80;

    public record Options(double heightMm, boolean allowSeparateClosedShells) {
        public Options(double heightMm) { this(heightMm,false); }
        public Options {
            if(!Double.isFinite(heightMm)||heightMm<10||heightMm>600)
                throw new IllegalArgumentException("Print height must be 10 to 600 mm");
        }
    }

    public record Audit(int triangles, int parts, double widthMm, double depthMm,
                        double heightMm, long boundaryEdges, long nonManifoldEdges,
                        long windingConflicts, long degenerateTriangles,
                        long inwardOrZeroVolumeParts, double shortestEdgeMm,
                        double signedVolumeMm3, int disconnectedShells,
                        boolean multiShellMode, boolean technicallyClosed) {
        public void requireGeometryGate() throws IOException {
            if(!technicallyClosed)
                throw new IOException("Print geometry rejected: "+boundaryEdges+
                        " boundary edges, "+nonManifoldEdges+" non-manifold edges, "+
                        windingConflicts+" winding conflicts, "+degenerateTriangles+
                        " degenerate triangles, "+inwardOrZeroVolumeParts+
                        " inward/zero-volume parts, "+disconnectedShells+
                        " disconnected shells. Join the source or explicitly allow closed multi-piece printing.");
        }
        public String summary() {
            return triangles+" triangles · "+parts+" part(s) · "+
                String.format(java.util.Locale.ROOT,"%.1f × %.1f × %.1f mm",widthMm,depthMm,heightMm)
                +" · closed="+technicallyClosed+" · shells="+disconnectedShells+
                (multiShellMode?" (multiple closed shells permitted)":" (single shell required)")+
                " · wall thickness, support structures and self-intersections NOT validated";
        }
    }

    private record Frame(double minY,double centerX,double centerZ,double scale) {
        static Frame of(Mesh mesh,Options opts) throws IOException {
            float[] bounds=mesh.bounds();
            double sourceHeight=bounds[4]-bounds[1];
            if(!Double.isFinite(sourceHeight)||sourceHeight<=1e-8)
                throw new IOException("Cannot size a zero-height asset for printing");
            double scale=opts.heightMm()/sourceHeight;
            return new Frame(bounds[1],(bounds[0]+bounds[3])/2.,
                    (bounds[2]+bounds[5])/2.,scale);
        }
        void point(float[] vertex,double[] out,int offset) throws IOException {
            // Proper +90 degree rotation around X: (x,y,z) -> (x,-z,y).
            out[offset]=(vertex[0]-centerX)*scale;
            out[offset+1]=-(vertex[2]-centerZ)*scale;
            out[offset+2]=(vertex[1]-minY)*scale;
            for(int n=0;n<3;n++)if(!Double.isFinite(out[offset+n])||Math.abs(out[offset+n])>9000)
                throw new IOException("3D printing output exceeds supported coordinate range");
        }
        long quantizedPoint(double[] xyz,int offset) throws IOException {
            long[] q=new long[3];
            for(int axis=0;axis<3;axis++){
                // Offset to positive 21-bit bins: 0.01 mm exact weld precision.
                q[axis]=Math.round((xyz[offset+axis]+10000.)/QUANTIZATION_MM);
                if(q[axis]<0||q[axis]>MAX_COORD_BIN)
                    throw new IOException("Print coordinate outside 21-bit welding bounds");
            }
            return (q[0]<<42)|(q[1]<<21)|q[2];
        }
    }

    private PrintExport(){}

    public static Audit inspect(Mesh mesh,Options options,VisualHull.Budget budget) throws Exception {
        mesh.validate();
        Frame frame=Frame.of(mesh,options);
        int verts=mesh.vertices.size(),triangles=verts/3;
        if(verts>MAX_EDGES||triangles==0)
            throw new IOException("Print mesh exceeds memory-safe triangle budget");
        List<Mesh.Part> parts=mesh.parts();
        double[] volumes=new double[parts.size()];
        long[] lo=new long[verts],hi=new long[verts];
        byte[] direction=new byte[verts];
        int[] edgeOwner=new int[verts];
        int[] parent=new int[triangles];
        byte[] rank=new byte[triangles];
        for(int t=0;t<triangles;t++)parent[t]=t;
        long degenerate=0;
        double shortest=Double.POSITIVE_INFINITY;
        double minX=Double.POSITIVE_INFINITY,maxX=Double.NEGATIVE_INFINITY,
                minY=Double.POSITIVE_INFINITY,maxY=Double.NEGATIVE_INFINITY,
                minZ=Double.POSITIVE_INFINITY,maxZ=Double.NEGATIVE_INFINITY;
        double[] points=new double[9];
        long[] ids=new long[3];
        int part=0;
        for(int tri=0;tri<triangles;tri++){
            if((tri&1023)==0)budget.check();
            int offset=tri*3;
            while(part+1<parts.size()&&offset>=parts.get(part+1).firstVertex())part++;
            for(int j=0;j<3;j++){
                frame.point(mesh.vertices.get(offset+j),points,j*3);
                ids[j]=frame.quantizedPoint(points,j*3);
                for(int axis=0;axis<3;axis++){
                    double n=points[j*3+axis];
                    if(axis==0){minX=Math.min(minX,n);maxX=Math.max(maxX,n);}
                    if(axis==1){minY=Math.min(minY,n);maxY=Math.max(maxY,n);}
                    if(axis==2){minZ=Math.min(minZ,n);maxZ=Math.max(maxZ,n);}
                }
            }
            double ux=points[3]-points[0],uy=points[4]-points[1],uz=points[5]-points[2];
            double vx=points[6]-points[0],vy=points[7]-points[1],vz=points[8]-points[2];
            double nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx;
            if(nx*nx+ny*ny+nz*nz<1e-10 ||
                    ids[0]==ids[1]||ids[1]==ids[2]||ids[2]==ids[0])degenerate++;
            volumes[part]+=(points[0]* (points[4]*points[8]-points[5]*points[7])
                +points[1]*(points[5]*points[6]-points[3]*points[8])
                +points[2]*(points[3]*points[7]-points[4]*points[6]))/6.;
            shortest=Math.min(shortest,Math.sqrt(dist2(points,0,3)));
            shortest=Math.min(shortest,Math.sqrt(dist2(points,3,6)));
            shortest=Math.min(shortest,Math.sqrt(dist2(points,6,0)));
            for(int j=0;j<3;j++){
                long a=ids[j],c=ids[(j+1)%3];
                int edge=offset+j;
                boolean ascending=Long.compareUnsigned(a,c)<0;
                lo[edge]=ascending?a:c;
                hi[edge]=ascending?c:a;
                direction[edge]=(byte)(ascending?1:-1);
                edgeOwner[edge]=tri;
            }
        }
        sortEdges(lo,hi,direction,edgeOwner);
        long boundary=0,nonManifold=0,winding=0;
        for(int i=0;i<verts;){
            if((i&0xffff)==0)budget.check();
            int next=i+1,sign=direction[i];
            while(next<verts&&lo[next]==lo[i]&&hi[next]==hi[i]){
                sign+=direction[next];next++;
            }
            int incidence=next-i;
            if(incidence==1)boundary++;
            else if(incidence>2)nonManifold++;
            if(incidence==2&&sign!=0)winding++;
            for(int k=i+1;k<next;k++)union(parent,rank,edgeOwner[i],edgeOwner[k]);
            i=next;
        }
        long inverted=0;
        double volume=0;
        for(double v:volumes){
            volume+=v;
            if(v<=MIN_VOLUME_MM3)inverted++;
        }
        int shells=0;
        for(int t=0;t<triangles;t++)if(find(parent,t)==t)shells++;
        boolean closed=boundary==0&&nonManifold==0&&winding==0&&
                degenerate==0&&inverted==0&&(shells==1||options.allowSeparateClosedShells());
        return new Audit(triangles,parts.size(),maxX-minX,maxY-minY,maxZ-minZ,
                boundary,nonManifold,winding,degenerate,inverted,shortest,volume,
                shells,options.allowSeparateClosedShells(),closed);
    }

    /**
     * Validate before touching the output stream. Binary STL is geometry only;
     * the existing GLB/NoWorld package remains the authoritative asset.
     */
    public static Audit writeStrict(Mesh mesh,Options options,OutputStream stream,
                                    VisualHull.Budget budget) throws Exception {
        Audit audit=inspect(mesh,options,budget);
        audit.requireGeometryGate();
        Frame frame=Frame.of(mesh,options);
        BufferedOutputStream output=new BufferedOutputStream(stream,65536);
        byte[] header=new byte[HEADER_BYTES];
        byte[] legend=("ASTERION FOUNDRY STL | MM | STATIC | "+VERSION)
                .getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(legend,0,header,0,Math.min(header.length,legend.length));
        output.write(header);
        output.write(littleInt(audit.triangles()));
        ByteBuffer triangle=ByteBuffer.allocate(50).order(ByteOrder.LITTLE_ENDIAN);
        double[] p=new double[9];
        for(int i=0;i<mesh.vertices.size();i+=3){
            if((i&3071)==0)budget.check();
            for(int n=0;n<3;n++)frame.point(mesh.vertices.get(i+n),p,n*3);
            double ux=p[3]-p[0],uy=p[4]-p[1],uz=p[5]-p[2];
            double vx=p[6]-p[0],vy=p[7]-p[1],vz=p[8]-p[2];
            double nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx;
            double length=Math.sqrt(nx*nx+ny*ny+nz*nz);
            if(length<1e-8)throw new IOException("Invalid normal after print conversion");
            triangle.clear();
            triangle.putFloat((float)(nx/length));
            triangle.putFloat((float)(ny/length));
            triangle.putFloat((float)(nz/length));
            for(double coord:p)triangle.putFloat((float)coord);
            triangle.putShort((short)0);
            output.write(triangle.array());
        }
        output.flush();
        return audit;
    }

    private static byte[] littleInt(int value) {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array();
    }
    private static double dist2(double[] p,int a,int b){
        double dx=p[a]-p[b],dy=p[a+1]-p[b+1],dz=p[a+2]-p[b+2];
        return dx*dx+dy*dy+dz*dz;
    }

    // Primitive in-place heapsort avoids per-edge objects and unbounded recursion.
    // A large 500k-triangle asset uses ~26 MB for 1.5m oriented edges.
    private static int find(int[] parent,int node){
        while(parent[node]!=node){parent[node]=parent[parent[node]];node=parent[node];}
        return node;
    }
    private static void union(int[] parent,byte[] rank,int a,int b){
        a=find(parent,a);b=find(parent,b);if(a==b)return;
        if(rank[a]<rank[b])parent[a]=b;
        else if(rank[a]>rank[b])parent[b]=a;
        else{parent[b]=a;rank[a]++;}
    }
    private static void sortEdges(long[] first,long[] second,byte[] sign,int[] owner) {
        int length=first.length;
        for(int start=length/2-1;start>=0;start--)sift(first,second,sign,owner,start,length);
        for(int end=length-1;end>0;end--){
            swap(first,second,sign,owner,0,end);
            sift(first,second,sign,owner,0,end);
        }
    }
    private static void sift(long[] a,long[] b,byte[] s,int[] owner,int root,int size){
        while(true){
            int child=root*2+1;
            if(child>=size)return;
            if(child+1<size&&compare(a,b,child,child+1)<0)child++;
            if(compare(a,b,root,child)>=0)return;
            swap(a,b,s,owner,root,child);root=child;
        }
    }
    private static int compare(long[] a,long[] b,int i,int j){
        int cmp=Long.compareUnsigned(a[i],a[j]);
        return cmp!=0?cmp:Long.compareUnsigned(b[i],b[j]);
    }
    private static void swap(long[] a,long[] b,byte[] s,int[] owner,int i,int j){
        long t=a[i];a[i]=a[j];a[j]=t;
        t=b[i];b[i]=b[j];b[j]=t;
        byte v=s[i];s[i]=s[j];s[j]=v;
        int v2=owner[i];owner[i]=owner[j];owner[j]=v2;
    }
}
