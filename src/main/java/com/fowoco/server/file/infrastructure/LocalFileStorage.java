package com.fowoco.server.file.infrastructure;

import com.fowoco.server.file.application.port.FileStorage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LocalFileStorage implements FileStorage {

    private static final String TEMPORARY_FILE_PREFIX = ".fowoco-upload-";
    private static final String TEMPORARY_FILE_SUFFIX = ".tmp";

    private final Path rootDirectory;

    public LocalFileStorage(@Value("${app.file-storage.local-path}") String localPath) {
        this.rootDirectory = Path.of(localPath).normalize();
    }

    @Override
    public void store(String storageKey, InputStream content, long size, String mimeType) {
        Path temporary = null;
        try {
            Files.createDirectories(rootDirectory);
            Path target = resolveSafely(storageKey);
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                throw new FileAlreadyExistsException(target.toString());
            }

            temporary = Files.createTempFile(
                    rootDirectory,
                    TEMPORARY_FILE_PREFIX,
                    TEMPORARY_FILE_SUFFIX
            );
            Files.copy(content, temporary, StandardCopyOption.REPLACE_EXISTING);
            long storedSize = Files.size(temporary);
            if (storedSize != size) {
                throw new IOException(
                        "stored file size does not match the declared size: expected="
                                + size + ", actual=" + storedSize
                );
            }
            moveToFinalPath(temporary, target);
        } catch (IOException exception) {
            deleteTemporaryAfterFailure(temporary, exception);
            throw new UncheckedIOException("failed to store file: " + storageKey, exception);
        } catch (RuntimeException exception) {
            deleteTemporaryAfterFailure(temporary, exception);
            throw exception;
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

    @Override
    public void deleteIfExists(String storageKey) {
        Path target = resolveSafely(storageKey);
        try {
            Files.deleteIfExists(target);
        } catch (IOException exception) {
            throw new UncheckedIOException("failed to delete file: " + storageKey, exception);
        }
    }

    private void moveToFinalPath(Path temporary, Path target) throws IOException {
        Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
    }

    private void deleteTemporaryAfterFailure(Path temporary, Throwable failure) {
        if (temporary == null) {
            return;
        }
        try {
            Files.deleteIfExists(temporary);
        } catch (IOException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
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
