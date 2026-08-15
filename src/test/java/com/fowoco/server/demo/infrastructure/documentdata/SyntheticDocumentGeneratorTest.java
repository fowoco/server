package com.fowoco.server.demo.infrastructure.documentdata;

import static org.assertj.core.api.Assertions.assertThat;

import com.fowoco.server.demo.infrastructure.documentdata.DemoDocumentFixtureCatalog.DemoDocumentFixture;
import com.fowoco.server.demo.infrastructure.documentdata.DemoDocumentFixtureCatalog.FixtureFormat;
import com.fowoco.server.file.application.validation.HwpSignatureValidator;
import com.fowoco.server.file.application.validation.HwpxSignatureValidator;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class SyntheticDocumentGeneratorTest {

    private final SyntheticDocumentGenerator generator = new SyntheticDocumentGenerator();

    @Test
    void generatesRealSignaturesForEverySupportedDemoFormat() {
        assertThat(generate(FixtureFormat.PNG)).startsWith(0x89, 0x50, 0x4e, 0x47);
        assertThat(generate(FixtureFormat.JPEG)).startsWith(0xff, 0xd8, 0xff);
        assertThat(new String(generate(FixtureFormat.PDF), 0, 8, StandardCharsets.US_ASCII))
                .isEqualTo("%PDF-1.4");
        assertThat(new HwpSignatureValidator().isValidHwp(generate(FixtureFormat.HWP))).isTrue();
        assertThat(new HwpxSignatureValidator().isValidHwpx(generate(FixtureFormat.HWPX))).isTrue();
    }

    @Test
    void generatesPassportLikeGoldWorkerFixtureWithSyntheticPortrait() throws IOException {
        DemoDocumentFixture passport = DemoDocumentFixtureCatalog.fixtures().stream()
                .filter(fixture -> fixture.documentId()
                        .equals(DemoDocumentFixtureCatalog.PASSPORT_BIO_DOCUMENT_ID))
                .findFirst()
                .orElseThrow();

        var image = ImageIO.read(new ByteArrayInputStream(generator.generate(
                passport,
                LocalDate.of(2025, 8, 15),
                LocalDate.of(2027, 8, 15)
        )));

        assertThat(image.getWidth()).isEqualTo(1400);
        assertThat(image.getHeight()).isEqualTo(900);
        assertThat(image.getRGB(200, 350)).isNotEqualTo(image.getRGB(500, 350));
        assertThat(generator.generate(
                passport,
                LocalDate.of(2025, 8, 16),
                LocalDate.of(2027, 8, 16)
        )).isNotEqualTo(generator.generate(
                passport,
                LocalDate.of(2025, 8, 15),
                LocalDate.of(2027, 8, 15)
        ));
    }

    @Test
    void generatesTwentySevenDifferentPassportCopiesForRemainingWorkers() throws IOException {
        LocalDate anchorDate = LocalDate.of(2026, 8, 15);
        var fixtures = DemoDocumentFixtureCatalog.passportCoverageFixtures();
        Set<String> checksums = new HashSet<>();
        Set<Object> workerIds = new HashSet<>();

        assertThat(fixtures).hasSize(27);
        for (DemoDocumentFixture fixture : fixtures) {
            byte[] content = generator.generate(
                    fixture,
                    anchorDate.plusDays(fixture.issueDays()),
                    anchorDate.plusDays(fixture.expiryDays())
            );
            var image = ImageIO.read(new ByteArrayInputStream(content));

            assertThat(fixture.passportIdentity()).isNotNull();
            assertThat(fixture.originalFilename()).endsWith(".png");
            assertThat(fixture.contentType()).isEqualTo("image/png");
            assertThat(image.getWidth()).isEqualTo(1400);
            assertThat(image.getHeight()).isEqualTo(900);
            checksums.add(DemoDocumentFileInstaller.sha256(content));
            workerIds.add(fixture.workerId());
        }

        assertThat(checksums).hasSize(27);
        assertThat(workerIds).hasSize(27);
    }

    private byte[] generate(FixtureFormat format) {
        DemoDocumentFixture template = DemoDocumentFixtureCatalog.fixtures().stream()
                .filter(fixture -> fixture.format() == format)
                .findFirst()
                .orElseThrow();
        return generator.generate(
                template,
                LocalDate.of(2025, 8, 15),
                LocalDate.of(2027, 8, 15)
        );
    }
}
