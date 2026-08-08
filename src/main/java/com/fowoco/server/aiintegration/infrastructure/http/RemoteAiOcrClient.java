package com.fowoco.server.aiintegration.infrastructure.http;

import com.fowoco.server.aiintegration.application.error.AiRuntimeCallException;
import com.fowoco.server.aiintegration.application.error.AiRuntimeFailureCode;
import com.fowoco.server.aiintegration.application.model.AiRuntimeCallContext;
import com.fowoco.server.aiintegration.application.ocr.AiOcrRequest;
import com.fowoco.server.aiintegration.application.ocr.AiOcrResponse;
import com.fowoco.server.aiintegration.application.port.AiOcrClient;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

final class RemoteAiOcrClient implements AiOcrClient {

    private static final Set<AiRuntimeFailureCode> CIRCUIT_FAILURES = EnumSet.of(
            AiRuntimeFailureCode.DEADLINE_EXCEEDED,
            AiRuntimeFailureCode.RATE_LIMITED,
            AiRuntimeFailureCode.RUNTIME_UNAVAILABLE,
            AiRuntimeFailureCode.RESPONSE_TOO_LARGE,
            AiRuntimeFailureCode.RESPONSE_PARSING_FAILED,
            AiRuntimeFailureCode.TRANSPORT_FAILURE
    );

    private final URI endpoint;
    private final String authorizationHeader;
    private final Duration overallTimeout;
    private final int maxResponseBytes;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Semaphore bulkhead;
    private final AiRuntimeCircuitBreaker circuitBreaker;

    RemoteAiOcrClient(
            URI endpoint,
            String authorizationHeader,
            Duration overallTimeout,
            int maxResponseBytes,
            int maxConcurrentCalls,
            HttpClient httpClient,
            ObjectMapper objectMapper,
            AiRuntimeCircuitBreaker circuitBreaker
    ) {
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint must not be null");
        this.authorizationHeader = Objects.requireNonNull(authorizationHeader, "authorizationHeader must not be null");
        this.overallTimeout = Objects.requireNonNull(overallTimeout, "overallTimeout must not be null");
        this.maxResponseBytes = maxResponseBytes;
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.bulkhead = new Semaphore(maxConcurrentCalls);
        this.circuitBreaker = Objects.requireNonNull(circuitBreaker, "circuitBreaker must not be null");
    }

    @Override
    public AiOcrResponse recognize(AiOcrRequest request, AiRuntimeCallContext context) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(context, "context must not be null");
        if (!bulkhead.tryAcquire()) {
            throw failure(AiRuntimeFailureCode.BULKHEAD_FULL, "AI OCR concurrency limit is full.");
        }
        boolean permit = false;
        try {
            circuitBreaker.beforeCall();
            permit = true;
            String boundary = "fowoco-" + UUID.randomUUID();
            URI requestUri = appendPath(endpoint, request.workerDocumentId().toString());
            HttpRequest.Builder builder = HttpRequest.newBuilder(requestUri)
                    .timeout(overallTimeout)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .header("Accept", "application/json")
                    .header("Authorization", authorizationHeader)
                    .header("X-Request-Id", request.requestId().toString())
                    .POST(HttpRequest.BodyPublishers.ofByteArray(multipart(request, boundary)));
            if (context.traceParent() != null) {
                builder.header("traceparent", context.traceParent());
            }
            HttpResponse<byte[]> response = execute(builder.build());
            AiOcrResponse decoded = decode(response);
            circuitBreaker.recordSuccess();
            return decoded;
        } catch (AiRuntimeCallException exception) {
            if (permit) {
                if (CIRCUIT_FAILURES.contains(exception.failureCode())) {
                    circuitBreaker.recordFailure();
                } else {
                    circuitBreaker.recordSuccess();
                }
            }
            throw exception;
        } catch (RuntimeException exception) {
            if (permit) {
                circuitBreaker.recordFailure();
            }
            throw new AiRuntimeCallException(
                    AiRuntimeFailureCode.TRANSPORT_FAILURE,
                    "AI OCR transport failed.",
                    exception
            );
        } finally {
            bulkhead.release();
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
            throw new AiRuntimeCallException(
                    AiRuntimeFailureCode.DEADLINE_EXCEEDED,
                    "AI OCR deadline was exceeded.",
                    exception
            );
        } catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new AiRuntimeCallException(
                    AiRuntimeFailureCode.TRANSPORT_FAILURE,
                    "AI OCR call was interrupted.",
                    exception
            );
        } catch (ExecutionException exception) {
            Throwable cause = unwrap(exception.getCause());
            if (cause instanceof HttpTimeoutException) {
                throw new AiRuntimeCallException(
                        AiRuntimeFailureCode.DEADLINE_EXCEEDED,
                        "AI OCR deadline was exceeded.",
                        cause
                );
            }
            if (cause instanceof LimitedByteArrayBodyHandler.ResponseTooLargeException) {
                throw new AiRuntimeCallException(
                        AiRuntimeFailureCode.RESPONSE_TOO_LARGE,
                        "AI OCR response exceeded the configured size limit.",
                        cause
                );
            }
            throw new AiRuntimeCallException(
                    AiRuntimeFailureCode.TRANSPORT_FAILURE,
                    "AI OCR transport failed.",
                    cause
            );
        }
    }

    private AiOcrResponse decode(HttpResponse<byte[]> response) {
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            if (status == 401 || status == 403) {
                throw failure(AiRuntimeFailureCode.AUTHENTICATION_FAILED, "AI OCR service authentication failed.");
            }
            if (status == 408) {
                throw failure(AiRuntimeFailureCode.DEADLINE_EXCEEDED, "AI OCR deadline was exceeded.");
            }
            if (status == 429) {
                throw failure(AiRuntimeFailureCode.RATE_LIMITED, "AI OCR rate limit was reached.");
            }
            if (status >= 500) {
                throw failure(AiRuntimeFailureCode.RUNTIME_UNAVAILABLE, "AI OCR is unavailable.");
            }
            throw failure(AiRuntimeFailureCode.INVALID_REQUEST_CONTRACT, "AI OCR rejected the request contract.");
        }
        try {
            return objectMapper.readValue(response.body(), AiOcrHttpResponse.class).toDomain();
        } catch (JacksonException exception) {
            throw new AiRuntimeCallException(
                    AiRuntimeFailureCode.RESPONSE_PARSING_FAILED,
                    "AI OCR response JSON is invalid.",
                    exception
            );
        }
    }

    private byte[] multipart(AiOcrRequest request, String boundary) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            textPart(output, boundary, "request_id", request.requestId().toString());
            textPart(output, boundary, "document_type", request.documentType().name());
            if (request.countryCode() != null) {
                textPart(output, boundary, "country_code", request.countryCode());
            }
            output.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            output.write(("Content-Disposition: form-data; name=\"file\"; filename=\""
                    + safeFileName(request.file().fileName()) + "\"\r\n").getBytes(StandardCharsets.UTF_8));
            output.write(("Content-Type: " + request.file().contentType() + "\r\n\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            output.write(request.file().content());
            output.write("\r\n".getBytes(StandardCharsets.UTF_8));
            output.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("AI OCR multipart body creation failed", exception);
        }
    }

    private void textPart(ByteArrayOutputStream output, String boundary, String name, String value)
            throws IOException {
        output.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        output.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        output.write(value.getBytes(StandardCharsets.UTF_8));
        output.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private String safeFileName(String value) {
        return value.replace("\\", "_").replace("\"", "_").replace("\r", "_").replace("\n", "_");
    }

    private URI appendPath(URI base, String segment) {
        String value = base.toString();
        return URI.create((value.endsWith("/") ? value : value + "/") + segment);
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

    private AiRuntimeCallException failure(AiRuntimeFailureCode code, String message) {
        return new AiRuntimeCallException(code, message);
    }
}
