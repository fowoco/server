package com.fowoco.server.aiintegration.infrastructure.http;

import com.fowoco.server.aiintegration.application.document.DocumentGenerationClient;
import com.fowoco.server.aiintegration.application.document.DocumentGenerationRequest;
import com.fowoco.server.aiintegration.application.document.GeneratedDocumentFile;
import com.fowoco.server.aiintegration.application.error.AiRuntimeCallException;
import com.fowoco.server.aiintegration.application.error.AiRuntimeFailureCode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.http.ContentDisposition;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** One transport attempt against the Agent-owned document generation endpoint. */
final class RemoteDocumentGenerationClient implements DocumentGenerationClient {

    private final URI endpoint;
    private final String authorizationHeader;
    private final Duration overallTimeout;
    private final int maxResponseBytes;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    RemoteDocumentGenerationClient(
            URI endpoint,
            String authorizationHeader,
            Duration overallTimeout,
            int maxResponseBytes,
            HttpClient httpClient,
            ObjectMapper objectMapper
    ) {
        this.endpoint = Objects.requireNonNull(endpoint);
        this.authorizationHeader = requireText(authorizationHeader, "authorizationHeader");
        this.overallTimeout = requirePositive(overallTimeout, "overallTimeout");
        if (maxResponseBytes < 1) {
            throw new IllegalArgumentException("maxResponseBytes must be positive");
        }
        this.maxResponseBytes = maxResponseBytes;
        this.httpClient = Objects.requireNonNull(httpClient);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    @Override
    public GeneratedDocumentFile generate(DocumentGenerationRequest request) {
        Objects.requireNonNull(request);
        try {
            String boundary = "fowoco-" + UUID.randomUUID();
            HttpRequest httpRequest = HttpRequest.newBuilder(endpoint)
                    .timeout(overallTimeout)
                    .header("Authorization", authorizationHeader)
                    .header(
                            "Accept",
                            "application/octet-stream, application/vnd.hancom.hwp, application/vnd.hancom.hwpx"
                    )
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(multipart(request, boundary)))
                    .build();
            HttpResponse<byte[]> response = execute(httpRequest);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw classifyStatus(response.statusCode());
            }
            if (response.body().length == 0) {
                throw failure(AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT, "Generated document is empty.");
            }
            response.headers().firstValue("X-Document-Template-Id").ifPresent(templateId -> {
                if (!request.templateId().equals(templateId)) {
                    throw failure(
                            AiRuntimeFailureCode.CORE_VALUE_MISMATCH,
                            "Generated document template does not match the request."
                    );
                }
            });
            String fileName = response.headers().firstValue("Content-Disposition")
                    .map(this::fileName)
                    .filter(value -> hasExpectedExtension(value, request.format()))
                    .orElse(request.templateId() + "." + request.format());
            return new GeneratedDocumentFile(fileName, request.format(), response.body());
        } catch (AiRuntimeCallException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw failure(
                    AiRuntimeFailureCode.TRANSPORT_FAILURE,
                    "Document generation transport failed.",
                    exception
            );
        }
    }

    private byte[] multipart(DocumentGenerationRequest request, String boundary) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("template_id", request.templateId());
        payload.put("format", request.format());
        payload.put("values", request.values());
        try {
            String json = objectMapper.writeValueAsString(payload);
            return ("--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"payload\"\r\n"
                    + "Content-Type: application/json; charset=UTF-8\r\n\r\n"
                    + json + "\r\n"
                    + "--" + boundary + "--\r\n")
                    .getBytes(StandardCharsets.UTF_8);
        } catch (JacksonException exception) {
            throw failure(
                    AiRuntimeFailureCode.INVALID_REQUEST_CONTRACT,
                    "Document generation request could not be created.",
                    exception
            );
        }
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
            throw failure(AiRuntimeFailureCode.DEADLINE_EXCEEDED, "Document generation timed out.", exception);
        } catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw failure(AiRuntimeFailureCode.TRANSPORT_FAILURE, "Document generation was interrupted.", exception);
        } catch (ExecutionException exception) {
            Throwable cause = unwrap(exception.getCause());
            if (cause instanceof HttpTimeoutException || cause instanceof TimeoutException) {
                throw failure(AiRuntimeFailureCode.DEADLINE_EXCEEDED, "Document generation timed out.", cause);
            }
            if (cause instanceof LimitedByteArrayBodyHandler.ResponseTooLargeException) {
                throw failure(AiRuntimeFailureCode.RESPONSE_TOO_LARGE, "Generated document is too large.", cause);
            }
            throw failure(AiRuntimeFailureCode.TRANSPORT_FAILURE, "Document generation transport failed.", cause);
        }
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

    private AiRuntimeCallException classifyStatus(int status) {
        if (status == 401 || status == 403) {
            return failure(AiRuntimeFailureCode.AUTHENTICATION_FAILED, "Document generation authentication failed.");
        }
        if (status == 408) {
            return failure(AiRuntimeFailureCode.DEADLINE_EXCEEDED, "Document generation timed out.");
        }
        if (status == 429) {
            return failure(AiRuntimeFailureCode.RATE_LIMITED, "Document generation rate limit was reached.");
        }
        if (status >= 500) {
            return failure(AiRuntimeFailureCode.RUNTIME_UNAVAILABLE, "Document generation is unavailable.");
        }
        return failure(AiRuntimeFailureCode.INVALID_REQUEST_CONTRACT, "Document generation request was rejected.");
    }

    private String fileName(String header) {
        try {
            String parsed = ContentDisposition.parse(header).getFilename();
            if (parsed == null) {
                return "";
            }
            String normalized = parsed.replace('\\', '/');
            return normalized.substring(normalized.lastIndexOf('/') + 1);
        } catch (IllegalArgumentException exception) {
            return "";
        }
    }

    private boolean hasExpectedExtension(String fileName, String format) {
        return fileName.toLowerCase(java.util.Locale.ROOT).endsWith("." + format);
    }

    private AiRuntimeCallException failure(AiRuntimeFailureCode code, String message) {
        return new AiRuntimeCallException(code, message);
    }

    private AiRuntimeCallException failure(AiRuntimeFailureCode code, String message, Throwable cause) {
        return new AiRuntimeCallException(code, message, cause);
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
