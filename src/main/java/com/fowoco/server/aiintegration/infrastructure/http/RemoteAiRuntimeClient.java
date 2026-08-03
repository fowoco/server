package com.fowoco.server.aiintegration.infrastructure.http;

import com.fowoco.server.aiintegration.application.error.AiRuntimeCallException;
import com.fowoco.server.aiintegration.application.error.AiRuntimeFailureCode;
import com.fowoco.server.aiintegration.application.model.AiAnalysisRequest;
import com.fowoco.server.aiintegration.application.model.AiAnalysisResponse;
import com.fowoco.server.aiintegration.application.model.AiRuntimeCallContext;
import com.fowoco.server.aiintegration.application.port.AiRuntimeClient;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Exactly-once transport attempt against the separately deployed fowoco/ai Runtime.
 */
public final class RemoteAiRuntimeClient implements AiRuntimeClient {

    private static final String AUTHORIZATION = "Authorization";
    private static final String REQUEST_ID = "X-Request-Id";
    private static final String TRACEPARENT = "traceparent";
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

    RemoteAiRuntimeClient(
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
        this.authorizationHeader = requireText(authorizationHeader, "authorizationHeader");
        this.overallTimeout = requirePositive(overallTimeout, "overallTimeout");
        if (maxResponseBytes < 1) {
            throw new IllegalArgumentException("maxResponseBytes must be positive");
        }
        if (maxConcurrentCalls < 1) {
            throw new IllegalArgumentException("maxConcurrentCalls must be positive");
        }
        this.maxResponseBytes = maxResponseBytes;
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.bulkhead = new Semaphore(maxConcurrentCalls);
        this.circuitBreaker = Objects.requireNonNull(circuitBreaker, "circuitBreaker must not be null");
    }

    @Override
    public AiAnalysisResponse analyze(AiAnalysisRequest request, AiRuntimeCallContext context) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(context, "context must not be null");
        long startedNanos = System.nanoTime();
        if (!bulkhead.tryAcquire()) {
            throw new AiRuntimeCallException(
                    AiRuntimeFailureCode.BULKHEAD_FULL,
                    "AI Runtime concurrency limit is full."
            );
        }

        boolean circuitPermitAcquired = false;
        try {
            circuitBreaker.beforeCall();
            circuitPermitAcquired = true;

            long remainingMillis = remainingMillis(request, startedNanos);
            AiAnalysisRequest outboundRequest = withRemainingDeadline(request, remainingMillis);
            byte[] requestBody = serialize(outboundRequest);
            remainingMillis = remainingMillis(request, startedNanos);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofMillis(remainingMillis))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header(AUTHORIZATION, authorizationHeader)
                    .header(REQUEST_ID, request.requestId().toString())
                    .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody));
            if (context.traceParent() != null) {
                requestBuilder.header(TRACEPARENT, context.traceParent());
            }

            HttpResponse<byte[]> response = execute(requestBuilder.build(), remainingMillis);
            AiAnalysisResponse result = decodeResponse(response);
            remainingMillis(request, startedNanos);
            circuitBreaker.recordSuccess();
            return result;
        } catch (AiRuntimeCallException exception) {
            if (circuitPermitAcquired) {
                if (CIRCUIT_FAILURES.contains(exception.failureCode())) {
                    circuitBreaker.recordFailure();
                } else {
                    circuitBreaker.recordSuccess();
                }
            }
            throw exception;
        } catch (RuntimeException exception) {
            if (circuitPermitAcquired) {
                circuitBreaker.recordFailure();
            }
            throw new AiRuntimeCallException(
                    AiRuntimeFailureCode.TRANSPORT_FAILURE,
                    "AI Runtime transport failed.",
                    exception
            );
        } finally {
            bulkhead.release();
        }
    }

    private HttpResponse<byte[]> execute(HttpRequest request, long timeoutMillis) {
        CompletableFuture<HttpResponse<byte[]>> future = httpClient.sendAsync(
                request,
                new LimitedByteArrayBodyHandler(maxResponseBytes)
        );
        try {
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw new AiRuntimeCallException(
                    AiRuntimeFailureCode.DEADLINE_EXCEEDED,
                    "AI Runtime deadline was exceeded.",
                    exception
            );
        } catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new AiRuntimeCallException(
                    AiRuntimeFailureCode.TRANSPORT_FAILURE,
                    "AI Runtime call was interrupted.",
                    exception
            );
        } catch (ExecutionException exception) {
            throw classifyExecutionFailure(exception.getCause());
        }
    }

    private AiAnalysisResponse decodeResponse(HttpResponse<byte[]> response) {
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw classifyStatus(status);
        }
        try {
            return objectMapper.readValue(response.body(), AiAnalysisResponse.class);
        } catch (JacksonException exception) {
            throw new AiRuntimeCallException(
                    AiRuntimeFailureCode.RESPONSE_PARSING_FAILED,
                    "AI Runtime response JSON is invalid.",
                    exception
            );
        }
    }

    private byte[] serialize(AiAnalysisRequest request) {
        try {
            return objectMapper.writeValueAsBytes(request);
        } catch (JacksonException exception) {
            throw new AiRuntimeCallException(
                    AiRuntimeFailureCode.INVALID_REQUEST_CONTRACT,
                    "AI Runtime request JSON could not be created.",
                    exception
            );
        }
    }

    private AiRuntimeCallException classifyExecutionFailure(Throwable cause) {
        Throwable failure = unwrap(cause);
        if (failure instanceof HttpTimeoutException
                || failure instanceof java.util.concurrent.TimeoutException) {
            return new AiRuntimeCallException(
                    AiRuntimeFailureCode.DEADLINE_EXCEEDED,
                    "AI Runtime deadline was exceeded.",
                    failure
            );
        }
        if (failure instanceof LimitedByteArrayBodyHandler.ResponseTooLargeException) {
            return new AiRuntimeCallException(
                    AiRuntimeFailureCode.RESPONSE_TOO_LARGE,
                    "AI Runtime response exceeded the configured size limit.",
                    failure
            );
        }
        return new AiRuntimeCallException(
                AiRuntimeFailureCode.TRANSPORT_FAILURE,
                "AI Runtime transport failed.",
                failure
        );
    }

    private AiRuntimeCallException classifyStatus(int status) {
        if (status == 408) {
            return new AiRuntimeCallException(
                    AiRuntimeFailureCode.DEADLINE_EXCEEDED,
                    "AI Runtime deadline was exceeded."
            );
        }
        if (status == 401 || status == 403) {
            return new AiRuntimeCallException(
                    AiRuntimeFailureCode.AUTHENTICATION_FAILED,
                    "AI Runtime service authentication failed."
            );
        }
        if (status == 429) {
            return new AiRuntimeCallException(
                    AiRuntimeFailureCode.RATE_LIMITED,
                    "AI Runtime rate limit was reached."
            );
        }
        if (status >= 500) {
            return new AiRuntimeCallException(
                    AiRuntimeFailureCode.RUNTIME_UNAVAILABLE,
                    "AI Runtime is unavailable."
            );
        }
        return new AiRuntimeCallException(
                AiRuntimeFailureCode.INVALID_REQUEST_CONTRACT,
                "AI Runtime rejected the request contract."
        );
    }

    private long remainingMillis(AiAnalysisRequest request, long startedNanos) {
        long configuredMillis = Math.min(request.deadlineMs(), overallTimeout.toMillis());
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
        long remainingMillis = configuredMillis - elapsedMillis;
        if (remainingMillis < 100) {
            throw new AiRuntimeCallException(
                    AiRuntimeFailureCode.DEADLINE_EXCEEDED,
                    "AI Runtime deadline was exceeded."
            );
        }
        return remainingMillis;
    }

    private AiAnalysisRequest withRemainingDeadline(AiAnalysisRequest request, long remainingMillis) {
        return new AiAnalysisRequest(
                request.requestId(),
                request.attemptId(),
                request.phase(),
                request.contractVersion(),
                request.requiredKnowledgeVersion(),
                remainingMillis,
                request.analysisInput()
        );
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof ExecutionException
                || current instanceof java.util.concurrent.CompletionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
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
