package com.fowoco.server.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.env.MockEnvironment;

class RefreshTokenCookieFactoryTest {

    private static final String COOKIE_NAME = "fowoco_refresh_token";
    private static final String COOKIE_PATH = "/api/v1/auth";

    @Test
    void sameSiteNoneIsRejectedWhileCsrfProtectionIsNotEnabled() {
        assertThatThrownBy(() -> new RefreshTokenCookieFactory(
                COOKIE_NAME,
                COOKIE_PATH,
                "None",
                true,
                Duration.ofDays(14),
                new MockEnvironment()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Strict or Lax");
    }

    @Test
    void clearCookieUsesTheSameSecurityAttributesAndExpiresImmediately() {
        RefreshTokenCookieFactory factory = new RefreshTokenCookieFactory(
                COOKIE_NAME,
                COOKIE_PATH,
                "Strict",
                true,
                Duration.ofDays(14),
                new MockEnvironment()
        );

        ResponseCookie cookie = factory.clear();

        assertThat(cookie.getName()).isEqualTo(COOKIE_NAME);
        assertThat(cookie.getValue()).isEmpty();
        assertThat(cookie.getPath()).isEqualTo(COOKIE_PATH);
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Strict");
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ZERO);
    }
}
