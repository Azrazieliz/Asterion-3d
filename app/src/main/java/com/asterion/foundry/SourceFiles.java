package com.asterion.foundry;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.*;
import java.util.LinkedHashMap;
import java.util.Map;
import org.json.JSONObject;

/** Immutable content-addressed original files, separated from generated projects. */
final class SourceFiles {
    private final File root;

    SourceFiles(File storage) {
        root = new File(storage, "sources");
    }

    File preserve(InputStream input) throws Exception {
        if (input == null) throw new IOException("The document provider returned no input");
        File temporary = File.createTempFile("import-", ".incoming", root);
        try {
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[65536];
                long size = 0;
                for (int n; (n = input.read(buffer)) != -1;) {
                    size += n;
                    if (size > 64L * 1024 * 1024) throw new IOException("An original may be at most 64 MiB");
                    output.write(buffer, 0, n);
                }
                if (size == 0) throw new IOException("The selected source is empty");
                output.getFD().sync();
            }
            String hash = FilesUtil.hash(temporary);
            File target = new File(root, hash);
            synchronized (SourceFiles.class) {
                if (target.isFile()) {
                    if (!FilesUtil.hash(target).equals(hash)) {
                        throw new IOException("Existing original failed its checksum");
                    }
                } else {
                    FilesUtil.move(temporary, target);
                }
            }
            return target;
        } finally {
            temporary.delete();
        }
    }

    Map<String, File> originals(JSONObject request) throws Exception {
        Map<String, File> result = new LinkedHashMap<>();
        for (String role : new String[]{"source", "reference", "front", "right", "back", "contract"}) {
            String path = request.optString(role);
            if (path.isEmpty()) continue;
            File file = new File(path).getCanonicalFile();
            if (!root.getCanonicalFile().equals(file.getParentFile())
                    || !file.getName().matches("[a-f0-9]{64}") || !file.isFile()
                    || !FilesUtil.hash(file).equals(file.getName())) {
                throw new IOException("Original missing or checksum mismatch for " + role);
            }
            result.put(role, file);
        }
        return result;
    }

    record Raster(int[] pixels, int width, int height) {}

    static Raster decode(File file, int limit) throws IOException {
        if (file == null) throw new IOException("A required image is missing");
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        if (options.outWidth < 1 || options.outHeight < 1) throw new IOException("Unreadable image");
        int longest = Math.max(options.outWidth, options.outHeight);
        int sample = 1;
        while (longest / sample > limit * 2) sample *= 2;
        options.inSampleSize = sample;
        options.inJustDecodeBounds = false;
        Bitmap input = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        if (input == null) throw new IOException("Image decoding failed");
        Bitmap resized = input;
        try {
            float ratio = Math.min(1f, (float) limit / Math.max(input.getWidth(), input.getHeight()));
            int width = Math.max(1, Math.round(input.getWidth() * ratio));
            int height = Math.max(1, Math.round(input.getHeight() * ratio));
            resized = Bitmap.createScaledBitmap(input, width, height, true);
            int[] pixels = new int[width * height];
            resized.getPixels(pixels, 0, width, 0, 0, width, height);
            return new Raster(pixels, width, height);
        } finally {
            if (resized != input) resized.recycle();
            input.recycle();
        }
    }

    static VisualHull.View view(File file) throws IOException {
        Raster raster = decode(file, 384);
        return new VisualHull.View(raster.pixels(), raster.width(), raster.height());
    }
}
