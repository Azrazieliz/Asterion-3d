package com.asterion.foundry;

import java.util.ArrayDeque;
import java.util.Arrays;

/** Bounded orthographic space carving from isolated, upright reference views. */
public final class VisualHull {
    public static final String METHOD = "orthographic-visual-hull-with-texture-v2";
    private static final int TILE = 512, ATLAS = 2*TILE;

    @FunctionalInterface
    public interface Budget {
        void check() throws Exception;
    }

    public static final class View {
        private final int[] pixels;
        private final int width;
        private final int left;
        private final int top;
        private final int subjectWidth;
        private final int subjectHeight;
        private final boolean calibrated;

        public View(int[] pixels, int width, int height) {
            this(pixels,width,height,false);
        }

        static View calibrated(int[] pixels,int width,int height) {
            return new View(pixels,width,height,true);
        }

        private View(int[] pixels,int width,int height,boolean calibrated) {
            if (width < 4 || height < 4 || width > 512 || height > 512
                    || pixels.length != width * height) {
                throw new IllegalArgumentException("Reference raster dimensions are outside the supported budget");
            }
            this.pixels = Arrays.copyOf(pixels, pixels.length);
            this.width = width;
            this.calibrated = calibrated;
            boolean transparency = false;
            for (int pixel : pixels) {
                if ((pixel >>> 24) < 32) {
                    transparency = true;
                    break;
                }
            }

            // For opaque input require all four corners to agree on background.
            int background = pixels[0];
            if (calibrated && !transparency) {
                throw new IllegalArgumentException("Calibrated part images need a transparent background");
            }
            if (!transparency) {
                for (int corner : new int[]{width - 1, width * (height - 1), pixels.length - 1}) {
                    if (distance(background, pixels[corner]) > 32) {
                        throw new IllegalArgumentException("Use transparent or uniform-background references");
                    }
                }
            }
            int minX = width, minY = height, maxX = -1, maxY = -1, count = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int index = x + y * width;
                    int pixel = pixels[index];
                    boolean foreground = (pixel >>> 24) >= 128
                            && (transparency || distance(pixel, background) > 64);
                    if (!foreground) {
                        this.pixels[index] = 0;
                        continue;
                    }
                    this.pixels[index] = pixel | 0xff000000;
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                    count++;
                }
            }
            if (count < 16 || maxX - minX < 2 || maxY - minY < 2 || count == pixels.length) {
                throw new IllegalArgumentException("No isolated foreground found in a reference");
            }
            left = calibrated ? 0 : minX;
            top = calibrated ? 0 : minY;
            subjectWidth = calibrated ? width : maxX - minX + 1;
            subjectHeight = calibrated ? height : maxY - minY + 1;
        }

        int width() { return width; }
        int height() { return pixels.length/width; }
        boolean calibrated() { return calibrated; }

        double aspect() {
            return (double) subjectWidth / subjectHeight;
        }

        int sample(int u, int y, int columns, int rows) {
            return pixels[pixelX(u, columns) + pixelY(y, rows) * width];
        }

        int pixelX(int u, int columns) {
            return left + (int) ((u + 0.5) * subjectWidth / columns);
        }

        int pixelY(int y, int rows) {
            return top + (int) ((rows - y - 0.5) * subjectHeight / rows);
        }

        private static int distance(int a, int b) {
            int red = ((a >>> 16) & 255) - ((b >>> 16) & 255);
            int green = ((a >>> 8) & 255) - ((b >>> 8) & 255);
            int blue = (a & 255) - (b & 255);
            return Math.max(Math.abs(red), Math.max(Math.abs(green), Math.abs(blue)));
        }
    }

    public record Result(Mesh mesh, int occupied, int components, double frontIou,
                         double rightIou, Double backIou, int resolution, int[] atlas,
                         int texturedTriangles) {}

    public static Result reconstruct(View front, View right, View back, float heightMetres,
                                     int resolution, Budget budget) throws Exception {
        if (!Float.isFinite(heightMetres) || heightMetres < .01f || heightMetres > 100f) {
            throw new IllegalArgumentException("Enter a subject height of 0.01–100 metres");
        }
        if (resolution < 16 || resolution > 64) {
            throw new IllegalArgumentException("Use a reconstruction resolution from 16 through 64");
        }
        if (back != null && Math.abs(back.aspect() / front.aspect() - 1) > .20) {
            throw new IllegalArgumentException("Front and back proportions disagree. Use aligned orthographic views.");
        }

        int sy = resolution;
        int sx = width(front, resolution);
        int sz = width(right, resolution);
        boolean[] volume = new boolean[sx * sy * sz];
        boolean[] frontProjection = new boolean[sx * sy];
        boolean[] rightProjection = new boolean[sz * sy];
        int[] frontDepth = new int[sx*sy], rightDepth = new int[sz*sy], backDepth = new int[sx*sy];
        Arrays.fill(frontDepth,-1);
        Arrays.fill(rightDepth,-1);
        Arrays.fill(backDepth,sz);
        int occupied = 0;
        for (int y = 0; y < sy; y++) {
            budget.check();
            for (int x = 0; x < sx; x++) {
                if (front.sample(x, y, sx, sy) == 0
                        || (back != null && back.sample(sx - 1 - x, y, sx, sy) == 0)) continue;
                for (int z = 0; z < sz; z++) {
                    if (right.sample(sz - 1 - z, y, sz, sy) == 0) continue;
                    volume[index(x, y, z, sx, sz)] = true;
                    frontProjection[y * sx + x] = true;
                    rightProjection[y * sz + (sz - 1 - z)] = true;
                    frontDepth[y*sx+x] = Math.max(frontDepth[y*sx+x],z);
                    backDepth[y*sx+x] = Math.min(backDepth[y*sx+x],z);
                    rightDepth[y*sz+z] = Math.max(rightDepth[y*sz+z],x);
                    occupied++;
                }
            }
        }
        if (occupied == 0) throw new IllegalArgumentException("The references do not overlap into a volume");

        Mesh mesh = new Mesh();
        int texturedTriangles = 0;
        float dx = heightMetres * (float) front.aspect() / sx;
        float dz = heightMetres * (float) right.aspect() / sz;
        float dy = heightMetres / sy;
        for (int y = 0; y < sy; y++) {
            budget.check();
            for (int z = 0; z < sz; z++) for (int x = 0; x < sx; x++) {
                if (!volume[index(x, y, z, sx, sz)]) continue;
                int f = front.sample(x, y, sx, sy);
                int r = right.sample(sz - 1 - z, y, sz, sy);
                int b = back == null ? f : back.sample(sx - 1 - x, y, sx, sy);
                boolean[] faces = {
                    z == 0 || !volume[index(x, y, z - 1, sx, sz)],
                    z + 1 == sz || !volume[index(x, y, z + 1, sx, sz)],
                    x == 0 || !volume[index(x - 1, y, z, sx, sz)],
                    x + 1 == sx || !volume[index(x + 1, y, z, sx, sz)],
                    y + 1 == sy || !volume[index(x, y + 1, z, sx, sz)],
                    y == 0 || !volume[index(x, y - 1, z, sx, sz)]
                };
                int[] tiles = {
                    back != null && z == backDepth[y*sx+x] ? 2 : 3,
                    z == frontDepth[y*sx+x] ? 0 : 3,
                    3, x == rightDepth[y*sz+z] ? 1 : 3, 3, 3
                };
                texturedTriangles += emit(mesh, (x - sx / 2f) * dx, (x + 1 - sx / 2f) * dx, y * dy,
                        (y + 1) * dy, (z - sz / 2f) * dz, (z + 1 - sz / 2f) * dz,
                        faces, new int[]{b, f, f, r, f, f}, tiles, sx*dx, sy*dy, sz*dz);
            }
        }

        boolean[] backProjection = new boolean[frontProjection.length];
        for (int y = 0; y < sy; y++) for (int x = 0; x < sx; x++) {
            backProjection[y * sx + x] = frontProjection[y * sx + sx - 1 - x];
        }
        mesh.validate();
        return new Result(mesh, occupied, components(volume, sx, sy, sz, budget),
                overlap(front, frontProjection, sx, sy), overlap(right, rightProjection, sz, sy),
                back == null ? null : overlap(back, backProjection, sx, sy), resolution,
                atlas(front,right,back,budget), texturedTriangles);
    }

    private static int width(View view, int resolution) {
        if (view.aspect() > 8 || view.aspect() < 1.0 / 32) {
            throw new IllegalArgumentException("Reference aspect ratio is outside the supported range");
        }
        return Math.max(2, Math.min(96, (int) Math.round(view.aspect() * resolution)));
    }

    private static int index(int x, int y, int z, int sx, int sz) {
        return x + sx * (z + sz * y);
    }

    private static double overlap(View view, boolean[] projection, int width, int height) {
        int union = 0;
        int intersection = 0;
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            boolean original = view.sample(x, y, width, height) != 0;
            boolean output = projection[y * width + x];
            if (original || output) union++;
            if (original && output) intersection++;
        }
        return union == 0 ? 0 : (double) intersection / union;
    }

    private static int components(boolean[] volume, int sx, int sy, int sz, Budget budget)
            throws Exception {
        boolean[] visited = new boolean[volume.length];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        int plane = sx * sz;
        int count = 0;
        int work = 0;
        for (int i = 0; i < volume.length; i++) {
            if (!volume[i] || visited[i]) continue;
            count++;
            visited[i] = true;
            queue.add(i);
            while (!queue.isEmpty()) {
                if ((++work & 4095) == 0) budget.check();
                int cell = queue.removeFirst();
                int x = cell % sx;
                int z = cell / sx % sz;
                int y = cell / plane;
                if (x > 0) visit(cell - 1, volume, visited, queue);
                if (x + 1 < sx) visit(cell + 1, volume, visited, queue);
                if (z > 0) visit(cell - sx, volume, visited, queue);
                if (z + 1 < sz) visit(cell + sx, volume, visited, queue);
                if (y > 0) visit(cell - plane, volume, visited, queue);
                if (y + 1 < sy) visit(cell + plane, volume, visited, queue);
            }
        }
        return count;
    }

    private static void visit(int i, boolean[] volume, boolean[] visited, ArrayDeque<Integer> queue) {
        if (volume[i] && !visited[i]) {
            visited[i] = true;
            queue.add(i);
        }
    }

    private static final int[][] FACE = {
        {0, 3, 2, 1}, {4, 5, 6, 7}, {0, 4, 7, 3},
        {1, 2, 6, 5}, {3, 7, 6, 2}, {0, 1, 5, 4}
    };

    private static int[] atlas(View front, View right, View back, Budget budget) throws Exception {
        int[] result = new int[ATLAS*ATLAS];
        Arrays.fill(result,0xffffffff);
        View[] views = {front,right,back};
        for (int tile = 0; tile < views.length; tile++) {
            View view = views[tile];
            if (view == null) continue;
            int ox=(tile%2)*TILE, oy=(tile/2)*TILE;
            int inner = TILE-4;
            for (int y = 0; y < TILE; y++) {
                if ((y&63)==0) budget.check();
                for (int x = 0; x < TILE; x++) {
                    int u=Math.max(0,Math.min(inner-1,x-2));
                    int v=Math.max(0,Math.min(inner-1,y-2));
                    int pixel=view.sample(u,inner-1-v,inner,inner);
                    result[(oy+y)*ATLAS+ox+x] = pixel == 0 ? 0xff808080 : pixel;
                }
            }
        }
        return result;
    }

    private static int emit(Mesh mesh, float x0, float x1, float y0, float y1,
                            float z0, float z1, boolean[] faces, int[] colors, int[] tiles,
                            float width, float height, float depth) {
        if (!faces[0] && !faces[1] && !faces[2] && !faces[3] && !faces[4] && !faces[5]) return 0;
        float[][] points = {{x0,y0,z0}, {x1,y0,z0}, {x1,y1,z0}, {x0,y1,z0},
                            {x0,y0,z1}, {x1,y0,z1}, {x1,y1,z1}, {x0,y1,z1}};
        int textured = 0;
        for (int i = 0; i < FACE.length; i++) {
            if (!faces[i]) continue;
            int[] face = FACE[i];
            int pixel = colors[i];
            int tile = tiles[i];
            float[] color = tile < 3 ? new float[]{1,1,1}
                    : new float[]{linear(pixel >>> 16), linear(pixel >>> 8), linear(pixel)};
            int start = mesh.vertices.size();
            mesh.quad(points[face[0]], points[face[1]], points[face[2]], points[face[3]], color, 0);
            if (tile < 3) textured += (mesh.vertices.size()-start)/3;
            for (int n = start; n < mesh.vertices.size(); n++) {
                float[] v = mesh.vertices.get(n);
                float u = switch(tile) {
                    case 0 -> .5f+v[0]/width;
                    case 1 -> .5f-v[2]/depth;
                    case 2 -> .5f-v[0]/width;
                    default -> .5f;
                };
                float top = tile < 3 ? 1-v[1]/height : .5f;
                v[6] = ((tile%2)*TILE + 2.5f + (TILE-5)*Math.max(0,Math.min(1,u)))/ATLAS;
                v[7] = ((tile/2)*TILE + 2.5f + (TILE-5)*Math.max(0,Math.min(1,top)))/ATLAS;
            }
        }
        return textured;
    }

    static float linear(int channel) {
        float value = (channel & 255) / 255f;
        return value <= .04045f ? value / 12.92f
                : (float) Math.pow((value + .055f) / 1.055f, 2.4);
    }
}
