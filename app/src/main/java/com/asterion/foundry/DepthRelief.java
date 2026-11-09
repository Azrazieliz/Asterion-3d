package com.asterion.foundry;

import java.util.Arrays;

/** Projects relative inverse depth onto a closed front relief with an explicit flat backing. */
public final class DepthRelief {
    private DepthRelief() {}

    public static Mesh build(int[] pixels, int width, int height, float[] inverseDepth,
                             int depthSide, float metres, VisualHull.Budget budget) throws Exception {
        if (depthSide < 2 || inverseDepth.length != depthSide * depthSide) {
            throw new IllegalArgumentException("Invalid inverse-depth map");
        }
        if (!Float.isFinite(metres) || metres < .01f || metres > 100f) {
            throw new IllegalArgumentException("Enter a height from 0.01 through 100 metres");
        }
        VisualHull.View view = new VisualHull.View(pixels, width, height);
        int rows = 64;
        int columns = Math.max(2, Math.min(96, (int) Math.round(rows * view.aspect())));
        float[] samples = new float[rows * columns];
        float[] sorted = new float[samples.length];
        int[] colors = new int[samples.length];
        int count = 0;
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < columns; x++) {
                int cell = y * columns + x;
                int color = view.sample(x, y, columns, rows);
                if (color == 0) continue;
                int px = view.pixelX(x, columns) * depthSide / width;
                int py = view.pixelY(y, rows) * depthSide / height;
                float depth = inverseDepth[py * depthSide + px];
                if (!Float.isFinite(depth)) throw new IllegalArgumentException("Model returned non-finite depth");
                colors[cell] = color;
                samples[cell] = depth;
                sorted[count++] = depth;
            }
        }
        if (count < 16) throw new IllegalArgumentException("Reference mask is too small at mesh resolution");
        Arrays.sort(sorted, 0, count);
        float low = sorted[count / 20];
        float high = sorted[count - 1 - count / 20];
        if (high - low < 1e-5f) throw new IllegalArgumentException("Model returned insufficient depth variation");
        for (int i = 0; i < samples.length; i++) {
            samples[i] = metres * (.025f + .20f * Math.max(0, Math.min(1, (samples[i] - low) / (high - low))));
        }
        float dx = metres * (float) view.aspect() / columns;
        float dy = metres / rows;
        Mesh mesh = new Mesh();
        for (int y = 0; y < rows; y++) {
            budget.check();
            for (int x = 0; x < columns; x++) {
                int pixel = colors[y * columns + x];
                if (pixel == 0) continue;
                float left = (x - columns / 2f) * dx;
                float right = (x + 1 - columns / 2f) * dx;
                float bottom = y * dy;
                float top = (y + 1) * dy;
                float[] a = {left,bottom,0}, b = {right,bottom,0};
                float[] c = {right,top,0}, d = {left,top,0};
                float[] e = {left,bottom,corner(x,y,columns,rows,colors,samples)};
                float[] f = {right,bottom,corner(x+1,y,columns,rows,colors,samples)};
                float[] g = {right,top,corner(x+1,y+1,columns,rows,colors,samples)};
                float[] h = {left,top,corner(x,y+1,columns,rows,colors,samples)};
                float[] color = {VisualHull.linear(pixel >>> 16), VisualHull.linear(pixel >>> 8),
                                 VisualHull.linear(pixel)};
                mesh.quad(a,d,c,b,color,0);
                mesh.quad(e,f,g,h,color,0);
                if (!filled(x-1,y,columns,rows,colors)) mesh.quad(a,e,h,d,color,0);
                if (!filled(x+1,y,columns,rows,colors)) mesh.quad(b,c,g,f,color,0);
                if (!filled(x,y+1,columns,rows,colors)) mesh.quad(d,h,g,c,color,0);
                if (!filled(x,y-1,columns,rows,colors)) mesh.quad(a,b,f,e,color,0);
            }
        }
        mesh.validate();
        return mesh;
    }

    private static boolean filled(int x, int y, int columns, int rows, int[] colors) {
        return x >= 0 && y >= 0 && x < columns && y < rows && colors[y * columns + x] != 0;
    }

    private static float corner(int x, int y, int columns, int rows, int[] colors, float[] depth) {
        float sum = 0;
        int count = 0;
        for (int cy = y - 1; cy <= y; cy++) for (int cx = x - 1; cx <= x; cx++) {
            if (!filled(cx, cy, columns, rows, colors)) continue;
            sum += depth[cy * columns + cx];
            count++;
        }
        return sum / count;
    }
}
