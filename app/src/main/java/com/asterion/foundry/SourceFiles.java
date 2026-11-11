package com.asterion.foundry;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Rect;
import java.io.*;
import java.util.LinkedHashMap;
import java.util.Map;
import org.json.JSONObject;

/** Immutable content-addressed original files, separated from generated projects. */
final class SourceFiles {
    static final long DEFAULT_IMPORT_BYTES = 64L * 1024 * 1024;
    static final long MODEL_IMPORT_BYTES = 1024L * 1024 * 1024;
    private static final long MIN_FREE_BYTES = 256L * 1024 * 1024;
    private final File root;

    SourceFiles(File storage) {
        root = new File(storage, "sources");
    }

    File preserve(InputStream input) throws Exception {
        return preserve(input, DEFAULT_IMPORT_BYTES);
    }

    File preserve(InputStream input, long maxBytes) throws Exception {
        if (input == null) throw new IOException("The document provider returned no input");
        if (maxBytes != DEFAULT_IMPORT_BYTES && maxBytes != MODEL_IMPORT_BYTES)
            throw new IOException("Unsupported import size budget");
        if (root.getUsableSpace() < MIN_FREE_BYTES)
            throw new IOException("Not enough free app storage to preserve an original source");
        File temporary = File.createTempFile("import-", ".incoming", root);
        try {
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[65536];
                long size = 0;
                for (int n; (n = input.read(buffer)) != -1;) {
                    size += n;
                    if (size > maxBytes)
                        throw new IOException("This source exceeds the selected " + (maxBytes / (1024 * 1024)) + " MiB import limit");
                    output.write(buffer, 0, n);
                    if ((size & ((16L * 1024 * 1024) - 1)) < n
                            && root.getUsableSpace() < MIN_FREE_BYTES)
                        throw new IOException("Original source import stopped to preserve free app storage");
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

    /** Private, byte-preserving original + independently SHA-addressed crop. */
    record CroppedSource(File derived, File original, ReferenceCrop.Box region,
                         int originalWidth, int originalHeight, int outputWidth,
                         int outputHeight) {
        JSONObject evidence() throws Exception {
            return new JSONObject().put("schema", ReferenceCrop.VERSION)
                    .put("original_sha256", original.getName())
                    .put("crop_sha256", derived.getName())
                    .put("source_width", originalWidth)
                    .put("source_height", originalHeight)
                    .put("left", region.left()).put("top", region.top())
                    .put("right", region.right()).put("bottom", region.bottom())
                    .put("output_width", outputWidth).put("output_height", outputHeight)
                    .put("original_unchanged",true)
                    .put("reference_view_alignment","must_be_reviewed");
        }
    }

    CroppedSource cropPreserved(File original,ReferenceCrop.Box region) throws Exception {
        if(original==null)throw new IOException("No preserved source image for cropping");
        File checked=original.getCanonicalFile();
        if(!checked.isFile()||!checked.getParentFile().equals(root.getCanonicalFile())
                ||!checked.getName().matches("[0-9a-f]{64}")
                ||!FilesUtil.hash(checked).equals(checked.getName()))
            throw new IOException("Selected image is not an intact preserved original");
        BitmapFactory.Options meta=new BitmapFactory.Options();
        meta.inJustDecodeBounds=true;
        BitmapFactory.decodeFile(checked.getAbsolutePath(),meta);
        region.requireInside(meta.outWidth,meta.outHeight);
        if(region.isFull(meta.outWidth,meta.outHeight))
            return new CroppedSource(checked,checked,region,
                    meta.outWidth,meta.outHeight,meta.outWidth,meta.outHeight);
        File png=File.createTempFile("crop-", ".png.incoming", root);
        BitmapRegionDecoder decoder=null;
        Bitmap bitmap=null;
        try {
            decoder=BitmapRegionDecoder.newInstance(checked.getAbsolutePath(),false);
            int sample=1;
            while(Math.max(region.width(),region.height())/sample>ReferenceCrop.MAX_MODEL_SOURCE)
                sample*=2;
            BitmapFactory.Options options=new BitmapFactory.Options();
            options.inSampleSize=sample;
            options.inPreferredConfig=Bitmap.Config.ARGB_8888;
            bitmap=decoder.decodeRegion(
                    new Rect(region.left(),region.top(),region.right(),region.bottom()),options);
            if(bitmap==null)throw new IOException("Crop image could not be decoded");
            int width=bitmap.getWidth(),height=bitmap.getHeight();
            if(width<ReferenceCrop.MIN_PIXELS||height<ReferenceCrop.MIN_PIXELS)
                throw new IOException("Selected crop has too few pixels for reconstruction");
            try(FileOutputStream output=new FileOutputStream(png)){
                if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,output))
                    throw new IOException("PNG encoding failed for selected subject");
                output.getFD().sync();
            }
            if(png.length()>DEFAULT_IMPORT_BYTES)
                throw new IOException("Crop exceeds the source image storage limit");
            File derived;
            try(InputStream input=new FileInputStream(png)){
                derived=preserve(input,DEFAULT_IMPORT_BYTES);
            }
            if(!FilesUtil.hash(checked).equals(checked.getName()))
                throw new IOException("Original image changed while creating a crop");
            return new CroppedSource(derived,checked,region,
                    meta.outWidth,meta.outHeight,width,height);
        }finally{
            if(bitmap!=null)bitmap.recycle();
            if(decoder!=null)decoder.recycle();
            png.delete();
        }
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
