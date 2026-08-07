package com.fowoco.server.auth.infrastructure.notification;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "app.auth.password-reset.notification")
public record PasswordResetNotificationProperties(
        String from,
        URI resetUrl,
        String subject
) {

    public PasswordResetNotificationProperties {
        from = from == null ? "" : from.trim();
        subject = subject == null ? "" : subject.trim();
    }

    void validateForSmtp() {
        if (!StringUtils.hasText(from) || containsLineBreak(from)) {
            throw new IllegalStateException("PASSWORD_RESET_MAIL_FROM must contain a safe sender address");
        }
        if (resetUrl == null || !isHttp(resetUrl)) {
            throw new IllegalStateException("PASSWORD_RESET_CLIENT_URL must be an HTTP(S) URL");
        }
        if (!StringUtils.hasText(subject) || containsLineBreak(subject)) {
            throw new IllegalStateException("PASSWORD_RESET_MAIL_SUBJECT must contain safe text");
        }
    }

    private boolean isHttp(URI uri) {
        return "http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme());
    }

    private boolean containsLineBreak(String value) {
        return value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0;
    }
}
