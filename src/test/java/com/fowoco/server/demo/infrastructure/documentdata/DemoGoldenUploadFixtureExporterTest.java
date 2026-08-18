package com.fowoco.server.demo.infrastructure.documentdata;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DemoGoldenUploadFixtureExporterTest {

    @TempDir
    Path output;

    @Test
    void exportsDeterministicSyntheticArcFilesWithoutDatabaseAccess() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-08-18T00:00:00Z"), ZoneOffset.UTC);

        List<Path> first = DemoGoldenUploadFixtureExporter.export(output, clock);
        List<byte[]> originalContents = first.stream().map(this::read).toList();
        List<Path> second = DemoGoldenUploadFixtureExporter.export(output, clock);

        assertThat(first).extracting(path -> path.getFileName().toString())
                .containsExactly(
                        "외국인등록증_앞면_응웬반A.png",
                        "외국인등록증_뒷면_응웬반A.jpg"
                );
        assertThat(second.stream().map(this::read).toList())
                .usingElementComparator((left, right) -> java.util.Arrays.compare(left, right))
                .containsExactlyElementsOf(originalContents);
        assertThat(originalContents.get(0)).startsWith((byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G');
        assertThat(originalContents.get(1)).startsWith((byte) 0xFF, (byte) 0xD8, (byte) 0xFF);
    }

    private byte[] read(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (java.io.IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
    }
}
