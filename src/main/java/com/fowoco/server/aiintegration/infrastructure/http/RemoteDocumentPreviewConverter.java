package com.fowoco.server.aiintegration.infrastructure.http;

import com.fowoco.server.file.application.DocumentPreviewConversionException;
import com.fowoco.server.file.application.DocumentPreviewSource;
import com.fowoco.server.file.application.port.DocumentPreviewConverter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** HWP·HWPX 원본을 Agent 문서 변환 API에 보내 PDF 미리보기로 변환합니다. */
final class RemoteDocumentPreviewConverter implements DocumentPreviewConverter {

    private static final byte[] PDF_SIGNATURE = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    private final URI endpoint;
    private final String authorizationHeader;
    private final Duration overallTimeout;
    private final int maxResponseBytes;
    private final HttpClient httpClient;

    RemoteDocumentPreviewConverter(
            URI endpoint,
            String authorizationHeader,
            Duration overallTimeout,
            int maxResponseBytes,
            HttpClient httpClient
    ) {
        this.endpoint = Objects.requireNonNull(endpoint);
        this.authorizationHeader = requireText(authorizationHeader, "authorizationHeader");
        this.overallTimeout = requirePositive(overallTimeout, "overallTimeout");
        if (maxResponseBytes < 1) {
            throw new IllegalArgumentException("maxResponseBytes must be positive");
        }
        this.maxResponseBytes = maxResponseBytes;
        this.httpClient = Objects.requireNonNull(httpClient);
    }

    @Override
    public byte[] convertToPdf(DocumentPreviewSource source) {
        Objects.requireNonNull(source);
        try {
            String boundary = "fowoco-preview-" + UUID.randomUUID();
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(overallTimeout)
                    .header("Authorization", authorizationHeader)
                    .header("Accept", "application/pdf")
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(multipart(source, boundary))
                    .build();
            HttpResponse<byte[]> response = execute(request);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw classifyStatus(response.statusCode());
            }
            if (!isPdf(response.body())) {
                throw invalid("Document preview response is not a PDF.");
            }
            return response.body();
        } catch (DocumentPreviewConversionException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw unavailable("Document preview transport failed.", exception);
        }
    }

    private HttpRequest.BodyPublisher multipart(DocumentPreviewSource source, String boundary) {
        byte[] preamble = ("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\""
                + safeFileName(source.fileName()) + "\"\r\n"
                + "Content-Type: " + safeMimeType(source.mimeType()) + "\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8);
        byte[] epilogue = ("\r\n--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"target_format\"\r\n\r\n"
                + "pdf\r\n--" + boundary + "--\r\n")
                .getBytes(StandardCharsets.UTF_8);
        return HttpRequest.BodyPublishers.concat(
                HttpRequest.BodyPublishers.ofByteArray(preamble),
                HttpRequest.BodyPublishers.ofByteArray(source.content()),
                HttpRequest.BodyPublishers.ofByteArray(epilogue)
        );
    }

    private HttpResponse<byte[]> execute(HttpRequest request) {
        CompletableFuture<HttpResponse<byte[]>> future = httpClient.sendAsync(
                request,
                new LimitedByteArrayBodyHandler(maxResponseBytes)
        );
        try {
            return future.get(overallTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw unavailable("Document preview conversion timed out.", exception);
        } catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw unavailable("Document preview conversion was interrupted.", exception);
        } catch (ExecutionException exception) {
            Throwable cause = unwrap(exception.getCause());
            if (cause instanceof HttpTimeoutException || cause instanceof TimeoutException) {
                throw unavailable("Document preview conversion timed out.", cause);
            }
            if (cause instanceof LimitedByteArrayBodyHandler.ResponseTooLargeException) {
                throw unavailable("Document preview response is too large.", cause);
            }
            throw unavailable("Document preview transport failed.", cause);
        }
    }

    private DocumentPreviewConversionException classifyStatus(int status) {
        if (status == 400 || status == 404 || status == 409 || status == 413 || status == 415 || status == 422) {
            return invalid("Document preview request was rejected.");
        }
        return unavailable("Document preview conversion is unavailable.");
    }

    private boolean isPdf(byte[] content) {
        if (content.length < PDF_SIGNATURE.length) {
            return false;
        }
        for (int index = 0; index < PDF_SIGNATURE.length; index++) {
            if (content[index] != PDF_SIGNATURE[index]) {
                return false;
            }
        }
        return true;
    }

    private String safeFileName(String fileName) {
        String baseName = fileName.replace('\\', '/');
        baseName = baseName.substring(baseName.lastIndexOf('/') + 1);
        return baseName.replace("\r", "").replace("\n", "").replace("\"", "");
    }

    private String safeMimeType(String mimeType) {
        String normalized = mimeType.replace("\r", "").replace("\n", "").strip();
        return normalized.isBlank() ? "application/octet-stream" : normalized;
    }

    private Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof ExecutionException
                || current instanceof java.util.concurrent.CompletionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private DocumentPreviewConversionException invalid(String message) {
        return new DocumentPreviewConversionException(
                DocumentPreviewConversionException.Reason.INVALID_DOCUMENT,
                message
        );
    }

    private DocumentPreviewConversionException unavailable(String message) {
        return new DocumentPreviewConversionException(
                DocumentPreviewConversionException.Reason.UNAVAILABLE,
                message
        );
    }

    private DocumentPreviewConversionException unavailable(String message, Throwable cause) {
        return new DocumentPreviewConversionException(
                DocumentPreviewConversionException.Reason.UNAVAILABLE,
                message,
                cause
        );
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private static Duration requirePositive(Duration value, String field) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }
}
