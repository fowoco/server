package com.fowoco.server.demo.infrastructure.seed;

import com.fowoco.server.demo.infrastructure.seed.DemoOperationalSeedCatalog.StoredFileSeed;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import org.springframework.core.io.ClassPathResource;

final class DemoFileFixtureInstaller {

    private final Path rootDirectory;

    DemoFileFixtureInstaller(String localPath) {
        this.rootDirectory = Path.of(localPath).toAbsolutePath().normalize();
    }

    long expectedSize(StoredFileSeed seed) {
        return sourceBytes(seed).length;
    }

    void install(StoredFileSeed seed) {
        byte[] expected = sourceBytes(seed);
        Path target = target(seed.storageKey());
        try {
            Files.createDirectories(rootDirectory);
            if (Files.exists(target)) {
                verify(target, expected, seed);
                return;
            }
            Path temporary = Files.createTempFile(rootDirectory, ".demo-fixture-", ".tmp");
            try {
                Files.write(temporary, expected);
                moveWithoutOverwrite(temporary, target);
            } catch (FileAlreadyExistsException exception) {
                verify(target, expected, seed);
            } finally {
                Files.deleteIfExists(temporary);
            }
            verify(target, expected, seed);
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to install demo file fixture: " + seed.name(), exception);
        }
    }

    void verify(StoredFileSeed seed) {
        byte[] expected = sourceBytes(seed);
        Path target = target(seed.storageKey());
        try {
            verify(target, expected, seed);
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to verify demo file fixture: " + seed.name(), exception);
        }
    }

    private byte[] sourceBytes(StoredFileSeed seed) {
        try (InputStream input = new ClassPathResource(seed.resourcePath()).getInputStream()) {
            return input.readAllBytes();
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to read demo file fixture: " + seed.resourcePath(), exception);
        }
    }

    private Path target(String storageKey) {
        Path target = rootDirectory.resolve(storageKey).normalize();
        if (!target.startsWith(rootDirectory)) {
            throw new IllegalArgumentException("demo storage key must not escape the storage root");
        }
        return target;
    }

    private void moveWithoutOverwrite(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target);
        }
    }

    private void verify(Path target, byte[] expected, StoredFileSeed seed) throws IOException {
        if (!Files.isRegularFile(target)
                || Files.size(target) != expected.length
                || !Arrays.equals(sha256(Files.readAllBytes(target)), sha256(expected))) {
            throw new IllegalStateException(
                    "a demo file storage key already contains different content: " + seed.storageKey()
            );
        }
    }

    private byte[] sha256(byte[] content) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(content);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
