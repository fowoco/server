package com.fowoco.server.auth.infrastructure.security;

import com.fowoco.server.auth.application.RefreshTokenFormat;
import com.fowoco.server.auth.application.port.RefreshTokenGenerator;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public final class SecureRefreshTokenGenerator implements RefreshTokenGenerator {

    private final SecureRandom secureRandom;
    private final RefreshTokenHasher refreshTokenHasher;
    private final RefreshTokenProperties properties;

    public SecureRefreshTokenGenerator(
            RefreshTokenHasher refreshTokenHasher,
            RefreshTokenProperties properties
    ) {
        this.secureRandom = new SecureRandom();
        this.refreshTokenHasher = Objects.requireNonNull(
                refreshTokenHasher,
                "refreshTokenHasher must not be null"
        );
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    @Override
    public GeneratedRefreshToken generate(Instant issuedAt) {
        Objects.requireNonNull(issuedAt, "issuedAt must not be null");
        byte[] tokenBytes = new byte[RefreshTokenFormat.ENTROPY_BYTES];
        secureRandom.nextBytes(tokenBytes);
        String rawValue = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        String tokenHash = refreshTokenHasher.hash(rawValue);
        return new GeneratedRefreshToken(rawValue, tokenHash, issuedAt.plus(properties.ttl()));
    }
}
