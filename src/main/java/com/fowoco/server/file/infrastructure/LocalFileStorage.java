package com.fowoco.server.file.infrastructure;

import com.fowoco.server.file.application.port.FileStorage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
            Path target = rootDirectory.resolve(storageKey).normalize();
            if (!target.startsWith(rootDirectory)) {
                throw new IllegalArgumentException("storageKey must not escape the storage root");
            }
            Files.copy(content, target);
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to store file: " + storageKey, exception);
        }
    }
}
