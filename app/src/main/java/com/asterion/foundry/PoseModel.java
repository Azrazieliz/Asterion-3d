package com.asterion.foundry;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.os.SystemClock;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;

/** Bundled Google MoveNet SinglePose Lightning inference through the existing LiteRT runtime. */
final class PoseModel {
    static final String MODEL = "movenet_lightning_v4_fp16.tflite";
    static final String SHA256 = "0fac2226112d0371903ca86e3853cec24ef603a0b2f96f589b180f0ebdd135ab";
    static final String IDENTITY = "Google MoveNet SinglePose Lightning float16 v4";

    record Estimate(PoseEvidence evidence, long elapsedMillis, int threads) {}

    static Estimate predict(Context context, SourceFiles.Raster image) throws IOException {
        long start = SystemClock.elapsedRealtime();
        PoseEvidence.ImageFit fit = PoseEvidence.ImageFit.of(image.width(), image.height());
        ByteBuffer input = fit.rgb(image.pixels());
        int threads = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() / 2));
        int size = PoseEvidence.INPUT_SIZE;
        int jointCount = PoseEvidence.Joint.values().length;
        float[][][][] output = new float[1][1][jointCount][3];
        try (AssetFileDescriptor asset = context.getAssets().openFd(MODEL);
             FileInputStream file = new FileInputStream(asset.getFileDescriptor());
             Interpreter interpreter = new Interpreter(file.getChannel().map(FileChannel.MapMode.READ_ONLY,
                     asset.getStartOffset(), asset.getDeclaredLength()),
                     new Interpreter.Options().setNumThreads(threads).setUseXNNPACK(true))) {
            if (interpreter.getInputTensor(0).dataType() != DataType.UINT8
                    || interpreter.getOutputTensor(0).dataType() != DataType.FLOAT32
                    || !Arrays.equals(interpreter.getInputTensor(0).shape(), new int[]{1, size, size, 3})
                    || !Arrays.equals(interpreter.getOutputTensor(0).shape(), new int[]{1, 1, jointCount, 3})) {
                throw new IOException("Bundled MoveNet tensor contract mismatch");
            }
            interpreter.run(input, output);
            return new Estimate(PoseEvidence.decode(fit, output[0][0]),
                    SystemClock.elapsedRealtime() - start, threads);
        } catch (IllegalArgumentException | IllegalStateException error) {
            throw new IOException("Bundled MoveNet inference failed", error);
        }
    }
}
