package com.fowoco.server.demo.infrastructure.documentdata;

import static org.assertj.core.api.Assertions.assertThat;

import com.fowoco.server.demo.infrastructure.documentdata.DemoDocumentFixtureCatalog.DemoDocumentFixture;
import com.fowoco.server.demo.infrastructure.documentdata.DemoDocumentFixtureCatalog.FixtureFormat;
import com.fowoco.server.file.application.validation.HwpSignatureValidator;
import com.fowoco.server.file.application.validation.HwpxSignatureValidator;
import java.nio.charset.StandardCharsets;
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

    private byte[] generate(FixtureFormat format) {
        DemoDocumentFixture template = DemoDocumentFixtureCatalog.fixtures().stream()
                .filter(fixture -> fixture.format() == format)
                .findFirst()
                .orElseThrow();
        return generator.generate(template);
    }
}
