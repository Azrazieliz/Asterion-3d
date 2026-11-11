package com.asterion.foundry;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;

/** Real closed and rejected geometry cases plus large-mesh streaming acceptance. */
public final class PrintExportAcceptance {
    public static void main(String[] unused) throws Exception {
        File dir=Files.createTempDirectory("foundry-print-check").toFile();
        try{
            closedBox(dir);
            rejectsOpen();
            rejectsInverted();
            rejectsNonManifold();
            rejectsInvalidSettings();
            acceptsMeshLargerThanOldTopologyAudit();
            System.out.println("PASS strict STL: mm/Z-up, normals, topology orientation, non-manifold/open/inward rejections, parent immutability and >180k vertex mesh");
        }finally{
            for(File f:dir.listFiles())Files.deleteIfExists(f.toPath());
            Files.deleteIfExists(dir.toPath());
        }
    }
    private static void closedBox(File dir)throws Exception {
        Mesh mesh=new Mesh();
        mesh.box(0,0,0,1,1.5f,.5f,new float[]{.4f,.8f,.3f},0);
        File original=new File(dir,"original.mesh");
        mesh.save(original);
        String unchanged=FilesUtil.hash(original);
        PrintExport.Options settings=new PrintExport.Options(180);
        PrintExport.Audit audit=PrintExport.inspect(mesh,settings,()->{});
        if(!audit.technicallyClosed()||audit.boundaryEdges()!=0||audit.nonManifoldEdges()!=0
                ||audit.windingConflicts()!=0||audit.inwardOrZeroVolumeParts()!=0
                ||Math.abs(audit.heightMm()-180)>0.02
                ||Math.abs(audit.widthMm()-120)>0.02
                ||Math.abs(audit.depthMm()-60)>0.02
                ||audit.triangles()!=12)
            throw new AssertionError("Closed box preflight, scaling or geometry quality failed: "+audit);
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        PrintExport.Audit exported=PrintExport.writeStrict(mesh,settings,output,()->{});
        if(!audit.equals(exported))throw new AssertionError("Preflight changed during export");
        byte[] stl=output.toByteArray();
        if(stl.length!=84+12*50)throw new AssertionError("Incorrect binary STL length");
        int triangles=ByteBuffer.wrap(stl).order(ByteOrder.LITTLE_ENDIAN).getInt(80);
        if(triangles!=12)throw new AssertionError("Wrong binary STL triangle count");
        double minZ=Double.POSITIVE_INFINITY,maxZ=Double.NEGATIVE_INFINITY;
        double minY=Double.POSITIVE_INFINITY,maxY=Double.NEGATIVE_INFINITY;
        for(int i=0;i<triangles;i++){
            ByteBuffer part=ByteBuffer.wrap(stl,84+i*50,50).order(ByteOrder.LITTLE_ENDIAN);
            float nx=part.getFloat(),ny=part.getFloat(),nz=part.getFloat();
            if(Math.abs(nx*nx+ny*ny+nz*nz-1)>1e-4)throw new AssertionError("STL normals not normalized");
            for(int vertex=0;vertex<3;vertex++){
                float x=part.getFloat(),y=part.getFloat(),z=part.getFloat();
                if(!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(z))
                    throw new AssertionError("STL exported nonfinite coordinates");
                minZ=Math.min(minZ,z);maxZ=Math.max(maxZ,z);
                minY=Math.min(minY,y);maxY=Math.max(maxY,y);
            }
            if(part.getShort()!=0)throw new AssertionError("Nonzero STL attribute");
        }
        if(Math.abs(minZ)>1e-4||Math.abs(maxZ-180)>1e-4
                ||Math.abs(maxY-minY-60)>1e-4)
            throw new AssertionError("STL mm scale or Z-up conversion incorrect");
        if(!unchanged.equals(FilesUtil.hash(original)))throw new AssertionError("Print path mutated original asset");
    }
    private static void rejectsOpen()throws Exception{
        Mesh plane=new Mesh();
        float[] colour={1,1,1};
        plane.tri(new float[]{0,0,0},new float[]{1,0,0},new float[]{0,1,0},colour,0);
        PrintExport.Audit audit=PrintExport.inspect(plane,new PrintExport.Options(100),()->{});
        if(audit.boundaryEdges()!=3||audit.technicallyClosed())
            throw new AssertionError("Open mesh not diagnosed");
        ByteArrayOutputStream sink=new ByteArrayOutputStream();
        try{
            PrintExport.writeStrict(plane,new PrintExport.Options(100),sink,()->{});
            throw new AssertionError("Open surface was exported as print-ready");
        }catch(IOException expected){}
        if(sink.size()!=0)throw new AssertionError("Strict exporter wrote data before validating");
    }
    private static void rejectsInverted()throws Exception{
        Mesh box=new Mesh();
        box.box(0,0,0,1,1,1,new float[]{1,1,1},0);
        for(int i=0;i<box.vertices.size();i+=3){
            float[] t=box.vertices.get(i+1);
            box.vertices.set(i+1,box.vertices.get(i+2));
            box.vertices.set(i+2,t);
        }
        PrintExport.Audit result=PrintExport.inspect(box,new PrintExport.Options(100),()->{});
        if(result.inwardOrZeroVolumeParts()!=1||result.technicallyClosed())
            throw new AssertionError("Inverted closed shell incorrectly approved");
    }
    private static void rejectsNonManifold()throws Exception{
        Mesh box=new Mesh();
        box.box(0,0,0,1,1,1,new float[]{1,1,1},0);
        for(int i=0;i<3;i++)box.vertices.add(box.vertices.get(i).clone());
        PrintExport.Audit result=PrintExport.inspect(box,new PrintExport.Options(100),()->{});
        if(result.nonManifoldEdges()<3||result.technicallyClosed())
            throw new AssertionError("Triply shared edges incorrectly accepted");
    }
    private static void rejectsInvalidSettings() {
        for(double value:new double[]{0,4,601,Double.NaN,Double.POSITIVE_INFINITY}){
            try{new PrintExport.Options(value);throw new AssertionError("Invalid height accepted");}
            catch(IllegalArgumentException expected){}
        }
    }
    private static void acceptsMeshLargerThanOldTopologyAudit()throws Exception {
        Mesh mesh=new Mesh();
        float[] colour={.6f,.6f,.6f};
        for(int i=0;i<5250;i++){
            float x=(i%75)*.04f,z=(i/75)*.04f;
            float height=i==0?1f:.12f;
            mesh.box(x,0,z,.02f,height,.02f,colour,0);
        }
        if(mesh.vertices.size()<=180000)throw new AssertionError("Stress case below old limit");
        PrintExport.Audit quality=PrintExport.inspect(mesh,new PrintExport.Options(140),()->{});
        if(!quality.technicallyClosed()||quality.triangles()!=63000)
            throw new AssertionError("Large object print audit failed: "+quality);
    }
}
