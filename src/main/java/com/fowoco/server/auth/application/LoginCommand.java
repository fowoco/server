package com.fowoco.server.auth.application;

import java.nio.charset.StandardCharsets;

public final class LoginCommand {

    private static final int MAX_EMAIL_LENGTH = 254;
    private static final int MAX_PASSWORD_LENGTH = 128;

    private final String email;
    private final String password;
    private final String deviceSummary;

    public LoginCommand(String email, String password, String deviceSummary) {
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
        if (deviceSummary == null || deviceSummary.isBlank()) {
            throw new IllegalArgumentException("deviceSummary must not be blank");
        }
        this.email = email;
        this.password = password;
        this.deviceSummary = deviceSummary;
    }

    public String email() {
        return email;
    }

    public String password() {
        return password;
    }

    public String deviceSummary() {
        return deviceSummary;
    }
}
