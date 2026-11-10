package com.asterion.foundry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Exercises the Android projection against a known source raster and an occluded surface. */
public final class ReferenceViewAcceptance {
    public static void main(String[] args) throws Exception {
        var vertices = new ArrayList<float[]>();
        var faces = new ArrayList<int[]>();
        int frontCount = plane(vertices, faces, .20f);
        plane(vertices, faces, -.30f);
        float[] original = new float[3 * vertices.size()];
        Arrays.fill(original, .35f);

        ReferenceView.Evaluation aligned = ReferenceView.evaluate(vertices, faces, original,
                source(50, 206, 50, 206), false, () -> {});
        ReferenceView.Result result = aligned.result();
        if (!"applied".equals(result.colorTransfer()) || result.silhouetteIou() < .99
                || result.baseColorMaeAfter() >= .6 * result.baseColorMaeBefore()
                || result.recoloredVertices() <= 0 || result.recoloredVertices() > frontCount
                || result.sourceTouchesImageBorder()) {
            throw new AssertionError("Aligned view failed: " + result.evidence());
        }
        if (original[3 * (frontCount / 2)] != .35f) throw new AssertionError("Original colors mutated");
        for (int i = frontCount * 3; i < original.length; i++) {
            if (aligned.colors()[i] != original[i]) throw new AssertionError("Occluded surface was recolored");
        }
        ReferenceView.Evaluation shifted = ReferenceView.evaluate(vertices, faces, original,
                source(0, 156, 0, 156), true, () -> {});
        if (!"silhouette_gate".equals(shifted.result().colorTransfer())
                || shifted.result().silhouetteIou() > .5 || !shifted.result().sourceTouchesImageBorder()
                || shifted.colors() != original) {
            throw new AssertionError("Misaligned view changed the original: " + shifted.result().evidence());
        }
        ReferenceView.Evaluation disjoint = ReferenceView.evaluate(vertices, faces, original,
                source(0, 10, 0, 10), true, () -> {});
        if (disjoint.result().baseColorMaeBefore() != null || disjoint.result().silhouetteIou() != 0
                || disjoint.colors() != original) throw new AssertionError("Disjoint reference was scored as overlapping");

        System.out.println("Aligned and occluded: " + result.evidence());
        System.out.println("Misaligned: " + shifted.result().evidence());
        System.out.println("Disjoint: " + disjoint.result().evidence());
    }

    private static int plane(List<float[]> vertices, List<int[]> faces, float depth) {
        int offset = vertices.size();
        int count = 17;
        double perPixel = (1.9 - depth) * Math.tan(Math.toRadians(20)) / 128;
        for (int y = 0; y < count; y++) for (int x = 0; x < count; x++) {
            vertices.add(new float[]{depth, (float) ((x - 8) * 9.75 * perPixel),
                    (float) ((y - 8) * 9.75 * perPixel)});
        }
        for (int y = 0; y < count - 1; y++) for (int x = 0; x < count - 1; x++) {
            int i = offset + y * count + x;
            faces.add(new int[]{i, i + 1, i + count + 1});
            faces.add(new int[]{i, i + count + 1, i + count});
        }
        return count * count;
    }

    private static int[] source(int left, int right, int top, int bottom) {
        int side = ReferenceView.FRAME_SIDE;
        int[] pixels = new int[side * side];
        for (int y = 2 * top; y < 2 * bottom; y++) for (int x = 2 * left; x < 2 * right; x++) {
            pixels[y * side + x] = x < left + right ? 0xffec3893 : 0xff20c5ee;
        }
        return pixels;
    }
}
