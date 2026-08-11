package com.fowoco.server.workerlink.infrastructure.sms;

import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.worker-link")
public record WorkerLinkDeliveryProperties(
        URI portalBaseUrl,
        Sms sms
) {
    public WorkerLinkDeliveryProperties {
        portalBaseUrl = requireSafeUri(portalBaseUrl, "portalBaseUrl");
        sms = Objects.requireNonNull(sms, "sms must not be null");
    }

    public record Sms(
            String provider,
            URI endpoint,
            String apiKey,
            String apiSecret,
            String senderNumber,
            Duration connectTimeout,
            Duration overallTimeout,
            int maxResponseBytes
    ) {
        public Sms {
            provider = requireText(provider, "provider").toLowerCase(Locale.ROOT);
            endpoint = requireSafeUri(endpoint, "endpoint");
            connectTimeout = requirePositive(connectTimeout, "connectTimeout");
            overallTimeout = requirePositive(overallTimeout, "overallTimeout");
            if (maxResponseBytes < 1 || maxResponseBytes > 1_048_576) {
                throw new IllegalArgumentException("maxResponseBytes must be between 1 and 1048576");
            }
        }
    }

    private static URI requireSafeUri(URI value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (!value.isAbsolute() || value.getHost() == null || value.getUserInfo() != null
                || value.getQuery() != null || value.getFragment() != null) {
            throw new IllegalArgumentException(fieldName + " must be an absolute origin URI");
        }
        boolean https = "https".equalsIgnoreCase(value.getScheme());
        boolean localHttp = "http".equalsIgnoreCase(value.getScheme()) && isLoopback(value.getHost());
        if (!https && !localHttp) {
            throw new IllegalArgumentException(fieldName + " must use HTTPS outside local development");
        }
        return value;
    }

    private static boolean isLoopback(String host) {
        return "localhost".equalsIgnoreCase(host)
                || "127.0.0.1".equals(host)
                || "::1".equals(host)
                || "[::1]".equals(host);
    }

    private static Duration requirePositive(Duration value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return value;
    }

    static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
