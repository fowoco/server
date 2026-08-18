package com.fowoco.server.file.application;

import com.fowoco.server.auth.application.ActorContext;
import com.fowoco.server.common.error.ApiException;
import com.fowoco.server.common.web.RequestMetadata;
import com.fowoco.server.file.application.error.FileErrorCode;
import com.fowoco.server.file.application.port.DocumentPreviewConverter;
import com.fowoco.server.file.domain.StoredFile;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class FilePreviewService {

    private static final String PDF_MIME_TYPE = "application/pdf";
    private static final Set<String> INLINE_MIME_TYPES = Set.of(
            PDF_MIME_TYPE,
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    private final FileService fileService;
    private final DocumentPreviewConverter documentPreviewConverter;

    public FilePreviewService(FileService fileService, DocumentPreviewConverter documentPreviewConverter) {
        this.fileService = fileService;
        this.documentPreviewConverter = documentPreviewConverter;
    }

    public FilePreviewResult preview(
            UUID fileId,
            ActorContext actor,
            RequestMetadata requestMetadata
    ) {
        FileDownloadResult download = fileService.download(fileId, actor, requestMetadata);
        StoredFile storedFile = download.storedFile();
        String normalizedMimeType = normalizeMimeType(storedFile.mimeType());

        if (INLINE_MIME_TYPES.contains(normalizedMimeType)) {
            return new FilePreviewResult(
                    storedFile.name(),
                    normalizedMimeType,
                    storedFile.size(),
                    download.content()
            );
        }
        if (!isConvertibleDocument(storedFile.name())) {
            closeQuietly(download.content());
            throw new ApiException(FileErrorCode.FILE_PREVIEW_UNSUPPORTED);
        }

        try (InputStream content = download.content()) {
            byte[] converted = documentPreviewConverter.convertToPdf(new DocumentPreviewSource(
                    storedFile.name(),
                    storedFile.mimeType(),
                    content.readAllBytes()
            ));
            return new FilePreviewResult(
                    pdfFileName(storedFile.name()),
                    PDF_MIME_TYPE,
                    converted.length,
                    new ByteArrayInputStream(converted)
            );
        } catch (DocumentPreviewConversionException exception) {
            if (exception.reason() == DocumentPreviewConversionException.Reason.INVALID_DOCUMENT) {
                throw new ApiException(FileErrorCode.FILE_PREVIEW_INVALID);
            }
            throw new ApiException(FileErrorCode.FILE_PREVIEW_UNAVAILABLE);
        } catch (IOException exception) {
            throw new ApiException(FileErrorCode.FILE_PREVIEW_UNAVAILABLE);
        }
    }

    private boolean isConvertibleDocument(String fileName) {
        String normalized = fileName.toLowerCase(Locale.ROOT);
        return normalized.endsWith(".hwp") || normalized.endsWith(".hwpx");
    }

    private String pdfFileName(String originalName) {
        int extensionStart = originalName.lastIndexOf('.');
        String baseName = extensionStart > 0 ? originalName.substring(0, extensionStart) : originalName;
        return baseName + ".pdf";
    }

    private String normalizeMimeType(String mimeType) {
        int parameterStart = mimeType.indexOf(';');
        String value = parameterStart >= 0 ? mimeType.substring(0, parameterStart) : mimeType;
        return value.strip().toLowerCase(Locale.ROOT);
    }

    private void closeQuietly(InputStream inputStream) {
        try {
            inputStream.close();
        } catch (IOException ignored) {
            // 미지원 형식 응답이 원본 Stream 정리 실패에 의해 달라지지 않게 합니다.
        }
    }
}
