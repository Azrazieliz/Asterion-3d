package com.asterion.foundry;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/** Exercise the immutable source association used before reconstruction. */
public final class ReferenceRegistryAcceptance {
    public static void main(String[] args) throws Exception {
        File storage = Files.createTempDirectory("foundry-reference-test-").toFile();
        try {
            File sources = new File(storage,"sources");
            Files.createDirectories(sources.toPath());
            File reference = source(sources, Files.readAllBytes(new File(args[0]).toPath()));
            File first = source(sources, Files.readAllBytes(new File(args[1]).toPath()));
            File second = source(sources, Files.readAllBytes(new File(args[2]).toPath()));
            ReferenceRegistry registry = new ReferenceRegistry(storage);
            if (registry.find(reference.getName()) != null) throw new AssertionError("Unregistered reference resolved");
            registry.bind(reference, first);
            registry.bind(reference, first);
            try {
                registry.bind(reference, second);
                throw new AssertionError("Different original replaced the binding");
            } catch (IOException expected) {}
            File matched = new ReferenceRegistry(storage).find(reference.getName());
            if (!first.equals(matched) || Files.mismatch(first.toPath(), new File(args[1]).toPath()) != -1L) {
                throw new AssertionError("Registered original did not round-trip byte for byte");
            }
            FilesUtil.write(first, "damaged source");
            try {
                registry.find(reference.getName());
                throw new AssertionError("Tampered original was accepted");
            } catch (IOException expected) {}
            System.out.println("Reference registry passed persistence, exact bytes, conflicts and tamper detection");
        } finally {
            try (var paths = Files.walk(storage.toPath())) {
                for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }

    private static File source(File sources, byte[] bytes) throws Exception {
        File staged = File.createTempFile("stage-", ".tmp", sources);
        FilesUtil.write(staged, bytes);
        File original = new File(sources, FilesUtil.hash(staged));
        FilesUtil.move(staged, original);
        return original;
    }
}
