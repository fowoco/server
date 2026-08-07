package com.fowoco.server.file.infrastructure;

import com.fowoco.server.file.application.port.FileStorage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LocalFileStorage implements FileStorage {

    private final Path rootDirectory;

    public LocalFileStorage(@Value("${app.file-storage.local-path}") String localPath) {
        this.rootDirectory = Path.of(localPath).normalize();
    }

    @Override
    public void store(String storageKey, InputStream content, long size, String mimeType) {
        try {
            Files.createDirectories(rootDirectory);
            Path target = resolveSafely(storageKey);
            Files.copy(content, target);
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to store file: " + storageKey, exception);
        }
    }

    @Override
    public Optional<InputStream> open(String storageKey) {
        Path target = resolveSafely(storageKey);
        if (!Files.isRegularFile(target)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.newInputStream(target));
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to open file: " + storageKey, exception);
        }
    }

    private Path resolveSafely(String storageKey) {
        Path target = rootDirectory.resolve(storageKey).normalize();
        if (!target.startsWith(rootDirectory)) {
            throw new IllegalArgumentException("storageKey must not escape the storage root");
        }
        return target;
    }
}
