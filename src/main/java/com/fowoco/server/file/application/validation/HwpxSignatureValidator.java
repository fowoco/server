package com.fowoco.server.file.application.validation;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.stereotype.Component;

/**
 * HWPX는 ZIP(OWPML) 구조이며, 정식 MIME 타입은 있지만(application/hwp+zip)
 * 클라이언트가 보내는 값을 신뢰하지 않는다. 실제 압축 내부에
 * 최상위 "mimetype" 항목의 값이 "application/hwp+zip"인지, 그리고
 * 본문 콘텐츠("Contents/section0.xml" 또는 호환 경로)가 있는지 확인한다.
 */
@Component
public class HwpxSignatureValidator {

    private static final String MIMETYPE_ENTRY_NAME = "mimetype";
    private static final String EXPECTED_MIMETYPE = "application/hwp+zip";
    private static final String SECTION_ENTRY_PREFIX = "Contents/section";
    private static final String SECTION_ENTRY_SUFFIX = ".xml";

    public boolean isValidHwpx(byte[] content) {
        boolean mimetypeMatched = false;
        boolean sectionFound = false;

        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                String entryName = entry.getName();
                if (MIMETYPE_ENTRY_NAME.equals(entryName)) {
                    mimetypeMatched = EXPECTED_MIMETYPE.equals(readEntryAsString(zipInputStream).strip());
                } else if (isSectionEntry(entryName)) {
                    sectionFound = true;
                }
            }
        } catch (IOException | IllegalArgumentException exception) {
            return false;
        }

        return mimetypeMatched && sectionFound;
    }

    private boolean isSectionEntry(String entryName) {
        return entryName != null
                && entryName.startsWith(SECTION_ENTRY_PREFIX)
                && entryName.endsWith(SECTION_ENTRY_SUFFIX);
    }

    private String readEntryAsString(InputStream stream) throws IOException {
        return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
}
