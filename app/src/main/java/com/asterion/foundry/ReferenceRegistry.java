package com.asterion.foundry;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Local, user-authorized associations from exact reference bytes to exact originals. */
final class ReferenceRegistry {
    private final File storage;
    private final File root;

    ReferenceRegistry(File storage) {
        this.storage = storage;
        root = new File(storage, "reference-bindings");
    }

    private File original(String hash) throws Exception {
        if (hash == null || !hash.matches("[a-f0-9]{64}")) throw new IOException("Invalid source checksum");
        File sources = new File(storage, "sources").getCanonicalFile();
        File source = new File(sources, hash).getCanonicalFile();
        if (!sources.equals(source.getParentFile()) || !source.isFile()
                || !hash.equals(FilesUtil.hash(source))) throw new IOException("Original source failed verification");
        return source;
    }

    void bind(File reference, File glb) throws Exception {
        File referenceSource = original(reference.getName());
        File assetSource = original(glb.getName());
        OriginalGlb.inspect(assetSource);
        synchronized (ReferenceRegistry.class) {
            Files.createDirectories(root.toPath());
            File binding = new File(root, referenceSource.getName());
            if (binding.isFile()) {
                String previous = FilesUtil.readString(binding.toPath(), StandardCharsets.US_ASCII).trim();
                if (!previous.equals(assetSource.getName())) {
                    throw new IOException("This reference already has a different original");
                }
                return;
            }
            FilesUtil.write(binding, assetSource.getName() + "\n");
        }
    }

    File find(String referenceSha) throws Exception {
        if (referenceSha == null || !referenceSha.matches("[a-f0-9]{64}")) return null;
        File binding = new File(root, referenceSha);
        if (!binding.isFile()) return null;
        original(referenceSha);
        if (binding.length() != 65) throw new IOException("Reference binding is damaged");
        return original(FilesUtil.readString(binding.toPath(), StandardCharsets.US_ASCII).trim());
    }
}
