package com.fowoco.server.auth.infrastructure.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.net.URI;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.boot.mail.autoconfigure.MailProperties;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class SmtpPasswordResetNotificationAdapterTest {

    @Test
    void sendsResetLinkWithTokenAndExpiry() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        SmtpPasswordResetNotificationAdapter adapter = new SmtpPasswordResetNotificationAdapter(
                mailSender,
                new PasswordResetNotificationProperties(
                        "no-reply@fowoco.test",
                        URI.create("https://demo.fowoco.test/reset-password"),
                        "FOWOCO 비밀번호 재설정"
                ),
                mailProperties("smtp.fowoco.test", false, null, null)
        );
        Instant expiresAt = Instant.parse("2026-08-07T06:30:00Z");

        adapter.sendResetLink("worker@example.com", "safe_token-123", expiresAt);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();
        assertThat(message.getFrom()).isEqualTo("no-reply@fowoco.test");
        assertThat(message.getTo()).containsExactly("worker@example.com");
        assertThat(message.getSubject()).isEqualTo("FOWOCO 비밀번호 재설정");
        assertThat(message.getText())
                .contains("https://demo.fowoco.test/reset-password?token=safe_token-123")
                .contains("2026-08-07T06:30:00Z")
                .contains("본인이 요청하지 않았다면");
    }

    @Test
    void rejectsUnsafeOrIncompleteSmtpConfiguration() {
        JavaMailSender mailSender = mock(JavaMailSender.class);

        assertThatThrownBy(() -> new SmtpPasswordResetNotificationAdapter(
                mailSender,
                new PasswordResetNotificationProperties(
                        "",
                        URI.create("https://demo.fowoco.test/reset-password"),
                        "FOWOCO 비밀번호 재설정"
                ),
                mailProperties("smtp.fowoco.test", false, null, null)
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PASSWORD_RESET_MAIL_FROM");

        assertThatThrownBy(() -> new SmtpPasswordResetNotificationAdapter(
                mailSender,
                new PasswordResetNotificationProperties(
                        "no-reply@fowoco.test",
                        URI.create("file:///tmp/reset-password"),
                        "FOWOCO 비밀번호 재설정"
                ),
                mailProperties("smtp.fowoco.test", false, null, null)
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PASSWORD_RESET_CLIENT_URL");
    }

    @Test
    void rejectsMissingHostAndAuthenticationCredentials() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        PasswordResetNotificationProperties notificationProperties =
                new PasswordResetNotificationProperties(
                        "no-reply@fowoco.test",
                        URI.create("https://demo.fowoco.test/reset-password"),
                        "FOWOCO 비밀번호 재설정"
                );

        assertThatThrownBy(() -> new SmtpPasswordResetNotificationAdapter(
                mailSender,
                notificationProperties,
                mailProperties("", false, null, null)
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SPRING_MAIL_HOST");

        assertThatThrownBy(() -> new SmtpPasswordResetNotificationAdapter(
                mailSender,
                notificationProperties,
                mailProperties("smtp.fowoco.test", true, "", "password")
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SPRING_MAIL_USERNAME");

        assertThatThrownBy(() -> new SmtpPasswordResetNotificationAdapter(
                mailSender,
                notificationProperties,
                mailProperties("smtp.fowoco.test", true, "mailer", "")
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SPRING_MAIL_PASSWORD");
    }

    private MailProperties mailProperties(
            String host,
            boolean authenticationRequired,
            String username,
            String password
    ) {
        MailProperties properties = new MailProperties();
        properties.setHost(host);
        properties.setUsername(username);
        properties.setPassword(password);
        properties.getProperties().put("mail.smtp.auth", Boolean.toString(authenticationRequired));
        return properties;
    }
}
