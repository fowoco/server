package com.fowoco.server.demo.infrastructure.documentdata;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class DemoDocumentFileInstaller {

    private final Path rootDirectory;

    DemoDocumentFileInstaller(String localPath) {
        this.rootDirectory = Path.of(localPath).toAbsolutePath().normalize();
    }

    void install(String storageKey, byte[] content) {
        Path target = target(storageKey);
        try {
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                requireExpectedRegularFile(target, storageKey, content);
                return;
            }
            Files.createDirectories(target.getParent());
            Path temporary = Files.createTempFile(target.getParent(), ".demo-document-", ".tmp");
            try {
                Files.write(temporary, content);
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temporary);
            }
            requireExpectedRegularFile(target, storageKey, content);
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to install demo document file", exception);
        }
    }

    void verify(String storageKey, byte[] content) {
        try {
            requireExpectedRegularFile(target(storageKey), storageKey, content);
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to verify demo document file", exception);
        }
    }

    void cleanup(String storageKey, byte[] content) {
        Path target = target(storageKey);
        try {
            if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                return;
            }
            requireExpectedRegularFile(target, storageKey, content);
            Files.delete(target);
            pruneEmptyParents(target.getParent());
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to clean up demo document file", exception);
        }
    }

    private void requireExpectedRegularFile(Path target, String storageKey, byte[] expected)
            throws IOException {
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)
                || Files.size(target) != expected.length
                || !sha256(Files.readAllBytes(target)).equals(sha256(expected))) {
            throw new IllegalStateException(
                    "demo document storage key contains unexpected content: " + storageKey
            );
        }
    }

    private void pruneEmptyParents(Path directory) throws IOException {
        Path current = directory;
        while (current != null && !current.equals(rootDirectory) && current.startsWith(rootDirectory)) {
            try (var entries = Files.list(current)) {
                if (entries.findAny().isPresent()) {
                    return;
                }
            }
            Files.delete(current);
            current = current.getParent();
        }
    }

    private Path target(String storageKey) {
        Path target = rootDirectory.resolve(storageKey).normalize();
        if (!target.startsWith(rootDirectory)) {
            throw new IllegalArgumentException("demo document storage key must not escape the storage root");
        }
        return target;
    }

    static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
