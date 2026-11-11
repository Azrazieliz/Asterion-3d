package com.asterion.foundry;

/** Exact UV overlap, touch-only boundary, cross-part classification and budget. */
public final class UvOverlapAcceptance {
    private static void triangle(Mesh mesh,double[][] uv){
        int start=mesh.vertices.size();
        mesh.tri(new float[]{0,0,0},new float[]{1,0,0},new float[]{0,1,0},
                new float[]{0.5f,0.6f,0.7f},0);
        for(int i=0;i<3;i++){
            mesh.vertices.get(start+i)[6]=(float)uv[i][0];
            mesh.vertices.get(start+i)[7]=(float)uv[i][1];
        }
    }
    public static void main(String[] unused) {
        Mesh mesh=new Mesh();
        mesh.beginPart("body","Body","body");
        triangle(mesh,new double[][]{{0,0},{0.5,0},{0,0.5}});
        triangle(mesh,new double[][]{{0.5,0},{0.5,0.5},{0,0.5}});
        UvOverlapAudit.Result touching=UvOverlapAudit.inspect(mesh);
        if(touching.overlappingPairs()!=0 || touching.skippedTriangles()!=0)
            throw new AssertionError("Touching UV edges must not count as area overlap");
        mesh.beginPart("hair","Hair","hair");
        triangle(mesh,new double[][]{{0.05,0.05},{0.2,0.05},{0.05,0.2}});
        UvOverlapAudit.Result crossing=UvOverlapAudit.inspect(mesh);
        if(crossing.overlappingPairs()!=1 || crossing.crossPartPairs()!=1
                || !crossing.status().equals("potential_overlap_review_required"))
            throw new AssertionError("Cross-part UV overlap was not reported");
        triangle(mesh,new double[][]{{1.05,0},{1.15,0},{1.05,0.2}});
        UvOverlapAudit.Result outside=UvOverlapAudit.inspect(mesh);
        if(outside.skippedTriangles()!=1 || outside.overlappingPairs()!=1)
            throw new AssertionError("Invalid UVs must not silence the valid overlap warning");
        Mesh repeated=new Mesh();
        for(int i=0;i<600;i++)triangle(repeated,new double[][]{{0,0},{1,0},{0,1}});
        UvOverlapAudit.Result capped=UvOverlapAudit.inspect(repeated);
        if(capped.pairsCompared()>UvOverlapAudit.MAX_PAIRS || capped.overlappingPairs()<=0)
            throw new AssertionError("Overlap search exceeded resource limits or lost evidence");
        System.out.println("PASS bounded UV overlap, edge adjacency, cross-part evidence, invalid UV exclusion");
    }
}
