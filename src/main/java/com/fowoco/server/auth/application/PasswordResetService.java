package com.fowoco.server.auth.application;

import com.fowoco.server.auth.application.port.PasswordResetNotificationPort;
import com.fowoco.server.auth.infrastructure.security.PasswordResetProperties;
import java.util.Optional;
import java.util.concurrent.locks.LockSupport;
import org.springframework.stereotype.Service;

@Service
public class PasswordResetService {

    private final PasswordResetTransaction transaction;
    private final PasswordResetNotificationPort notificationPort;
    private final PasswordResetRequestRateLimiter rateLimiter;
    private final PasswordResetProperties properties;

    public PasswordResetService(
            PasswordResetTransaction transaction,
            PasswordResetNotificationPort notificationPort,
            PasswordResetRequestRateLimiter rateLimiter,
            PasswordResetProperties properties
    ) {
        this.transaction = transaction;
        this.notificationPort = notificationPort;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
    }

    public void request(String email, String remoteAddress, String requestId, String traceId) {
        long startedAt = System.nanoTime();
        try {
            if (!rateLimiter.tryAcquire(remoteAddress)) {
                return;
            }
            Optional<PasswordResetDispatch> dispatch = transaction.issue(email, requestId, traceId);
            dispatch.ifPresent(value -> notificationPort.sendResetLink(
                    value.email(),
                    value.rawToken(),
                    value.expiresAt()
            ));
        } finally {
            waitForMinimumResponseTime(startedAt);
        }
    }

    public void complete(String rawToken, String newPassword, String requestId, String traceId) {
        transaction.complete(rawToken, newPassword, requestId, traceId);
    }

    private void waitForMinimumResponseTime(long startedAt) {
        long minimumNanos = properties.minimumResponseTime().toNanos();
        long remainingNanos;
        while ((remainingNanos = minimumNanos - (System.nanoTime() - startedAt)) > 0) {
            LockSupport.parkNanos(remainingNanos);
        }
    }
}
