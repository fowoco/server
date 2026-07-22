package com.fowoco.server.auth.api;

import java.time.Duration;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
final class RefreshTokenCookieFactory {

    private static final Set<String> ALLOWED_SAME_SITE_VALUES = Set.of("Strict", "Lax", "None");

    private final String cookieName;
    private final String cookiePath;
    private final String sameSite;
    private final boolean secure;
    private final Duration maxAge;

    RefreshTokenCookieFactory(
            @Value("${app.auth.refresh-token.cookie.name}") String cookieName,
            @Value("${app.auth.refresh-token.cookie.path}") String cookiePath,
            @Value("${app.auth.refresh-token.cookie.same-site}") String sameSite,
            @Value("${app.auth.refresh-token.cookie.secure}") boolean secure,
            @Value("${app.auth.refresh-token.ttl}") Duration maxAge,
            Environment environment
    ) {
        if (cookieName == null || cookieName.isBlank()) {
            throw new IllegalArgumentException("refresh token cookie name must not be blank");
        }
        if (cookiePath == null || !cookiePath.startsWith("/")) {
            throw new IllegalArgumentException("refresh token cookie path must start with '/'");
        }
        if (!ALLOWED_SAME_SITE_VALUES.contains(sameSite)) {
            throw new IllegalArgumentException("refresh token SameSite must be Strict, Lax, or None");
        }
        if (maxAge == null || maxAge.isZero() || maxAge.isNegative()) {
            throw new IllegalArgumentException("refresh token cookie max age must be positive");
        }
        if ("None".equals(sameSite) && !secure) {
            throw new IllegalArgumentException("SameSite=None requires a Secure cookie");
        }
        if (environment.matchesProfiles("prod") && !secure) {
            throw new IllegalArgumentException("production refresh token cookies must be Secure");
        }
        this.cookieName = cookieName;
        this.cookiePath = cookiePath;
        this.sameSite = sameSite;
        this.secure = secure;
        this.maxAge = maxAge;
    }

    ResponseCookie create(String rawRefreshToken) {
        return ResponseCookie.from(cookieName, rawRefreshToken)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path(cookiePath)
                .maxAge(maxAge)
                .build();
    }
}
