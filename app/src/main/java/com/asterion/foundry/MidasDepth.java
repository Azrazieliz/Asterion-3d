package com.asterion.foundry;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.os.SystemClock;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;

/** Bundled MiDaS small inverse-depth inference. Interpreter lifetime is one inference. */
final class MidasDepth {
    static final String MODEL = "midas_small_256_fp16.tflite";
    static final String SHA256 = "bec9bce704789e504ec306196fcb0aabe90fd25c2b9d7db382339741950890ca";
    static final int SIZE = 256;
    private static final float[] MEAN = {.485f, .456f, .406f};
    private static final float[] STD = {.229f, .224f, .225f};

    record Estimate(float[] inverseDepth, long elapsedMillis, int threads) {}

    static Estimate predict(Context context, SourceFiles.Raster image) throws IOException {
        long start = SystemClock.elapsedRealtime();
        ByteBuffer input = ByteBuffer.allocateDirect(SIZE * SIZE * 3 * Float.BYTES)
                .order(ByteOrder.nativeOrder());
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int sourceX = x * image.width() / SIZE;
                int sourceY = y * image.height() / SIZE;
                int pixel = image.pixels()[sourceY * image.width() + sourceX];
                float alpha = (pixel >>> 24) / 255f;
                for (int c = 0; c < 3; c++) {
                    float value = ((pixel >>> (16 - c * 8)) & 255) / 255f;
                    // Composite transparent input over neutral grey for the RGB model.
                    input.putFloat((value * alpha + .5f * (1 - alpha) - MEAN[c]) / STD[c]);
                }
            }
        }
        input.rewind();
        ByteBuffer output = ByteBuffer.allocateDirect(SIZE * SIZE * Float.BYTES)
                .order(ByteOrder.nativeOrder());
        int threads = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() / 2));
        try (AssetFileDescriptor asset = context.getAssets().openFd(MODEL);
             FileInputStream file = new FileInputStream(asset.getFileDescriptor());
             Interpreter interpreter = new Interpreter(file.getChannel().map(FileChannel.MapMode.READ_ONLY,
                     asset.getStartOffset(), asset.getDeclaredLength()),
                     new Interpreter.Options().setNumThreads(threads).setUseXNNPACK(true))) {
            if (interpreter.getInputTensor(0).dataType() != DataType.FLOAT32
                    || interpreter.getOutputTensor(0).dataType() != DataType.FLOAT32
                    || !Arrays.equals(interpreter.getInputTensor(0).shape(), new int[]{1, SIZE, SIZE, 3})
                    || !Arrays.equals(interpreter.getOutputTensor(0).shape(), new int[]{1, SIZE, SIZE})) {
                throw new IOException("Bundled model tensor contract mismatch");
            }
            interpreter.run(input, output);
        }
        float[] depth = new float[SIZE * SIZE];
        output.rewind();
        output.asFloatBuffer().get(depth);
        return new Estimate(depth, SystemClock.elapsedRealtime() - start, threads);
    }
}
