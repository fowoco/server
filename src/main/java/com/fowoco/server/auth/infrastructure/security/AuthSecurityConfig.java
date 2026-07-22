package com.fowoco.server.auth.infrastructure.security;

import com.fowoco.server.auth.domain.UserRole;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.crypto.SecretKey;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({JwtProperties.class, RefreshTokenProperties.class})
public class AuthSecurityConfig {

    private static final Set<String> ALLOWED_ROLES = Arrays.stream(UserRole.values())
            .map(Enum::name)
            .collect(Collectors.toUnmodifiableSet());

    @Bean
    public JwtEncoder jwtEncoder(JwtProperties properties) {
        return NimbusJwtEncoder.withSecretKey(properties.secretKey())
                .algorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    public JwtDecoder jwtDecoder(JwtProperties properties) {
        SecretKey secretKey = properties.secretKey();
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .validateType(true)
                .build();

        OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(properties.issuer());
        OAuth2TokenValidator<Jwt> audienceValidator = audienceValidator(properties.audience());
        OAuth2TokenValidator<Jwt> accessTokenValidator = accessTokenValidator();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                issuerValidator,
                audienceValidator,
                accessTokenValidator
        ));
        return decoder;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("roles");
        authoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter authenticationConverter = new JwtAuthenticationConverter();
        authenticationConverter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return authenticationConverter;
    }

    private OAuth2TokenValidator<Jwt> audienceValidator(String requiredAudience) {
        return jwt -> {
            List<String> audience = jwt.getAudience();
            if (audience != null && audience.contains(requiredAudience)) {
                return OAuth2TokenValidatorResult.success();
            }
            OAuth2Error error = new OAuth2Error(
                    "invalid_token",
                    "The required audience is missing.",
                    null
            );
            return OAuth2TokenValidatorResult.failure(error);
        };
    }

    private OAuth2TokenValidator<Jwt> accessTokenValidator() {
        return jwt -> {
            if (hasRequiredAccessTokenClaims(jwt)) {
                return OAuth2TokenValidatorResult.success();
            }
            OAuth2Error error = new OAuth2Error(
                    "invalid_token",
                    "Required access token claims are missing or invalid.",
                    null
            );
            return OAuth2TokenValidatorResult.failure(error);
        };
    }

    private boolean hasRequiredAccessTokenClaims(Jwt jwt) {
        try {
            UUID.fromString(jwt.getSubject());
            UUID.fromString(jwt.getClaimAsString("company_id"));
            List<String> roles = jwt.getClaimAsStringList("roles");
            return "access".equals(jwt.getClaimAsString("token_type"))
                    && jwt.getId() != null
                    && !jwt.getId().isBlank()
                    && roles != null
                    && !roles.isEmpty()
                    && roles.stream().allMatch(ALLOWED_ROLES::contains);
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
