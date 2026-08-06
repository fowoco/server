package com.fowoco.server.auth.application.port;

import java.time.Instant;

public interface PasswordResetNotificationPort {

    void sendResetLink(String email, String rawToken, Instant expiresAt);
}
