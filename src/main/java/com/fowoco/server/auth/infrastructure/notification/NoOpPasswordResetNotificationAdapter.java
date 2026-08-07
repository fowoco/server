package com.fowoco.server.auth.infrastructure.notification;

import com.fowoco.server.auth.application.port.PasswordResetNotificationPort;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "app.auth.password-reset.notification",
        name = "provider",
        havingValue = "none",
        matchIfMissing = true
)
public final class NoOpPasswordResetNotificationAdapter implements PasswordResetNotificationPort {

    private static final Logger LOGGER = LoggerFactory.getLogger(NoOpPasswordResetNotificationAdapter.class);

    @Override
    public void sendResetLink(String email, String rawToken, Instant expiresAt) {
        // 원본 이메일과 token은 의도적으로 로그에 남기지 않습니다.
        LOGGER.info("password_reset_notification_skipped provider=none expires_at={}", expiresAt);
    }
}
