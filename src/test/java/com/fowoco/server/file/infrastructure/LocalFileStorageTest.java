package com.fowoco.server.file.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFileStorageTest {

    private static final String MIME_TYPE = "application/pdf";

    @TempDir
    Path storageRoot;

    @Test
    void storesCompleteContentWithoutTemporaryArtifacts() throws Exception {
        LocalFileStorage storage = storage();
        byte[] content = "complete-content".getBytes(StandardCharsets.UTF_8);

        storage.store("stored-file-id", new ByteArrayInputStream(content), content.length, MIME_TYPE);

        assertThat(Files.readAllBytes(storageRoot.resolve("stored-file-id"))).isEqualTo(content);
        assertThat(temporaryArtifacts()).isEmpty();
    }

    @Test
    void cleansTemporaryArtifactWhenSourceReadFails() throws Exception {
        LocalFileStorage storage = storage();

        assertThatThrownBy(() -> storage.store("stored-file-id", failingInput(), 8, MIME_TYPE))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("failed to store file");

        assertThat(Files.exists(storageRoot.resolve("stored-file-id"))).isFalse();
        assertThat(temporaryArtifacts()).isEmpty();
    }

    @Test
    void cleansTemporaryArtifactWhenStoredSizeDiffersFromDeclaredSize() throws Exception {
        LocalFileStorage storage = storage();
        byte[] content = "content".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> storage.store(
                "stored-file-id",
                new ByteArrayInputStream(content),
                content.length + 1,
                MIME_TYPE
        ))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("failed to store file");

        assertThat(Files.exists(storageRoot.resolve("stored-file-id"))).isFalse();
        assertThat(temporaryArtifacts()).isEmpty();
    }

    @Test
    void cleansTemporaryArtifactWhenFinalPathCannotBeCreated() throws Exception {
        LocalFileStorage storage = storage();
        byte[] content = "content".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> storage.store(
                "missing-directory/stored-file-id",
                new ByteArrayInputStream(content),
                content.length,
                MIME_TYPE
        ))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("failed to store file");

        assertThat(Files.exists(storageRoot.resolve("missing-directory/stored-file-id"))).isFalse();
        assertThat(temporaryArtifacts()).isEmpty();
    }

    @Test
    void doesNotReplaceExistingFinalFile() throws Exception {
        LocalFileStorage storage = storage();
        byte[] existing = "existing".getBytes(StandardCharsets.UTF_8);
        byte[] replacement = "replacement".getBytes(StandardCharsets.UTF_8);
        Files.write(storageRoot.resolve("stored-file-id"), existing);

        assertThatThrownBy(() -> storage.store(
                "stored-file-id",
                new ByteArrayInputStream(replacement),
                replacement.length,
                MIME_TYPE
        )).isInstanceOf(UncheckedIOException.class);

        assertThat(Files.readAllBytes(storageRoot.resolve("stored-file-id"))).isEqualTo(existing);
        assertThat(temporaryArtifacts()).isEmpty();
    }

    @Test
    void deletesOnlyRequestedFileAndTreatsRepeatedDeletionAsSuccess() throws Exception {
        LocalFileStorage storage = storage();
        byte[] content = "content".getBytes(StandardCharsets.UTF_8);
        storage.store("owned-file", new ByteArrayInputStream(content), content.length, MIME_TYPE);
        storage.store("other-file", new ByteArrayInputStream(content), content.length, MIME_TYPE);

        storage.deleteIfExists("owned-file");
        storage.deleteIfExists("owned-file");

        assertThat(Files.exists(storageRoot.resolve("owned-file"))).isFalse();
        assertThat(Files.readAllBytes(storageRoot.resolve("other-file"))).isEqualTo(content);
    }

    @Test
    void rejectsStorageKeysThatEscapeTheRoot() {
        LocalFileStorage storage = storage();

        assertThatThrownBy(() -> storage.deleteIfExists("../outside-file"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not escape");
    }

    private LocalFileStorage storage() {
        return new LocalFileStorage(storageRoot.toString());
    }

    private List<Path> temporaryArtifacts() throws IOException {
        try (var files = Files.list(storageRoot)) {
            return files
                    .filter(path -> path.getFileName().toString().startsWith(".fowoco-upload-"))
                    .toList();
        }
    }

    private InputStream failingInput() {
        return new InputStream() {
            private int reads;

            @Override
            public int read() throws IOException {
                if (reads++ >= 3) {
                    throw new IOException("simulated source failure");
                }
                return 'a';
            }

            @Override
            public int read(byte[] buffer, int offset, int length) throws IOException {
                if (reads >= 3) {
                    throw new IOException("simulated source failure");
                }
                int written = Math.min(length, 3 - reads);
                for (int index = 0; index < written; index++) {
                    buffer[offset + index] = 'a';
                }
                reads += written;
                return written;
            }
        };
    }
}
