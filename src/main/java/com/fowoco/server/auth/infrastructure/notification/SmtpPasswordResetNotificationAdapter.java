package com.fowoco.server.auth.infrastructure.notification;

import com.fowoco.server.auth.application.port.PasswordResetNotificationPort;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.mail.autoconfigure.MailProperties;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@ConditionalOnProperty(
        prefix = "app.auth.password-reset.notification",
        name = "provider",
        havingValue = "smtp"
)
public final class SmtpPasswordResetNotificationAdapter implements PasswordResetNotificationPort {

    private final JavaMailSender mailSender;
    private final PasswordResetNotificationProperties properties;

    public SmtpPasswordResetNotificationAdapter(
            JavaMailSender mailSender,
            PasswordResetNotificationProperties properties,
            MailProperties mailProperties
    ) {
        properties.validateForSmtp();
        validateMailProperties(mailProperties);
        this.mailSender = mailSender;
        this.properties = properties;
    }

    private void validateMailProperties(MailProperties mailProperties) {
        if (!StringUtils.hasText(mailProperties.getHost())) {
            throw new IllegalStateException(
                    "SPRING_MAIL_HOST must not be blank when the SMTP notification provider is enabled"
            );
        }
        boolean authenticationRequired = Boolean.parseBoolean(
                mailProperties.getProperties().getOrDefault("mail.smtp.auth", "false")
        );
        if (authenticationRequired && !StringUtils.hasText(mailProperties.getUsername())) {
            throw new IllegalStateException(
                    "SPRING_MAIL_USERNAME is required when SMTP authentication is enabled"
            );
        }
        if (authenticationRequired && !StringUtils.hasText(mailProperties.getPassword())) {
            throw new IllegalStateException(
                    "SPRING_MAIL_PASSWORD is required when SMTP authentication is enabled"
            );
        }
    }

    @Override
    public void sendResetLink(String email, String rawToken, Instant expiresAt) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.from());
        message.setTo(email);
        message.setSubject(properties.subject());
        message.setText(body(resetLink(rawToken), expiresAt));
        mailSender.send(message);
    }

    private String resetLink(String rawToken) {
        return UriComponentsBuilder.fromUri(properties.resetUrl())
                .queryParam("token", rawToken)
                .build()
                .encode()
                .toUriString();
    }

    private String body(String resetLink, Instant expiresAt) {
        return """
                FOWOCO 비밀번호 재설정 요청이 접수되었습니다.

                아래 링크에서 새 비밀번호를 설정해 주세요.
                %s

                링크 만료 시각(UTC): %s

                본인이 요청하지 않았다면 이 메일을 무시해 주세요.
                """.formatted(resetLink, DateTimeFormatter.ISO_INSTANT.format(expiresAt));
    }
}
