package com.asterion.foundry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Bounded SAT-based UV triangle overlap evidence; a warning, never automatic
 * visual acceptance. Shared mirrored UVs and re-used atlases may be intentional.
 * No quadratic unbounded check is permitted on the phone.
 */
final class UvOverlapAudit {
    static final int MAX_TRIANGLES = 2048;
    static final int MAX_PAIRS = 200000;
    private static final int GRID = 24;
    private static final double EPS = 1e-8;

    record Result(int totalTriangles, int sampledTriangles, int skippedTriangles,
                  int pairsCompared, int overlappingPairs, int crossPartPairs,
                  boolean budgetExceeded) {
        String status() {
            if (budgetExceeded) return "incomplete_pair_budget";
            if (overlappingPairs > 0) return "potential_overlap_review_required";
            if (sampledTriangles < totalTriangles || skippedTriangles > 0)
                return "none_detected_in_bounded_sample";
            return "no_positive_area_overlaps_detected";
        }
    }

    private record Triangle(double[] u,double[] v,double minU,double maxU,
                            double minV,double maxV,int part) {}

    static Result inspect(Mesh mesh) {
        mesh.validate();
        int total = mesh.vertices.size() / 3;
        int count = Math.min(MAX_TRIANGLES,total);
        List<Mesh.Part> parts = mesh.parts();
        List<Triangle> triangles = new ArrayList<>(count);
        int skipped = 0, partIndex = 0;
        for (int i = 0; i < count; i++) {
            int first = 3 * (int)((long)i * total / count);
            while (partIndex + 1 < parts.size() && parts.get(partIndex + 1).firstVertex() <= first)
                partIndex++;
            double[] u = new double[3], v = new double[3];
            double minU = Double.POSITIVE_INFINITY, maxU = Double.NEGATIVE_INFINITY;
            double minV = Double.POSITIVE_INFINITY, maxV = Double.NEGATIVE_INFINITY;
            boolean valid = true;
            for (int k = 0; k < 3; k++) {
                float[] vertex = mesh.vertices.get(first + k);
                u[k] = vertex[6]; v[k] = vertex[7];
                valid &= Double.isFinite(u[k]) && Double.isFinite(v[k])
                        && u[k] >= 0 && u[k] <= 1 && v[k] >= 0 && v[k] <= 1;
                minU = Math.min(minU,u[k]); maxU = Math.max(maxU,u[k]);
                minV = Math.min(minV,v[k]); maxV = Math.max(maxV,v[k]);
            }
            double area = (u[1]-u[0])*(v[2]-v[0])-(v[1]-v[0])*(u[2]-u[0]);
            if (!valid || Math.abs(area) < EPS) { skipped++; continue; }
            triangles.add(new Triangle(u,v,minU,maxU,minV,maxV,partIndex));
        }
        List<List<Integer>> grid = new ArrayList<>(GRID*GRID);
        for (int i=0; i<GRID*GRID; i++) grid.add(new ArrayList<>());
        Set<Long> seen = new HashSet<>();
        int pairs = 0, overlaps = 0, acrossParts = 0;
        boolean exhausted = false;
        search:
        for (int current=0; current<triangles.size(); current++) {
            Triangle candidate = triangles.get(current);
            int x0=cell(candidate.minU),x1=cell(candidate.maxU);
            int y0=cell(candidate.minV),y1=cell(candidate.maxV);
            for(int y=y0; y<=y1; y++) for(int x=x0; x<=x1; x++){
                List<Integer> bucket=grid.get(y*GRID+x);
                for(int previous:bucket) {
                    long key=((long)previous << 32) | current;
                    if (!seen.add(key)) continue;
                    if (pairs >= MAX_PAIRS) {exhausted=true;break search;}
                    pairs++;
                    Triangle other=triangles.get(previous);
                    if (intersectPositiveArea(other,candidate)) {
                        overlaps++;
                        if(other.part!=candidate.part)acrossParts++;
                    }
                }
                bucket.add(current);
            }
        }
        return new Result(total,count,skipped,pairs,overlaps,acrossParts,exhausted);
    }

    private static int cell(double value) {
        return Math.max(0,Math.min(GRID-1,(int)(value*GRID)));
    }

    private static boolean intersectPositiveArea(Triangle a,Triangle b) {
        if(a.maxU <= b.minU+EPS || b.maxU <= a.minU+EPS
                || a.maxV <= b.minV+EPS || b.maxV <= a.minV+EPS) return false;
        return !separated(a,b) && !separated(b,a);
    }

    private static boolean separated(Triangle a,Triangle b) {
        for(int i=0;i<3;i++) {
            int next=(i+1)%3;
            double dx=a.u[next]-a.u[i],dy=a.v[next]-a.v[i];
            double axisX=-dy,axisY=dx;
            double aMin=Double.POSITIVE_INFINITY,aMax=Double.NEGATIVE_INFINITY;
            double bMin=Double.POSITIVE_INFINITY,bMax=Double.NEGATIVE_INFINITY;
            for(int k=0;k<3;k++){
                double pa=a.u[k]*axisX+a.v[k]*axisY;
                double pb=b.u[k]*axisX+b.v[k]*axisY;
                aMin=Math.min(aMin,pa);aMax=Math.max(aMax,pa);
                bMin=Math.min(bMin,pb);bMax=Math.max(bMax,pb);
            }
            if(aMax<=bMin+EPS || bMax<=aMin+EPS)return true;
        }
        return false;
    }
}
