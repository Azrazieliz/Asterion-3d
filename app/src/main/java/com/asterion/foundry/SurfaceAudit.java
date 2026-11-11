package com.asterion.foundry;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Geometry / UV / sampled colour evidence for editable assets.
 * Does not claim a similarity or material-quality score without a reference.
 * Android PNG decoding is done by the caller so this core runs in host tests.
 */
final class SurfaceAudit {
    private SurfaceAudit() {}
    static final int MAX_SAMPLE = 4096;

    static JSONObject inspect(Mesh mesh, int[] pixels, int width, int height) throws Exception {
        if (mesh == null) throw new IOException("No mesh to inspect");
        mesh.validate();
        if ((pixels == null) != (width == 0 && height == 0))
            throw new IOException("Incomplete texture input");
        if (pixels != null && (width < 1 || height < 1 || (long)width * height != pixels.length))
            throw new IOException("Invalid texture dimensions");
        long outsideUV = 0, collapsedUV = 0, invalidUV = 0;
        long outsideColors = 0;
        Set<Integer> quantizedVertexColors = new HashSet<>();
        for (float[] v : mesh.vertices) {
            float u = v[6], t = v[7];
            if (!Float.isFinite(u) || !Float.isFinite(t)) invalidUV++;
            else if (u < 0 || u > 1 || t < 0 || t > 1) outsideUV++;
            for (int k = 8; k <= 11; k++) if (v[k] < 0 || v[k] > 1) outsideColors++;
            int r = bin(v[8]), g = bin(v[9]), b = bin(v[10]);
            quantizedVertexColors.add((r << 10) | (g << 5) | b);
        }
        for (int i = 0; i < mesh.vertices.size(); i += 3) {
            float[] a = mesh.vertices.get(i), b = mesh.vertices.get(i + 1), c = mesh.vertices.get(i + 2);
            double area2 = (b[6] - a[6]) * (c[7] - a[7])
                    - (b[7] - a[7]) * (c[6] - a[6]);
            if (Math.abs(area2) < 1e-10) collapsedUV++;
        }
        JSONObject audit = new JSONObject()
                .put("schema", "asterion.foundry.surface-audit.v1")
                .put("vertex_palette_5bit_bins", quantizedVertexColors.size())
                .put("uv_outside_01_vertices", outsideUV)
                .put("uv_nonfinite_vertices", invalidUV)
                .put("uv_degenerate_triangles", collapsedUV)
                .put("vertex_rgba_outside_01_components", outsideColors)
                .put("uv_overlap", "not_checked")
                .put("texture_seams", "not_checked")
                .put("reference_texture_fidelity", "unverified")
                .put("source_palette_fidelity", "unverified")
                .put("visual_material_response", "unverified");

        if (pixels == null) return audit.put("base_color_texture", "none")
                .put("texture_sample_count", 0)
                .put("material_maps", new JSONArray().put("vertex_color"));
        int count = Math.min(pixels.length, MAX_SAMPLE), fullyOpaque = 0, transparent = 0;
        double[] means = new double[3], variances = new double[3];
        Set<Integer> unique = new HashSet<>();
        for (int i = 0; i < count; i++) {
            int index = count == 1 ? 0 : (int)((long)i * (pixels.length - 1) / (count - 1));
            int color = pixels[index];
            int alpha = color >>> 24;
            if (alpha == 255) fullyOpaque++;
            if (alpha == 0) transparent++;
            int[] channels = {(color >>> 16) & 255, (color >>> 8) & 255, color & 255};
            for (int k = 0; k < 3; k++) {
                double v = channels[k] / 255.0;
                means[k] += v;
                variances[k] += v * v;
            }
            unique.add(((channels[0] >>> 3) << 10)
                    | ((channels[1] >>> 3) << 5) | (channels[2] >>> 3));
        }
        JSONArray rgbMean = new JSONArray(), rgbStddev = new JSONArray();
        for (int k = 0; k < 3; k++) {
            double mean = means[k] / count;
            rgbMean.put(mean);
            rgbStddev.put(Math.sqrt(Math.max(0, variances[k] / count - mean * mean)));
        }
        audit.put("base_color_texture", "sampled_png")
                .put("texture_size", new JSONArray().put(width).put(height))
                .put("texture_sample_count", count)
                .put("sampled_opaque_fraction", fullyOpaque / (double)count)
                .put("sampled_transparent_fraction", transparent / (double)count)
                .put("sampled_palette_5bit_bins", unique.size())
                .put("sampled_rgb_mean_srgb", rgbMean)
                .put("sampled_rgb_stddev_srgb", rgbStddev)
                .put("material_maps", new JSONArray().put("base_color").put("vertex_color"));
        return audit;
    }

    private static int bin(float v) {
        if (!Float.isFinite(v)) return 0;
        return Math.max(0, Math.min(31, (int)(v * 31 + 0.5f)));
    }
}
