package com.fowoco.server.aiintegration.infrastructure.http;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai-runtime")
public final class AiRuntimeProperties {

    private static final int MIN_RESPONSE_BYTES = 1_024;
    private static final int MAX_RESPONSE_BYTES = 10 * 1_024 * 1_024;

    private boolean enabled;
    private URI endpoint = URI.create("http://127.0.0.1:8000/internal/v1/analyses");
    private String serviceCredential;
    private Duration connectTimeout = Duration.ofSeconds(2);
    private Duration overallTimeout = Duration.ofSeconds(15);
    private int maxResponseBytes = 1_048_576;
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
        this.overallTimeout = requirePositive(overallTimeout, "overallTimeout");
    }

    public int getMaxResponseBytes() {
        return maxResponseBytes;
    }

    public void setMaxResponseBytes(int maxResponseBytes) {
        if (maxResponseBytes < MIN_RESPONSE_BYTES || maxResponseBytes > MAX_RESPONSE_BYTES) {
            throw new IllegalArgumentException("maxResponseBytes must be between 1 KiB and 10 MiB");
        }
        this.maxResponseBytes = maxResponseBytes;
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
        authorizationHeader();
        requirePositive(connectTimeout, "connectTimeout");
        requirePositive(overallTimeout, "overallTimeout");
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
