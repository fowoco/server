package com.fowoco.server.file.application.validation;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.springframework.stereotype.Component;

/**
 * HWP 파일은 OLE Compound File 구조이며, 정식 MIME 타입이 없다.
 * 파일 내부의 "FileHeader" 스트림 앞부분에 있는 "HWP Document File" 문자열로
 * 실제 HWP 문서인지 확인한다.
 */
@Component
public class HwpSignatureValidator {

    private static final String FILE_HEADER_STREAM_NAME = "FileHeader";
    private static final String HWP_SIGNATURE = "HWP Document File";

    public boolean isValidHwp(byte[] content) {
        try (POIFSFileSystem fileSystem = new POIFSFileSystem(new ByteArrayInputStream(content))) {
            if (!fileSystem.getRoot().hasEntry(FILE_HEADER_STREAM_NAME)) {
                return false;
            }
            byte[] header = fileSystem.createDocumentInputStream(FILE_HEADER_STREAM_NAME)
                    .readAllBytes();
            if (header.length < HWP_SIGNATURE.length()) {
                return false;
            }
            String signature = new String(header, 0, HWP_SIGNATURE.length(), StandardCharsets.US_ASCII);
            return HWP_SIGNATURE.equals(signature);
        } catch (IOException | RuntimeException exception) {
            return false;
        }
    }
}
