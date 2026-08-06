package com.fowoco.server.file.application.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.junit.jupiter.api.Test;

class HwpSignatureValidatorTest {

    private final HwpSignatureValidator validator = new HwpSignatureValidator();

    @Test
    void acceptsValidHwpSignature() throws Exception {
        byte[] content = buildOleFile("HWP Document File");

        assertThat(validator.isValidHwp(content)).isTrue();
    }

    @Test
    void rejectsOleFileWithoutHwpSignature() throws Exception {
        byte[] content = buildOleFile("Not A HWP Document");

        assertThat(validator.isValidHwp(content)).isFalse();
    }

    @Test
    void rejectsNonOleFile() {
        byte[] content = "plain text content, not an OLE file".getBytes(StandardCharsets.UTF_8);

        assertThat(validator.isValidHwp(content)).isFalse();
    }

    @Test
    void rejectsEmptyContent() {
        assertThat(validator.isValidHwp(new byte[0])).isFalse();
    }

    private byte[] buildOleFile(String signatureText) throws Exception {
        try (POIFSFileSystem fileSystem = new POIFSFileSystem()) {
            byte[] header = new byte[256];
            byte[] signatureBytes = signatureText.getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(signatureBytes, 0, header, 0, signatureBytes.length);
            fileSystem.createDocument(new java.io.ByteArrayInputStream(header), "FileHeader");

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            fileSystem.writeFilesystem(out);
            return out.toByteArray();
        }
    }
}
