package com.asterion.foundry;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Compares and conservatively recolours a candidate in the TripoSR reference camera. */
final class ReferenceView {
    static final int SIDE = 256;
    static final int FRAME_SIDE = SIDE * 2;
    private static final double DISTANCE = 1.9;
    private static final double FOCAL = SIDE / (2 * Math.tan(Math.toRadians(20)));
    private static final int MAX_RASTER_TESTS = 16_000_000;

    record Result(Double silhouetteIou, double precision, double recall, Double baseColorMaeBefore,
                  Double baseColorMaeAfter, int recoloredVertices, boolean sourceTouchesImageBorder,
                  String colorTransfer) {
        Map<String, Object> evidence() {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("camera", "triposr_nominal_front_40deg");
            values.put("raster_side", SIDE);
            values.put("color_transfer", colorTransfer);
            values.put("recolored_vertices", recoloredVertices);
            values.put("source_touches_image_border", sourceTouchesImageBorder);
            if (silhouetteIou != null) {
                values.put("silhouette_iou", silhouetteIou);
                values.put("silhouette_precision", precision);
                values.put("silhouette_recall", recall);
            }
            if (baseColorMaeBefore != null) {
                values.put("base_color_srgb_mae_before", baseColorMaeBefore);
                values.put("base_color_srgb_mae_after", baseColorMaeAfter);
            }
            return values;
        }
    }

    record Evaluation(Result result, float[] colors) {}

    private record Point(double x, double y, double inverseDepth) {}

    private static final class Raster {
        final int[] face = new int[SIDE * SIDE];
        final float[] inverseDepth = new float[SIDE * SIDE];
        final float[] weightA = new float[SIDE * SIDE];
        final float[] weightB = new float[SIDE * SIDE];
        final boolean[] frontFacing;

        Raster(int faceCount) {
            Arrays.fill(face, -1);
            frontFacing = new boolean[faceCount];
        }
    }

    static Evaluation evaluate(List<float[]> vertices, List<int[]> triangles, float[] colors, int[] source,
                               boolean cropped, VisualHull.Budget budget) throws Exception {
        if (source.length != FRAME_SIDE * FRAME_SIDE || colors.length != 3 * vertices.size()) {
            throw new IllegalArgumentException("Invalid reference-view input");
        }
        Point[] points = new Point[vertices.size()];
        for (int i = 0; i < points.length; i++) {
            float[] point = vertices.get(i);
            // Upstream TripoSR uses Z up, with the front camera on +X and +Y to its right.
            double inverseDepth = 1 / (DISTANCE - point[0]);
            points[i] = new Point(SIDE / 2d + FOCAL * point[1] * inverseDepth,
                    SIDE / 2d - FOCAL * point[2] * inverseDepth, inverseDepth);
        }
        Raster raster = rasterize(points, triangles, budget);
        if (raster == null) {
            return new Evaluation(new Result(null, 0, 0, null, null, 0, cropped, "raster_budget"), colors);
        }
        int[] reference = new int[SIDE * SIDE];
        int sourceCount = 0, meshCount = 0, common = 0;
        for (int y = 0; y < SIDE; y++) for (int x = 0; x < SIDE; x++) {
            int i = y * SIDE + x;
            reference[i] = source[(2 * y + 1) * FRAME_SIDE + 2 * x + 1];
            boolean foreground = (reference[i] >>> 24) >= 128;
            boolean covered = raster.face[i] >= 0;
            if (foreground) sourceCount++;
            if (covered) meshCount++;
            if (foreground && covered) common++;
        }
        double iou = ratio(common, sourceCount + meshCount - common);
        double precision = ratio(common, meshCount), recall = ratio(common, sourceCount);
        Double before = common == 0 ? null : error(raster, triangles, colors, reference, common, budget);
        if (iou < .75 || precision < .88 || recall < .80) {
            return new Evaluation(new Result(iou, precision, recall, before, before, 0, cropped, "silhouette_gate"), colors);
        }

        float[] recolored = colors.clone();
        int changed = 0;
        for (int i = 0; i < points.length; i++) {
            if ((i & 2047) == 0) budget.check();
            Point point = points[i];
            int x = (int) Math.floor(point.x), y = (int) Math.floor(point.y);
            if (x < 1 || y < 1 || x >= SIDE - 1 || y >= SIDE - 1
                    || !visible(i, point, raster, triangles, x, y)) continue;
            int u = (int) (point.x * 2), v = (int) (point.y * 2);
            int index = v * FRAME_SIDE + u;
            int sample = source[index];
            // Stay inside the source mask, away from antialiasing and background fringes.
            if ((sample >>> 24) < 250 || (source[index - 1] >>> 24) < 250 || (source[index + 1] >>> 24) < 250
                    || (source[index - FRAME_SIDE] >>> 24) < 250 || (source[index + FRAME_SIDE] >>> 24) < 250) continue;
            boolean differs = false;
            for (int c = 0; c < 3; c++) {
                float old = colors[3 * i + c];
                float channel = ((sample >>> (16 - 8 * c)) & 255) / 255f;
                recolored[3 * i + c] = .25f * old + .75f * channel;
                differs |= old != recolored[3 * i + c];
            }
            if (differs) changed++;
        }
        if (changed == 0) {
            return new Evaluation(new Result(iou, precision, recall, before, before, 0, cropped, "no_visible_changes"), colors);
        }
        double after = error(raster, triangles, recolored, reference, common, budget);
        if (after >= before) {
            return new Evaluation(new Result(iou, precision, recall, before, before, 0, cropped, "no_measured_improvement"), colors);
        }
        return new Evaluation(new Result(iou, precision, recall, before, after, changed, cropped, "applied"), recolored);
    }

    private static boolean visible(int vertex, Point point, Raster raster, List<int[]> triangles, int x, int y) {
        int centre = y * SIDE + x;
        if (raster.inverseDepth[centre] > point.inverseDepth + .003) return false;
        for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
            int face = raster.face[centre + dy * SIDE + dx];
            if (face < 0 || !raster.frontFacing[face]) continue;
            int[] ids = triangles.get(face);
            if (ids[0] == vertex || ids[1] == vertex || ids[2] == vertex) return true;
        }
        return false;
    }

    private static Raster rasterize(Point[] points, List<int[]> triangles, VisualHull.Budget budget) throws Exception {
        Raster result = new Raster(triangles.size());
        long work = 0;
        for (int i = 0; i < triangles.size(); i++) {
            if ((i & 255) == 0) budget.check();
            int[] ids = triangles.get(i);
            Point a = points[ids[0]], b = points[ids[1]], c = points[ids[2]];
            double area = edge(a, b, c.x, c.y);
            if (Math.abs(area) < 1e-9) continue;
            result.frontFacing[i] = area < 0;
            int left = Math.max(0, (int) Math.floor(Math.min(a.x, Math.min(b.x, c.x))));
            int right = Math.min(SIDE - 1, (int) Math.floor(Math.max(a.x, Math.max(b.x, c.x))));
            int top = Math.max(0, (int) Math.floor(Math.min(a.y, Math.min(b.y, c.y))));
            int bottom = Math.min(SIDE - 1, (int) Math.floor(Math.max(a.y, Math.max(b.y, c.y))));
            if (right < left || bottom < top) continue;
            work += (long) (right - left + 1) * (bottom - top + 1);
            if (work > MAX_RASTER_TESTS) return null;
            for (int y = top; y <= bottom; y++) for (int x = left; x <= right; x++) {
                double wA = edge(b, c, x + .5, y + .5) / area;
                double wB = edge(c, a, x + .5, y + .5) / area;
                double wC = 1 - wA - wB;
                if (wA < -1e-9 || wB < -1e-9 || wC < -1e-9) continue;
                double invDepth = wA * a.inverseDepth + wB * b.inverseDepth + wC * c.inverseDepth;
                int index = y * SIDE + x;
                if (invDepth <= result.inverseDepth[index]) continue;
                result.inverseDepth[index] = (float) invDepth;
                result.face[index] = i;
                result.weightA[index] = (float) (wA * a.inverseDepth / invDepth);
                result.weightB[index] = (float) (wB * b.inverseDepth / invDepth);
            }
        }
        return result;
    }

    private static double edge(Point a, Point b, double x, double y) {
        return (b.x - a.x) * (y - a.y) - (b.y - a.y) * (x - a.x);
    }

    private static double error(Raster raster, List<int[]> triangles, float[] colors,
                                int[] reference, int common, VisualHull.Budget budget) throws Exception {
        float[] linear = new float[colors.length];
        for (int i = 0; i < colors.length; i++) {
            float c = Math.min(1, Math.max(0, colors[i]));
            linear[i] = c <= .04045 ? c / 12.92f : (float) Math.pow((c + .055) / 1.055, 2.4);
        }
        double sum = 0;
        for (int i = 0; i < reference.length; i++) {
            if ((i & 4095) == 0) budget.check();
            if (raster.face[i] < 0 || (reference[i] >>> 24) < 128) continue;
            int[] ids = triangles.get(raster.face[i]);
            double a = raster.weightA[i], b = raster.weightB[i];
            for (int c = 0; c < 3; c++) {
                double mixed = a * linear[3 * ids[0] + c] + b * linear[3 * ids[1] + c]
                        + (1 - a - b) * linear[3 * ids[2] + c];
                double srgb = mixed <= .0031308 ? 12.92 * mixed : 1.055 * Math.pow(mixed, 1 / 2.4) - .055;
                sum += Math.abs(srgb - ((reference[i] >>> (16 - 8 * c)) & 255) / 255d);
            }
        }
        return sum / (3d * common);
    }

    private static double ratio(int numerator, int denominator) {
        return denominator == 0 ? 0 : (double) numerator / denominator;
    }
}
