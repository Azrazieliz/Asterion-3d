package com.asterion.foundry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Carves each named part against a shared orthographic frame rather than centering individual masks. */
final class PartHull {
    static final String METHOD = "calibrated-part-hull-v2";
    static final int MAX_PARTS = 32;
    private static final int CELL = 512;
    private static final double MIN_OVERLAP = .65;

    record Part(String id,String name,String role) {
        Part {
            if(!Mesh.validPart(id,name,role))throw new IllegalArgumentException("Invalid part identity, name or role");
        }
    }
    record Views(VisualHull.View front,VisualHull.View right,VisualHull.View back) {}
    @FunctionalInterface interface ViewSource { Views read(Part part) throws Exception; }
    record Evidence(Part part,double frontIou,double rightIou,Double backIou,int occupiedCells,
                    int connectedComponents,GeometryAudit.Result topology) {}
    record Result(Mesh mesh,int[] atlas,int width,int height,List<Evidence> parts) {}

    static Result reconstruct(List<Part> parts,ViewSource views,float canvasHeightMetres,int resolution,
                              VisualHull.Budget budget) throws Exception {
        if(parts.isEmpty()||parts.size()>MAX_PARTS)throw new IllegalArgumentException("Use one to "+MAX_PARTS+" parts");
        Set<String> ids=new HashSet<>(),names=new HashSet<>();
        for(Part part:parts){
            if(!ids.add(part.id)||!names.add(part.name))throw new IllegalArgumentException("Duplicate reference part identity or name");
        }
        int columns=parts.size()>4?4:parts.size()>1?2:1;
        int rows=(parts.size()+columns-1)/columns;
        int width=columns*CELL,height=rows*CELL;
        if((long)width*height>8_388_608L)
            throw new IllegalArgumentException("Combined part texture atlas exceeds the mobile memory budget");
        int[] atlas=new int[width*height];
        Arrays.fill(atlas,0xffffffff);
        Mesh output=new Mesh();
        List<Evidence> evidence=new ArrayList<>();
        int frontWidth=0,rightWidth=0,frameHeight=0;
        for(int i=0;i<parts.size();i++){
            budget.check();
            Part part=parts.get(i);
            Views pair=views.read(part);
            requireAligned(pair);
            if(i==0){
                frontWidth=pair.front.width();rightWidth=pair.right.width();frameHeight=pair.front.height();
            }else if(pair.front.width()!=frontWidth||pair.right.width()!=rightWidth||pair.front.height()!=frameHeight){
                throw new IllegalArgumentException("Every part must use the same full front and right canvases");
            }
            VisualHull.Result hull=VisualHull.reconstruct(pair.front,pair.right,pair.back,canvasHeightMetres,resolution,budget);
            if(hull.frontIou()<MIN_OVERLAP||hull.rightIou()<MIN_OVERLAP||hull.backIou()!=null&&hull.backIou()<MIN_OVERLAP){
                throw new IllegalArgumentException("Orthographic masks disagree for "+part.name);
            }
            if(output.vertices.size()+hull.mesh().vertices.size()>Mesh.LIMIT){
                throw new IllegalArgumentException("Part reconstruction exceeds the shared mesh budget");
            }
            output.beginPart(part.id,part.name,part.role);
            int x=i%columns,y=i/columns;
            copyAtlas(hull.atlas(),atlas,width,x*CELL,y*CELL,budget);
            int written=0;
            for(float[] vertex:hull.mesh().vertices){
                if((written++&4095)==0)budget.check();
                vertex[6]=(x+vertex[6])/columns;
                vertex[7]=(y+vertex[7])/rows;
                output.vertices.add(vertex);
            }
            evidence.add(new Evidence(part,hull.frontIou(),hull.rightIou(),hull.backIou(),hull.occupied(),hull.components(),
                    GeometryAudit.inspect(hull.mesh(),budget)));
        }
        output.validate();
        return new Result(output,atlas,width,height,List.copyOf(evidence));
    }

    private static void requireAligned(Views views){
        VisualHull.View front=views.front,right=views.right,back=views.back;
        if(front==null||right==null||!front.calibrated()||!right.calibrated()||front.height()!=right.height()
                ||back!=null&&(!back.calibrated()||back.width()!=front.width()||back.height()!=front.height())){
            throw new IllegalArgumentException("Part views must share aligned transparent canvases with the same height");
        }
    }

    private static void copyAtlas(int[] part,int[] atlas,int width,int ox,int oy,VisualHull.Budget budget) throws Exception {
        int side=(int)Math.sqrt(part.length);
        if(side<CELL||side*side!=part.length)throw new IllegalStateException("Invalid part texture atlas");
        for(int y=0;y<CELL;y++){
            if((y&63)==0)budget.check();
            int sourceY=(2*y+1)*side/(2*CELL);
            for(int x=0;x<CELL;x++)atlas[(oy+y)*width+ox+x]=part[sourceY*side+(2*x+1)*side/(2*CELL)];
        }
    }
}
