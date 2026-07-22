package com.fowoco.server.auth.application;

import java.nio.charset.StandardCharsets;

public final class LoginCommand {

    private static final int MAX_EMAIL_LENGTH = 254;
    private static final int MAX_PASSWORD_LENGTH = 128;

    private final String email;
    private final String password;

    public LoginCommand(String email, String password) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }
        if (email.length() > MAX_EMAIL_LENGTH) {
            throw new IllegalArgumentException("email must not exceed " + MAX_EMAIL_LENGTH + " characters");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("password must not be blank");
        }
        if (password.length() > MAX_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("password must not exceed " + MAX_PASSWORD_LENGTH + " characters");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("password must not exceed 72 UTF-8 bytes");
        }
        this.email = email;
        this.password = password;
    }

    public String email() {
        return email;
    }

    public String password() {
        return password;
    }
}
