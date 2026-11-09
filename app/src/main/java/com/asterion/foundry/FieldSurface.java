package com.asterion.foundry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/** Extracts a closed triangle surface from a sampled scalar field. */
final class FieldSurface {
    // A consistent six-tetrahedron partition across adjacent cubes.
    private static final int[][] TETRAHEDRA = {
            {0,1,3,7}, {0,3,2,7}, {0,2,6,7}, {0,6,4,7}, {0,4,5,7}, {0,5,1,7}
    };
    private static final int MAX_TRIANGLES = Mesh.LIMIT / 3;
    final ArrayList<float[]> positions = new ArrayList<>();
    final ArrayList<int[]> triangles = new ArrayList<>();
    private final Map<Long, Integer> edges = new HashMap<>();
    private final float[] values;
    private final int size;
    private final float radius;
    private final float threshold;

    private FieldSurface(float[] values, int size, float radius, float threshold) {
        this.values = values;
        this.size = size;
        this.radius = radius;
        this.threshold = threshold;
    }

    static FieldSurface extract(float[] field, int size, float radius, float threshold,
                                VisualHull.Budget budget) throws Exception {
        if (size < 8 || size > 160 || field.length != size * size * size
                || !Float.isFinite(radius) || radius <= 0 || !Float.isFinite(threshold)) {
            throw new IllegalArgumentException("Invalid density grid");
        }
        FieldSurface surface = new FieldSurface(field, size, radius, threshold);
        int[] corners = new int[8];
        int[] inside = new int[4], outside = new int[4];
        for (int x = 0; x < size; x++) {
            budget.check();
            for (int y = 0; y < size; y++) for (int z = 0; z < size; z++) {
                float density = field[surface.index(x,y,z)];
                if (!Float.isFinite(density)) throw new IllegalArgumentException("Invalid field sample");
                if (density >= threshold && (x == 0 || y == 0 || z == 0
                        || x == size-1 || y == size-1 || z == size-1)) {
                    throw new IllegalArgumentException("Predicted geometry crosses the reconstruction bounds");
                }
            }
        }
        for (int x = 0; x < size - 1; x++) {
            budget.check();
            for (int y = 0; y < size - 1; y++) for (int z = 0; z < size - 1; z++) {
                int count = 0;
                for (int c = 0; c < 8; c++) {
                    corners[c] = surface.index(x + (c&1), y + ((c>>1)&1), z + (c>>2));
                    if (field[corners[c]] >= threshold) count++;
                }
                if (count == 0 || count == 8) continue;
                for (int[] tetrahedron : TETRAHEDRA) {
                    int inner = 0, outer = 0;
                    for (int c : tetrahedron) {
                        int vertex = corners[c];
                        if (field[vertex] >= threshold) inside[inner++] = vertex;
                        else outside[outer++] = vertex;
                    }
                    surface.polygon(inside, outside, inner, outer);
                }
            }
        }
        if (surface.triangles.isEmpty()) throw new IllegalArgumentException("No usable surface was predicted");
        surface.edges.clear();
        return surface;
    }

    private int index(int x, int y, int z) { return (x * size + y) * size + z; }

    float coordinate(int coordinate) { return -radius + 2f * radius * coordinate / (size - 1); }

    private int axis(int node, int dimension) {
        return dimension == 0 ? node / (size*size) : dimension == 1 ? node / size % size : node % size;
    }

    private int intersection(int a, int b) {
        int lo = Math.min(a,b), hi = Math.max(a,b);
        // An exactly on-level node is shared by every incident edge.
        if (values[lo] == threshold) hi = lo;
        else if (values[hi] == threshold) lo = hi;
        long key = (long) lo << 32 | Integer.toUnsignedLong(hi);
        Integer existing = edges.get(key);
        if (existing != null) return existing;
        double fraction = lo == hi ? 0 : ((double) threshold - values[lo]) / ((double) values[hi] - values[lo]);
        float[] point = new float[3];
        for (int k = 0; k < 3; k++) {
            point[k] = (float) (coordinate(axis(lo,k)) * (1-fraction) + coordinate(axis(hi,k)) * fraction);
        }
        int id = positions.size();
        positions.add(point);
        edges.put(key,id);
        return id;
    }

    private void polygon(int[] inside, int[] outside, int inner, int outer) {
        if (inner == 0 || outer == 0) return;
        int origin = inside[0], direction = outside[0];
        if (inner == 1) {
            emit(intersection(origin,outside[0]), intersection(origin,outside[1]),
                    intersection(origin,outside[2]), origin, direction);
        } else if (outer == 1) {
            emit(intersection(inside[0],direction), intersection(inside[1],direction),
                    intersection(inside[2],direction), origin, direction);
        } else {
            int a = intersection(inside[0],outside[0]), b = intersection(inside[0],outside[1]);
            int c = intersection(inside[1],outside[0]), d = intersection(inside[1],outside[1]);
            emit(a,b,c,origin,direction);
            emit(b,d,c,origin,direction);
        }
    }

    private void emit(int a, int b, int c, int origin, int direction) {
        if (a == b || b == c || a == c) return;
        float[] p = positions.get(a), q = positions.get(b), r = positions.get(c);
        float[] normal = normal(p,q,r);
        double facing = 0;
        for (int k = 0; k < 3; k++) facing += normal[k] * (axis(direction,k) - axis(origin,k));
        if (facing == 0) return;
        if (triangles.size() >= MAX_TRIANGLES) throw new IllegalArgumentException("Predicted mesh exceeds budget");
        triangles.add(facing > 0 ? new int[]{a,b,c} : new int[]{a,c,b});
    }

    Mesh mesh(float[] srgb, float metres, VisualHull.Budget budget) throws Exception {
        if (srgb.length != positions.size() * 3 || !Float.isFinite(metres) || metres <= 0) {
            throw new IllegalArgumentException("Invalid surface colour or size");
        }
        // Model is Z-up, camera at +X; Foundry is Y-up with front at +Z.
        float[][] points = new float[positions.size()][3];
        float[][] normals = new float[positions.size()][3];
        float[] low = {Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY};
        float[] high = {Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY};
        for (int i = 0; i < points.length; i++) {
            float[] p = positions.get(i);
            points[i] = new float[]{p[1],p[2],p[0]};
            for (int axis = 0; axis < 3; axis++) {
                low[axis] = Math.min(low[axis],points[i][axis]);
                high[axis] = Math.max(high[axis],points[i][axis]);
            }
        }
        float span = Math.max(high[0]-low[0], Math.max(high[1]-low[1], high[2]-low[2]));
        if (span <= 0) throw new IllegalArgumentException("Degenerate predicted bounds");
        for (float[] point : points) {
            point[0] = (point[0] - (high[0]+low[0])/2) * metres/span;
            point[1] = (point[1] - low[1]) * metres/span;
            point[2] = (point[2] - (high[2]+low[2])/2) * metres/span;
        }
        int progress = 0;
        for (int[] face : triangles) {
            if ((progress++ & 4095) == 0) budget.check();
            float[] normal = normal(points[face[0]],points[face[1]],points[face[2]]);
            for (int i : face) for (int axis = 0; axis < 3; axis++) normals[i][axis] += normal[axis];
        }
        Mesh mesh = new Mesh();
        progress = 0;
        for (int[] face : triangles) {
            if ((progress++ & 4095) == 0) budget.check();
            float[] faceNormal = normal(points[face[0]], points[face[1]], points[face[2]]);
            double faceLength = length(faceNormal);
            if (faceLength == 0) continue;
            for (int id : face) {
                float[] point = points[id];
                float[] n = normals[id];
                double length = length(n);
                if (length == 0) {
                    n = faceNormal;
                    length = faceLength;
                }
                float[] vertex = new float[]{point[0], point[1], point[2],
                        (float) (n[0]/length), (float) (n[1]/length), (float) (n[2]/length),
                        point[0], point[1], 0, 0, 0, 1, 0};
                for (int axis = 0; axis < 3; axis++) {
                    float color = srgb[3*id+axis];
                    if (!Float.isFinite(color)) throw new IllegalArgumentException("Invalid decoded surface colour");
                    color = Math.min(1,Math.max(0,color));
                    vertex[8+axis] = color <= .04045f ? color/12.92f
                            : (float) Math.pow((color+.055f)/1.055f,2.4);
                }
                mesh.vertices.add(vertex);
            }
        }
        mesh.validate();
        return mesh;
    }

    private static double length(float[] vector) {
        return Math.sqrt((double) vector[0]*vector[0] + (double) vector[1]*vector[1]
                + (double) vector[2]*vector[2]);
    }

    private static float[] normal(float[] a,float[] b,float[] c) {
        float x=b[0]-a[0], y=b[1]-a[1], z=b[2]-a[2];
        float u=c[0]-a[0], v=c[1]-a[1], w=c[2]-a[2];
        return new float[]{y*w-z*v,z*u-x*w,x*v-y*u};
    }
}
