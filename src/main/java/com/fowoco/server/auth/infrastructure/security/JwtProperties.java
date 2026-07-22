package com.fowoco.server.auth.infrastructure.security;

import java.time.Duration;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth.jwt")
public final class JwtProperties {

    private static final int MINIMUM_SECRET_BYTES = 32;
    private static final Duration MINIMUM_ACCESS_TOKEN_TTL = Duration.ofMinutes(1);
    private static final Duration MAXIMUM_ACCESS_TOKEN_TTL = Duration.ofHours(1);

    private final String issuer;
    private final String audience;
    private final Duration accessTokenTtl;
    private final SecretKey secretKey;

    public JwtProperties(
            String issuer,
            String audience,
            Duration accessTokenTtl,
            String secretBase64
    ) {
        this.issuer = requireText(issuer, "issuer");
        this.audience = requireText(audience, "audience");
        if (accessTokenTtl == null
                || accessTokenTtl.compareTo(MINIMUM_ACCESS_TOKEN_TTL) < 0
                || accessTokenTtl.compareTo(MAXIMUM_ACCESS_TOKEN_TTL) > 0) {
            throw new IllegalArgumentException("accessTokenTtl must be between 1 minute and 1 hour");
        }
        this.accessTokenTtl = accessTokenTtl;
        this.secretKey = decodeSecret(secretBase64);
    }

    public String issuer() {
        return issuer;
    }

    public String audience() {
        return audience;
    }

    public Duration accessTokenTtl() {
        return accessTokenTtl;
    }

    SecretKey secretKey() {
        return secretKey;
    }

    private static SecretKey decodeSecret(String secretBase64) {
        String encodedSecret = requireText(secretBase64, "secretBase64");
        byte[] secretBytes;
        try {
            secretBytes = Base64.getDecoder().decode(encodedSecret);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("secretBase64 must be valid Base64", exception);
        }
        if (secretBytes.length < MINIMUM_SECRET_BYTES) {
            throw new IllegalArgumentException("JWT secret must contain at least 32 bytes");
        }
        return new SecretKeySpec(secretBytes, "HmacSHA256");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
