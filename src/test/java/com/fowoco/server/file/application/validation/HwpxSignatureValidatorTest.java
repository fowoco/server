package com.fowoco.server.file.application.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class HwpxSignatureValidatorTest {

    private final HwpxSignatureValidator validator = new HwpxSignatureValidator();

    @Test
    void acceptsValidHwpxStructure() throws Exception {
        byte[] content = buildZip("application/hwp+zip", "Contents/section0.xml");

        assertThat(validator.isValidHwpx(content)).isTrue();
    }

    @Test
    void rejectsWrongMimetypeEntry() throws Exception {
        byte[] content = buildZip("application/zip", "Contents/section0.xml");

        assertThat(validator.isValidHwpx(content)).isFalse();
    }

    @Test
    void rejectsMissingSectionXml() throws Exception {
        byte[] content = buildZip("application/hwp+zip", null);

        assertThat(validator.isValidHwpx(content)).isFalse();
    }

    @Test
    void rejectsNonZipContent() {
        byte[] content = "not a zip file at all".getBytes(StandardCharsets.UTF_8);

        assertThat(validator.isValidHwpx(content)).isFalse();
    }

    @Test
    void rejectsEmptyContent() {
        assertThat(validator.isValidHwpx(new byte[0])).isFalse();
    }

    @Test
    void acceptsAlternateSectionNumber() throws Exception {
        byte[] content = buildZip("application/hwp+zip", "Contents/section1.xml");

        assertThat(validator.isValidHwpx(content)).isTrue();
    }

    private byte[] buildZip(String mimetypeValue, String sectionEntryName) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("mimetype"));
            zip.write(mimetypeValue.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();

            if (sectionEntryName != null) {
                zip.putNextEntry(new ZipEntry(sectionEntryName));
                zip.write("<xml>placeholder</xml>".getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }
}
