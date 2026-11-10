package com.asterion.foundry;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** MoveNet's two-dimensional joint predictions in original raster coordinates. */
final class PoseEvidence {
    static final int INPUT_SIZE = 192;
    static final float MIN_CONFIDENCE = .30f;

    // This order is part of MoveNet's output tensor contract.
    enum Joint {
        NOSE, LEFT_EYE, RIGHT_EYE, LEFT_EAR, RIGHT_EAR, LEFT_SHOULDER, RIGHT_SHOULDER,
        LEFT_ELBOW, RIGHT_ELBOW, LEFT_WRIST, RIGHT_WRIST, LEFT_HIP, RIGHT_HIP,
        LEFT_KNEE, RIGHT_KNEE, LEFT_ANKLE, RIGHT_ANKLE
    }

    record Point(Joint joint, float x, float y, float confidence) {
        boolean inFrame() {
            return x >= 0f && x < 1f && y >= 0f && y < 1f;
        }

        boolean usable() {
            return inFrame() && confidence >= MIN_CONFIDENCE;
        }
    }

    private final List<Point> points;

    private PoseEvidence(List<Point> points) {
        this.points = Collections.unmodifiableList(points);
    }

    List<Point> points() {
        return points;
    }

    Point point(Joint joint) {
        return points.get(joint.ordinal());
    }

    boolean hasTorsoSupport() {
        return (point(Joint.LEFT_SHOULDER).usable() || point(Joint.RIGHT_SHOULDER).usable())
                && (point(Joint.LEFT_HIP).usable() || point(Joint.RIGHT_HIP).usable());
    }

    static PoseEvidence decode(ImageFit fit, float[][] output) {
        Joint[] joints = Joint.values();
        if (output.length != joints.length) {
            throw new IllegalArgumentException("Invalid MoveNet landmark count");
        }
        List<Point> points = new ArrayList<>(joints.length);
        for (Joint joint : joints) {
            float[] values = output[joint.ordinal()];
            if (values.length != 3 || !Float.isFinite(values[0]) || !Float.isFinite(values[1])
                    || !Float.isFinite(values[2]) || values[2] < 0 || values[2] > 1) {
                throw new IllegalArgumentException("Invalid MoveNet landmark values");
            }
            float x = (values[1] * INPUT_SIZE - fit.left) / fit.width;
            float y = (values[0] * INPUT_SIZE - fit.top) / fit.height;
            if (!Float.isFinite(x) || !Float.isFinite(y)) {
                throw new IllegalArgumentException("Invalid MoveNet landmark coordinates");
            }
            points.add(new Point(joint, x, y, values[2]));
        }
        return new PoseEvidence(points);
    }

    /** Aspect-preserving bilinear resize and black padding, shared with the output transform. */
    record ImageFit(int sourceWidth, int sourceHeight, int width, int height, int left, int top) {
        static ImageFit of(int width, int height) {
            if (width < 1 || height < 1) throw new IllegalArgumentException("Empty pose image");
            double scale = (double) INPUT_SIZE / Math.max(width, height);
            int fittedWidth = Math.max(1, (int) Math.round(width * scale));
            int fittedHeight = Math.max(1, (int) Math.round(height * scale));
            return new ImageFit(width, height, fittedWidth, fittedHeight,
                    (INPUT_SIZE - fittedWidth) / 2, (INPUT_SIZE - fittedHeight) / 2);
        }

        ByteBuffer rgb(int[] pixels) {
            if (pixels.length != (long) sourceWidth * sourceHeight) {
                throw new IllegalArgumentException("Invalid pose image pixels");
            }
            ByteBuffer buffer = ByteBuffer.allocateDirect(INPUT_SIZE * INPUT_SIZE * 3)
                    .order(ByteOrder.nativeOrder());
            // The direct buffer starts zeroed. Leave every letterbox pixel black.
            for (int y = 0; y < height; y++) {
                double sy = sample(y, sourceHeight, height);
                int y0 = (int) sy;
                int y1 = Math.min(y0 + 1, sourceHeight - 1);
                float fy = (float) (sy - y0);
                int offset = ((y + top) * INPUT_SIZE + left) * 3;
                for (int x = 0; x < width; x++) {
                    double sx = sample(x, sourceWidth, width);
                    int x0 = (int) sx;
                    int x1 = Math.min(x0 + 1, sourceWidth - 1);
                    float fx = (float) (sx - x0);
                    int p00 = pixels[y0 * sourceWidth + x0];
                    int p10 = pixels[y0 * sourceWidth + x1];
                    int p01 = pixels[y1 * sourceWidth + x0];
                    int p11 = pixels[y1 * sourceWidth + x1];
                    for (int shift = 16; shift >= 0; shift -= 8) {
                        float upper = channel(p00, shift) * (1 - fx) + channel(p10, shift) * fx;
                        float lower = channel(p01, shift) * (1 - fx) + channel(p11, shift) * fx;
                        buffer.put(offset++, (byte) Math.round(upper * (1 - fy) + lower * fy));
                    }
                }
            }
            return buffer;
        }

        private static double sample(int position, int source, int fitted) {
            return Math.max(0, Math.min(source - 1, (position + .5) * source / fitted - .5));
        }

        private static float channel(int argb, int shift) {
            return ((argb >>> shift) & 255) * (argb >>> 24) / 255f;
        }
    }
}
