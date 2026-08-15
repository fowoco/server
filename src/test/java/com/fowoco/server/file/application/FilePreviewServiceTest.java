package com.fowoco.server.file.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fowoco.server.auth.application.ActorContext;
import com.fowoco.server.auth.domain.UserRole;
import com.fowoco.server.common.error.ApiException;
import com.fowoco.server.common.web.RequestMetadata;
import com.fowoco.server.file.application.error.FileErrorCode;
import com.fowoco.server.file.application.port.DocumentPreviewConverter;
import com.fowoco.server.file.domain.ScanStatus;
import com.fowoco.server.file.domain.StoredFile;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FilePreviewServiceTest {

    private static final UUID FILE_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID COMPANY_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final ActorContext ACTOR = new ActorContext(
            UUID.fromString("30000000-0000-0000-0000-000000000001"),
            COMPANY_ID,
            Set.of(UserRole.HR)
    );
    private static final RequestMetadata REQUEST_METADATA = new RequestMetadata("request-1", "trace-1");

    private final FileService fileService = mock(FileService.class);
    private final DocumentPreviewConverter converter = mock(DocumentPreviewConverter.class);
    private final FilePreviewService service = new FilePreviewService(fileService, converter);

    @Test
    void returnsPdfInlineWithoutCallingConverter() throws Exception {
        byte[] original = "%PDF-1.7 original".getBytes(StandardCharsets.US_ASCII);
        when(fileService.download(FILE_ID, ACTOR, REQUEST_METADATA))
                .thenReturn(download("contract.pdf", "application/pdf", original));

        FilePreviewResult result = service.preview(FILE_ID, ACTOR, REQUEST_METADATA);

        assertThat(result.fileName()).isEqualTo("contract.pdf");
        assertThat(result.mimeType()).isEqualTo("application/pdf");
        assertThat(result.size()).isEqualTo(original.length);
        assertThat(result.content().readAllBytes()).isEqualTo(original);
        verify(converter, never()).convertToPdf(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void convertsHwpToPdfAndChangesPreviewFileName() throws Exception {
        byte[] original = "hwp-source".getBytes(StandardCharsets.UTF_8);
        byte[] converted = "%PDF-1.7 converted".getBytes(StandardCharsets.US_ASCII);
        when(fileService.download(FILE_ID, ACTOR, REQUEST_METADATA))
                .thenReturn(download("표준근로계약서.hwp", "application/octet-stream", original));
        when(converter.convertToPdf(org.mockito.ArgumentMatchers.any())).thenReturn(converted);

        FilePreviewResult result = service.preview(FILE_ID, ACTOR, REQUEST_METADATA);

        assertThat(result.fileName()).isEqualTo("표준근로계약서.pdf");
        assertThat(result.mimeType()).isEqualTo("application/pdf");
        assertThat(result.content().readAllBytes()).isEqualTo(converted);
        verify(converter).convertToPdf(org.mockito.ArgumentMatchers.argThat(source ->
                source.fileName().equals("표준근로계약서.hwp")
                        && java.util.Arrays.equals(source.content(), original)
        ));
    }

    @Test
    void mapsInvalidDocumentConversionToUnprocessableEntity() {
        when(fileService.download(FILE_ID, ACTOR, REQUEST_METADATA))
                .thenReturn(download(
                        "broken.hwpx",
                        "application/hwp+zip",
                        "invalid".getBytes(StandardCharsets.UTF_8)
                ));
        when(converter.convertToPdf(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new DocumentPreviewConversionException(
                        DocumentPreviewConversionException.Reason.INVALID_DOCUMENT,
                        "invalid document"
                ));

        assertThatThrownBy(() -> service.preview(FILE_ID, ACTOR, REQUEST_METADATA))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(FileErrorCode.FILE_PREVIEW_INVALID)
                );
    }

    @Test
    void rejectsUnsupportedPreviewTypeWithoutCallingConverter() {
        when(fileService.download(FILE_ID, ACTOR, REQUEST_METADATA))
                .thenReturn(download(
                        "archive.zip",
                        "application/zip",
                        "zip".getBytes(StandardCharsets.UTF_8)
                ));

        assertThatThrownBy(() -> service.preview(FILE_ID, ACTOR, REQUEST_METADATA))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(FileErrorCode.FILE_PREVIEW_UNSUPPORTED)
                );
        verify(converter, never()).convertToPdf(org.mockito.ArgumentMatchers.any());
    }

    private FileDownloadResult download(String name, String mimeType, byte[] content) {
        StoredFile storedFile = new StoredFile(
                FILE_ID,
                COMPANY_ID,
                name,
                mimeType,
                content.length,
                "GENERAL",
                null,
                null,
                FILE_ID.toString(),
                ScanStatus.NOT_SCANNED,
                false,
                Instant.parse("2026-08-16T00:00:00Z")
        );
        return new FileDownloadResult(storedFile, new ByteArrayInputStream(content));
    }
}
