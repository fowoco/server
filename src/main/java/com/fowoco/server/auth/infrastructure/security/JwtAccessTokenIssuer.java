package com.fowoco.server.auth.infrastructure.security;

import com.fowoco.server.auth.application.port.AccessTokenIssuer;
import com.fowoco.server.auth.domain.UserAccount;
import com.fowoco.server.common.id.UuidGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

@Component
public final class JwtAccessTokenIssuer implements AccessTokenIssuer {

    private static final String TOKEN_TYPE = "access";

    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;
    private final UuidGenerator uuidGenerator;

    public JwtAccessTokenIssuer(
            JwtEncoder jwtEncoder,
            JwtProperties properties,
            UuidGenerator uuidGenerator
    ) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.uuidGenerator = uuidGenerator;
    }

    @Override
    public IssuedAccessToken issue(UserAccount userAccount, Instant issuedAt) {
        Instant expiresAt = issuedAt.plus(properties.accessTokenTtl());
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256)
                .type("JWT")
                .build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(userAccount.userId().toString())
                .audience(List.of(properties.audience()))
                .issuedAt(issuedAt)
                .notBefore(issuedAt)
                .expiresAt(expiresAt)
                .id(uuidGenerator.generate().toString())
                .claim("company_id", userAccount.companyId().toString())
                .claim("roles", List.of(userAccount.role().name()))
                .claim("token_type", TOKEN_TYPE)
                .build();

        String tokenValue = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        long expiresInSeconds = Duration.between(issuedAt, expiresAt).toSeconds();
        return new IssuedAccessToken(tokenValue, expiresAt, expiresInSeconds);
    }
}
