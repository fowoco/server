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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipInputStream;
import javax.imageio.ImageIO;
import org.apache.poi.poifs.filesystem.DocumentEntry;
import org.apache.poi.poifs.filesystem.DocumentInputStream;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
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
        assertThat(image.getHeight()).isEqualTo(980);
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
            assertThat(image.getHeight()).isEqualTo(980);
            checksums.add(DemoDocumentFileInstaller.sha256(content));
            workerIds.add(fixture.workerId());
        }

        assertThat(checksums).hasSize(27);
        assertThat(workerIds).hasSize(27);
    }

    @Test
    void assignsOneConsistentSyntheticIdentityToEveryWorkerDocumentFixture() {
        Map<UUID, Object> identitiesByWorker = new HashMap<>();

        for (DemoDocumentFixture fixture : DemoDocumentFixtureCatalog.fixtures()) {
            assertThat(fixture.passportIdentity()).isNotNull();
            Object previous = identitiesByWorker.putIfAbsent(
                    fixture.workerId(), fixture.passportIdentity()
            );
            if (previous != null) {
                assertThat(fixture.passportIdentity()).isEqualTo(previous);
            }
        }

        assertThat(identitiesByWorker).hasSize(28);
    }

    @Test
    void embedsWorkerAndDocumentMetadataInPdfHwpAndHwpxContent() throws IOException {
        LocalDate issueDate = LocalDate.of(2026, 7, 16);
        LocalDate expiryDate = LocalDate.of(2027, 2, 11);

        DemoDocumentFixture pdfFixture = fixture(FixtureFormat.PDF);
        String pdf = new String(
                generator.generate(pdfFixture, issueDate, expiryDate),
                StandardCharsets.US_ASCII
        );
        assertMetadataText(pdf, pdfFixture, issueDate, expiryDate);

        DemoDocumentFixture hwpFixture = fixture(FixtureFormat.HWP);
        byte[] hwp = generator.generate(hwpFixture, issueDate, expiryDate);
        try (POIFSFileSystem fileSystem = new POIFSFileSystem(new ByteArrayInputStream(hwp));
             var stream = new DocumentInputStream(
                     (DocumentEntry) fileSystem.getRoot().getEntry("FOWOCO-Metadata")
             )) {
            assertMetadataText(
                    new String(stream.readAllBytes(), StandardCharsets.UTF_8),
                    hwpFixture,
                    issueDate,
                    expiryDate
            );
        }

        DemoDocumentFixture hwpxFixture = fixture(FixtureFormat.HWPX);
        byte[] hwpx = generator.generate(hwpxFixture, issueDate, expiryDate);
        String preview = hwpxEntry(hwpx, "Preview/PrvText.txt");
        assertMetadataText(preview, hwpxFixture, issueDate, expiryDate);
        assertThat(hwpxEntry(hwpx, "Contents/section0.xml"))
                .contains(hwpxFixture.passportIdentity().englishName())
                .contains(issueDate.toString())
                .contains(expiryDate.toString())
                .doesNotContain("DEMO_HOLDER_NAME", "2098-01-01", "2099-01-01");
    }

    private void assertMetadataText(
            String content,
            DemoDocumentFixture fixture,
            LocalDate issueDate,
            LocalDate expiryDate
    ) {
        assertThat(content)
                .contains(fixture.documentId().toString())
                .contains(fixture.workerId().toString())
                .contains(fixture.passportIdentity().englishName())
                .contains(fixture.passportIdentity().nationalityCode())
                .contains(fixture.passportIdentity().preferredLanguage())
                .contains(fixture.passportIdentity().visaType())
                .contains(fixture.documentType().name())
                .contains(fixture.status().name())
                .contains(issueDate.toString())
                .contains(expiryDate.toString())
                .contains(fixture.storageFilename());
    }

    private String hwpxEntry(byte[] content, String expectedName) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (entry.getName().equals(expectedName)) {
                    return new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        throw new AssertionError("HWPX entry is missing: " + expectedName);
    }

    private byte[] generate(FixtureFormat format) {
        DemoDocumentFixture template = fixture(format);
        return generator.generate(
                template,
                LocalDate.of(2025, 8, 15),
                LocalDate.of(2027, 8, 15)
        );
    }

    private DemoDocumentFixture fixture(FixtureFormat format) {
        return DemoDocumentFixtureCatalog.fixtures().stream()
                .filter(fixture -> fixture.format() == format)
                .findFirst()
                .orElseThrow();
    }
}
