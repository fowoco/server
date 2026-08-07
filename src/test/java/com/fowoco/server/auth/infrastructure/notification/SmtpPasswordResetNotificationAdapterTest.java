package com.fowoco.server.auth.infrastructure.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.net.URI;
import java.time.Instant;
import org.junit.jupiter.api.Test;
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
                )
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
                )
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PASSWORD_RESET_MAIL_FROM");

        assertThatThrownBy(() -> new SmtpPasswordResetNotificationAdapter(
                mailSender,
                new PasswordResetNotificationProperties(
                        "no-reply@fowoco.test",
                        URI.create("file:///tmp/reset-password"),
                        "FOWOCO 비밀번호 재설정"
                )
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PASSWORD_RESET_CLIENT_URL");
    }
}
