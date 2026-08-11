package com.fowoco.server.aiintegration.infrastructure.http;

import com.fowoco.server.aiintegration.application.error.AiRuntimeCallException;
import com.fowoco.server.aiintegration.application.error.AiRuntimeFailureCode;
import com.fowoco.server.aiintegration.application.model.AiRuntimeCallContext;
import com.fowoco.server.aiintegration.application.port.RenewalRuntimeClient;
import com.fowoco.server.aiintegration.application.renewal.RenewalRunRequest;
import com.fowoco.server.aiintegration.application.renewal.RenewalRunResponse;
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

/** One transport attempt against the Agent-owned Renewal endpoint. */
public final class RemoteRenewalRuntimeClient implements RenewalRuntimeClient {

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

    RemoteRenewalRuntimeClient(
            URI endpoint,
            String authorizationHeader,
            Duration overallTimeout,
            int maxResponseBytes,
            int maxConcurrentCalls,
            HttpClient httpClient,
            ObjectMapper objectMapper,
            AiRuntimeCircuitBreaker circuitBreaker
    ) {
        this.endpoint = Objects.requireNonNull(endpoint);
        this.authorizationHeader = requireText(authorizationHeader, "authorizationHeader");
        this.overallTimeout = requirePositive(overallTimeout, "overallTimeout");
        if (maxResponseBytes < 1 || maxConcurrentCalls < 1) {
            throw new IllegalArgumentException("Renewal transport limits must be positive");
        }
        this.maxResponseBytes = maxResponseBytes;
        this.httpClient = Objects.requireNonNull(httpClient);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.bulkhead = new Semaphore(maxConcurrentCalls);
        this.circuitBreaker = Objects.requireNonNull(circuitBreaker);
    }

    @Override
    public RenewalRunResponse run(RenewalRunRequest request, AiRuntimeCallContext context) {
        Objects.requireNonNull(request);
        Objects.requireNonNull(context);
        if (!bulkhead.tryAcquire()) {
            throw failure(AiRuntimeFailureCode.BULKHEAD_FULL, "AI Renewal concurrency limit is full.");
        }
        boolean circuitPermit = false;
        try {
            circuitBreaker.beforeCall();
            circuitPermit = true;
            byte[] body = serialize(request);
            HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint)
                    .timeout(overallTimeout)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", authorizationHeader)
                    .header("X-Request-Id", request.requestId().toString())
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body));
            if (context.traceParent() != null) {
                builder.header("traceparent", context.traceParent());
            }
            RenewalRunResponse response = decode(execute(builder.build()));
            circuitBreaker.recordSuccess();
            return response;
        } catch (AiRuntimeCallException exception) {
            if (circuitPermit) {
                if (CIRCUIT_FAILURES.contains(exception.failureCode())) {
                    circuitBreaker.recordFailure();
                } else {
                    circuitBreaker.recordSuccess();
                }
            }
            throw exception;
        } catch (RuntimeException exception) {
            if (circuitPermit) {
                circuitBreaker.recordFailure();
            }
            throw new AiRuntimeCallException(
                    AiRuntimeFailureCode.TRANSPORT_FAILURE,
                    "AI Renewal transport failed.",
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
            throw failure(AiRuntimeFailureCode.DEADLINE_EXCEEDED, "AI Renewal deadline was exceeded.", exception);
        } catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw failure(AiRuntimeFailureCode.TRANSPORT_FAILURE, "AI Renewal call was interrupted.", exception);
        } catch (ExecutionException exception) {
            throw classifyExecutionFailure(exception.getCause());
        }
    }

    private RenewalRunResponse decode(HttpResponse<byte[]> response) {
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw classifyStatus(status);
        }
        try {
            return objectMapper.readValue(response.body(), RenewalRunResponse.class);
        } catch (JacksonException exception) {
            throw failure(
                    AiRuntimeFailureCode.RESPONSE_PARSING_FAILED,
                    "AI Renewal response JSON is invalid.",
                    exception
            );
        }
    }

    private byte[] serialize(RenewalRunRequest request) {
        try {
            return objectMapper.writeValueAsBytes(request);
        } catch (JacksonException exception) {
            throw failure(
                    AiRuntimeFailureCode.INVALID_REQUEST_CONTRACT,
                    "AI Renewal request JSON could not be created.",
                    exception
            );
        }
    }

    private AiRuntimeCallException classifyExecutionFailure(Throwable cause) {
        Throwable failure = unwrap(cause);
        if (failure instanceof HttpTimeoutException || failure instanceof TimeoutException) {
            return failure(AiRuntimeFailureCode.DEADLINE_EXCEEDED, "AI Renewal deadline was exceeded.", failure);
        }
        if (failure instanceof LimitedByteArrayBodyHandler.ResponseTooLargeException) {
            return failure(AiRuntimeFailureCode.RESPONSE_TOO_LARGE, "AI Renewal response is too large.", failure);
        }
        return failure(AiRuntimeFailureCode.TRANSPORT_FAILURE, "AI Renewal transport failed.", failure);
    }

    private AiRuntimeCallException classifyStatus(int status) {
        if (status == 408) {
            return failure(AiRuntimeFailureCode.DEADLINE_EXCEEDED, "AI Renewal deadline was exceeded.");
        }
        if (status == 401 || status == 403) {
            return failure(AiRuntimeFailureCode.AUTHENTICATION_FAILED, "AI Renewal authentication failed.");
        }
        if (status == 429) {
            return failure(AiRuntimeFailureCode.RATE_LIMITED, "AI Renewal rate limit was reached.");
        }
        if (status >= 500) {
            return failure(AiRuntimeFailureCode.RUNTIME_UNAVAILABLE, "AI Renewal is unavailable.");
        }
        return failure(AiRuntimeFailureCode.INVALID_REQUEST_CONTRACT, "AI Renewal rejected the request contract.");
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
