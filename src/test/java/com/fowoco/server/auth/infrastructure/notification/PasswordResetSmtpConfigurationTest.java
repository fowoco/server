package com.fowoco.server.auth.infrastructure.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.fowoco.server.auth.application.port.PasswordResetNotificationPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ActiveProfiles("test")
@SpringBootTest(properties = {
        "app.auth.password-reset.notification.provider=smtp",
        "app.auth.password-reset.notification.from=no-reply@fowoco.test",
        "app.auth.password-reset.notification.reset-url=https://demo.fowoco.test/reset-password",
        "app.auth.password-reset.notification.subject=FOWOCO 비밀번호 재설정"
})
class PasswordResetSmtpConfigurationTest {

    @MockitoBean
    private JavaMailSender mailSender;

    @Autowired
    private PasswordResetNotificationPort notificationPort;

    @Test
    void smtpProviderSelectsSmtpAdapterInsteadOfNoOp() {
        assertThat(notificationPort).isInstanceOf(SmtpPasswordResetNotificationAdapter.class);
    }
}
