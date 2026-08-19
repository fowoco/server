package com.fowoco.server.aiintegration.infrastructure.http;

import com.fowoco.server.aiintegration.application.port.AiRuntimeDeadlinePolicy;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai-runtime")
public final class AiRuntimeProperties implements AiRuntimeDeadlinePolicy {

    private static final int MIN_RESPONSE_BYTES = 1_024;
    private static final int MAX_RESPONSE_BYTES = 10 * 1_024 * 1_024;
    private static final Duration MAX_OVERALL_TIMEOUT = Duration.ofMinutes(5);
    private static final int MAX_DOCUMENT_RESPONSE_BYTES = 20 * 1_024 * 1_024;

    private boolean enabled;
    private URI endpoint = URI.create("http://127.0.0.1:8000/internal/v1/analyses");
    private URI renewalEndpoint = URI.create("http://127.0.0.1:8000/internal/v1/workflows/renewal/run");
    private URI documentGenerationEndpoint = URI.create("http://127.0.0.1:8000/api/v1/documents/generate");
    private URI documentConversionEndpoint = URI.create("http://127.0.0.1:8000/api/v1/documents/convert");
    private String serviceCredential;
    private Duration connectTimeout = Duration.ofSeconds(2);
    private Duration overallTimeout = Duration.ofMinutes(5);
    private Duration documentConversionTimeout = Duration.ofSeconds(60);
    private int maxResponseBytes = 1_048_576;
    private int maxDocumentResponseBytes = MAX_DOCUMENT_RESPONSE_BYTES;
    private int maxConcurrentCalls = 8;
    private int circuitBreakerFailureThreshold = 5;
    private Duration circuitBreakerOpenDuration = Duration.ofSeconds(30);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public URI getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(URI endpoint) {
        this.endpoint = requireHttpEndpoint(endpoint);
    }

    public URI getRenewalEndpoint() {
        return renewalEndpoint;
    }

    public void setRenewalEndpoint(URI renewalEndpoint) {
        this.renewalEndpoint = requireHttpEndpoint(renewalEndpoint);
    }

    public URI getDocumentGenerationEndpoint() {
        return documentGenerationEndpoint;
    }

    public void setDocumentGenerationEndpoint(URI documentGenerationEndpoint) {
        this.documentGenerationEndpoint = requireHttpEndpoint(documentGenerationEndpoint);
    }

    public URI getDocumentConversionEndpoint() {
        return documentConversionEndpoint;
    }

    public void setDocumentConversionEndpoint(URI documentConversionEndpoint) {
        this.documentConversionEndpoint = requireHttpEndpoint(documentConversionEndpoint);
    }

    public void setServiceCredential(String serviceCredential) {
        this.serviceCredential = serviceCredential;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = requirePositive(connectTimeout, "connectTimeout");
    }

    public Duration getOverallTimeout() {
        return overallTimeout;
    }

    public void setOverallTimeout(Duration overallTimeout) {
        Duration validated = requirePositive(overallTimeout, "overallTimeout");
        if (validated.compareTo(MAX_OVERALL_TIMEOUT) > 0) {
            throw new IllegalArgumentException("overallTimeout must not exceed 5m");
        }
        this.overallTimeout = validated;
    }

    @Override
    public long attemptDeadlineMs() {
        return overallTimeout.toMillis();
    }

    public int getMaxResponseBytes() {
        return maxResponseBytes;
    }

    public Duration getDocumentConversionTimeout() {
        return documentConversionTimeout;
    }

    public void setDocumentConversionTimeout(Duration documentConversionTimeout) {
        Duration validated = requirePositive(documentConversionTimeout, "documentConversionTimeout");
        if (validated.compareTo(MAX_OVERALL_TIMEOUT) > 0) {
            throw new IllegalArgumentException("documentConversionTimeout must not exceed 5m");
        }
        this.documentConversionTimeout = validated;
    }

    public void setMaxResponseBytes(int maxResponseBytes) {
        if (maxResponseBytes < MIN_RESPONSE_BYTES || maxResponseBytes > MAX_RESPONSE_BYTES) {
            throw new IllegalArgumentException("maxResponseBytes must be between 1 KiB and 10 MiB");
        }
        this.maxResponseBytes = maxResponseBytes;
    }

    public int getMaxDocumentResponseBytes() {
        return maxDocumentResponseBytes;
    }

    public void setMaxDocumentResponseBytes(int maxDocumentResponseBytes) {
        if (maxDocumentResponseBytes < MIN_RESPONSE_BYTES
                || maxDocumentResponseBytes > MAX_DOCUMENT_RESPONSE_BYTES) {
            throw new IllegalArgumentException("maxDocumentResponseBytes must be between 1 KiB and 20 MiB");
        }
        this.maxDocumentResponseBytes = maxDocumentResponseBytes;
    }

    public int getMaxConcurrentCalls() {
        return maxConcurrentCalls;
    }

    public void setMaxConcurrentCalls(int maxConcurrentCalls) {
        if (maxConcurrentCalls < 1 || maxConcurrentCalls > 100) {
            throw new IllegalArgumentException("maxConcurrentCalls must be between 1 and 100");
        }
        this.maxConcurrentCalls = maxConcurrentCalls;
    }

    public int getCircuitBreakerFailureThreshold() {
        return circuitBreakerFailureThreshold;
    }

    public void setCircuitBreakerFailureThreshold(int circuitBreakerFailureThreshold) {
        if (circuitBreakerFailureThreshold < 1 || circuitBreakerFailureThreshold > 100) {
            throw new IllegalArgumentException("circuitBreakerFailureThreshold must be between 1 and 100");
        }
        this.circuitBreakerFailureThreshold = circuitBreakerFailureThreshold;
    }

    public Duration getCircuitBreakerOpenDuration() {
        return circuitBreakerOpenDuration;
    }

    public void setCircuitBreakerOpenDuration(Duration circuitBreakerOpenDuration) {
        this.circuitBreakerOpenDuration = requirePositive(
                circuitBreakerOpenDuration,
                "circuitBreakerOpenDuration"
        );
    }

    String authorizationHeader() {
        if (serviceCredential == null || serviceCredential.isBlank()) {
            throw new IllegalStateException(
                    "AI_RUNTIME_SERVICE_CREDENTIAL must be configured when AI Runtime is enabled"
            );
        }
        if (serviceCredential.indexOf('\r') >= 0 || serviceCredential.indexOf('\n') >= 0) {
            throw new IllegalStateException("AI Runtime service credential contains an invalid character");
        }
        return "Bearer " + serviceCredential.trim();
    }

    void validateEnabledConfiguration() {
        requireHttpEndpoint(endpoint);
        requireHttpEndpoint(renewalEndpoint);
        requireHttpEndpoint(documentGenerationEndpoint);
        requireHttpEndpoint(documentConversionEndpoint);
        authorizationHeader();
        requirePositive(connectTimeout, "connectTimeout");
        requirePositive(overallTimeout, "overallTimeout");
        requirePositive(documentConversionTimeout, "documentConversionTimeout");
    }

    private static URI requireHttpEndpoint(URI value) {
        if (value == null
                || !value.isAbsolute()
                || (!"http".equalsIgnoreCase(value.getScheme())
                && !"https".equalsIgnoreCase(value.getScheme()))
                || value.getHost() == null
                || value.getUserInfo() != null
                || value.getQuery() != null
                || value.getFragment() != null) {
            throw new IllegalArgumentException("endpoint must be an absolute HTTP(S) URI without credentials or query");
        }
        return value;
    }

    private static Duration requirePositive(Duration value, String field) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        if (value.compareTo(Duration.ofMillis(100)) < 0) {
            throw new IllegalArgumentException(field + " must be at least 100ms");
        }
        return value;
    }
}
