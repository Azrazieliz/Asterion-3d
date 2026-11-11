package com.asterion.foundry;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** The model pack is a one-time local install. Files are pinned before promotion. */
final class TripoModels {
    static final String VERSION = "triposr-q8-isnet-slimsam-048725e-v2";
    static final String EMBEDDED_ASSET = "foundry-3d.foundrymodels";
    static final String ENCODER = "triplane_q8.onnx";
    static final String WEIGHTS = "triplane_q8.onnx.data";
    static final String DECODER = "decoder.onnx";
    static final String SEGMENTER = "isnetis.onnx";
    private record Member(String name, long bytes, String sha256) {}
    private static String verifiedPath;
    private static final Member[] MEMBERS = {
        new Member(ENCODER,885712,"7721f5a45e79e02de8c6e909c21fb8e922fd0b594038a8fc1cfefa42c79ad6d5"),
        new Member(WEIGHTS,483785728,"50bf9b1c1218dc2c7fb6051d0d2cb7a48a1c5f75cc86e67a9d0e09a931298e35"),
        new Member(DECODER,173796,"6ff43581f1d2b46aa61764c2729a8d33985b51cb6c8088cf36ffee3fd70355eb"),
        new Member(SEGMENTER,176069933,"f15622d853e8260172812b657053460e20806f04b9e05147d49af7bed31a6e99"),
        new Member(SlimSam.ENCODER,23276014,"9f8433273a6750b587779baa0cf5508111001bf7e7acfcf585d370139fd366d0"),
        new Member(SlimSam.DECODER,16557892,"f4514391764fbd56e08e119060d874ecd7d52994bfb1968af159e12d4943b5bb")
    };

    static File directory(File root) { return new File(root,"models/" + VERSION); }

    static boolean ready(File root) {
        File location = directory(root);
        for (Member member : MEMBERS) {
            File file = new File(location,member.name);
            if (!file.isFile() || file.length() != member.bytes) return false;
        }
        return true;
    }

    /** APK-owned pinned weights are unpacked privately when an ONNX job needs file paths. */
    static synchronized void ensureBundled(Context context, File root) throws Exception {
        if (verifiedReady(root)) return;
        File target = directory(root);
        if (target.exists()) {
            // An older manual pack may be corrupt or incomplete: retain every byte,
            // but do not let it shadow the exact weights embedded in this APK.
            File recovery = new File(root, "archive/model-recovery-" + System.currentTimeMillis());
            Files.createDirectories(recovery.toPath().getParent());
            if (recovery.exists()) throw new IOException("Model recovery destination already exists");
            try {
                Files.move(target.toPath(), recovery.toPath(), StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(target.toPath(), recovery.toPath());
            }
        }
        try (InputStream archive = context.getAssets().open(
                EMBEDDED_ASSET, android.content.res.AssetManager.ACCESS_STREAMING)) {
            install(root, archive);
        } catch (java.io.FileNotFoundException absent) {
            throw new IOException("Offline reconstruction weights missing from this APK", absent);
        }
        if (!verifiedReady(root))
            throw new IOException("Bundled 3D model checksum verification failed after extraction");
    }

    private static boolean verifiedReady(File root) throws Exception {
        if (!ready(root)) return false;
        File location = directory(root).getCanonicalFile();
        if (location.getAbsolutePath().equals(verifiedPath)) return true;
        for (Member member : MEMBERS) {
            if (!FilesUtil.hash(new File(location, member.name)).equals(member.sha256)) return false;
        }
        verifiedPath = location.getAbsolutePath();
        return true;
    }

    static synchronized void install(File root, InputStream input) throws Exception {
        if (input == null) throw new IOException("No model archive was opened");
        File target = directory(root);
        if (ready(root)) return;
        if (target.exists()) throw new IOException("Model installation already exists but is incomplete");
        if (root.getUsableSpace() < 1024L*1024*1024) throw new IOException("Free at least 1 GiB for the model pack");
        Files.createDirectories(target.toPath().getParent());
        File staging = Files.createTempDirectory(target.toPath().getParent(), "install-").toFile();
        try (ZipInputStream archive = new ZipInputStream(input)) {
            Set<String> found = new HashSet<>();
            byte[] data = new byte[1024*1024];
            for (ZipEntry entry; (entry = archive.getNextEntry()) != null;) {
                Member member = member(entry.getName());
                if (entry.isDirectory() || member == null || !found.add(member.name)) {
                    throw new IOException("Unexpected model archive member");
                }
                File output = new File(staging,member.name);
                long length = 0;
                try (FileOutputStream stream = new FileOutputStream(output)) {
                    for (int n; (n=archive.read(data)) != -1;) {
                        length += n;
                        if (length > member.bytes) throw new IOException("Oversized model: " + member.name);
                        stream.write(data,0,n);
                    }
                    stream.getFD().sync();
                }
                if (length != member.bytes || !FilesUtil.hash(output).equals(member.sha256)) {
                    throw new IOException("Model checksum mismatch: " + member.name);
                }
                archive.closeEntry();
            }
            if (found.size()!=MEMBERS.length) throw new IOException("Model archive is incomplete");
            Files.move(staging.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE);
        } finally {
            File[] partials = staging.listFiles();
            if (partials != null) for (File partial : partials) Files.deleteIfExists(partial.toPath());
            Files.deleteIfExists(staging.toPath());
        }
    }

    private static Member member(String name) {
        for (Member member : MEMBERS) if (member.name.equals(name)) return member;
        return null;
    }
}
