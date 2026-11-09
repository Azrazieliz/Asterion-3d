package com.asterion.foundry;

import java.util.HashMap;
import java.util.Map;

/** Bounded topology audit. Welding is analytical only and never mutates source geometry. */
public final class GeometryAudit {
    private GeometryAudit() {}

    public record Result(int boundaryEdges, int nonManifoldEdges, int windingConflicts,
                         int collapsedTriangles, double toleranceMetres) {}
    private record Point(long x, long y, long z) {}
    private record Edge(int a, int b) {}

    private static final class Incidence {
        int count;
        int direction;
    }

    public static Result inspect(Mesh mesh, VisualHull.Budget budget) throws Exception {
        if (mesh.vertices.size() > 180000) return null;
        Map<Point, Integer> welded = new HashMap<>();
        Map<Edge, Incidence> edges = new HashMap<>();
        float[] bounds = mesh.bounds();
        double span = Math.max(bounds[3] - bounds[0],
                Math.max(bounds[4] - bounds[1], bounds[5] - bounds[2]));
        double tolerance = Math.max(1e-8, span * 1e-6);
        int collapsed = 0;
        for (int i = 0; i < mesh.vertices.size(); i += 3) {
            if (i % 3072 == 0) budget.check();
            int a = weld(mesh.vertices.get(i), tolerance, welded);
            int b = weld(mesh.vertices.get(i + 1), tolerance, welded);
            int c = weld(mesh.vertices.get(i + 2), tolerance, welded);
            if (a == b || b == c || a == c) {
                collapsed++;
                continue;
            }
            add(edges, a, b);
            add(edges, b, c);
            add(edges, c, a);
        }
        int boundary = 0, nonManifold = 0, winding = 0;
        for (Incidence incidence : edges.values()) {
            if (incidence.count == 1) boundary++;
            if (incidence.count > 2) nonManifold++;
            if (incidence.count == 2 && incidence.direction != 0) winding++;
        }
        return new Result(boundary, nonManifold, winding, collapsed, tolerance);
    }

    private static int weld(float[] vertex, double tolerance, Map<Point, Integer> welded) {
        Point point = new Point(Math.round(vertex[0] / tolerance), Math.round(vertex[1] / tolerance),
                Math.round(vertex[2] / tolerance));
        Integer index = welded.get(point);
        if (index != null) return index;
        int next = welded.size();
        welded.put(point, next);
        return next;
    }

    private static void add(Map<Edge, Incidence> edges, int a, int b) {
        Edge edge = new Edge(Math.min(a, b), Math.max(a, b));
        Incidence incidence = edges.computeIfAbsent(edge, ignored -> new Incidence());
        incidence.count++;
        incidence.direction += a < b ? 1 : -1;
    }
}
