package com.fowoco.server.auth.application.port;

public interface PasswordVerifier {

    boolean matches(String rawPassword, String passwordHash);

    void performDummyCheck(String rawPassword);
}
