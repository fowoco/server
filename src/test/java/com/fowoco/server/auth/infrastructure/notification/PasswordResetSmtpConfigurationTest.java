package com.fowoco.server.auth.infrastructure.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.fowoco.server.auth.application.port.PasswordResetNotificationPort;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.mail.autoconfigure.MailProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ActiveProfiles("test")
@SpringBootTest(properties = {
        "app.auth.password-reset.notification.provider=smtp",
        "app.auth.password-reset.notification.from=no-reply@fowoco.test",
        "app.auth.password-reset.notification.reset-url=https://demo.fowoco.test/reset-password",
        "app.auth.password-reset.notification.subject=FOWOCO 비밀번호 재설정",
        "spring.mail.host=smtp.fowoco.test",
        "management.health.mail.enabled=false"
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

    @Test
    void blankMailHostPreventsSmtpContextStartup() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        PasswordResetNotificationProperties notificationProperties =
                new PasswordResetNotificationProperties(
                        "no-reply@fowoco.test",
                        URI.create("https://demo.fowoco.test/reset-password"),
                        "FOWOCO 비밀번호 재설정"
                );
        MailProperties mailProperties = new MailProperties();

        assertThatThrownBy(() -> {
            try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
                TestPropertyValues.of("app.auth.password-reset.notification.provider=smtp")
                        .applyTo(context);
                context.registerBean(
                        SmtpPasswordResetNotificationAdapter.class,
                        () -> new SmtpPasswordResetNotificationAdapter(
                                mailSender,
                                notificationProperties,
                                mailProperties
                        )
                );
                context.refresh();
                context.getBean(SmtpPasswordResetNotificationAdapter.class);
            }
        }).hasRootCauseInstanceOf(IllegalStateException.class)
                .hasStackTraceContaining("SPRING_MAIL_HOST");
    }
}
