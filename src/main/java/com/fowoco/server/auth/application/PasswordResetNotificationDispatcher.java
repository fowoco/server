package com.fowoco.server.auth.application;

import com.fowoco.server.auth.application.port.PasswordResetNotificationPort;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class PasswordResetNotificationDispatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(PasswordResetNotificationDispatcher.class);

    private final PasswordResetNotificationPort notificationPort;
    private final Executor notificationExecutor;

    public PasswordResetNotificationDispatcher(
            PasswordResetNotificationPort notificationPort,
            @Qualifier("passwordResetNotificationExecutor") Executor notificationExecutor
    ) {
        this.notificationPort = notificationPort;
        this.notificationExecutor = notificationExecutor;
    }

    public void dispatch(PasswordResetDispatch dispatch) {
        try {
            notificationExecutor.execute(() -> sendSafely(dispatch));
        } catch (RuntimeException exception) {
            logFailure(exception);
        }
    }

    private void sendSafely(PasswordResetDispatch dispatch) {
        try {
            notificationPort.sendResetLink(
                    dispatch.email(),
                    dispatch.rawToken(),
                    dispatch.expiresAt()
            );
        } catch (RuntimeException exception) {
            logFailure(exception);
        }
    }

    private void logFailure(RuntimeException exception) {
        // Provider 예외 메시지에는 이메일이나 원본 token이 포함될 수 있으므로 유형만 기록합니다.
        LOGGER.warn(
                "password_reset_notification_failed exception_type={}",
                exception.getClass().getSimpleName()
        );
    }
}
