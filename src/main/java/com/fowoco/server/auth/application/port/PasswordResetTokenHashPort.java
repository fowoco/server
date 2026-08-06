package com.fowoco.server.auth.application.port;

public interface PasswordResetTokenHashPort {

    String hash(String rawToken);
}
