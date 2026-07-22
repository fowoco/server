package com.fowoco.server.auth.infrastructure.security;

import com.fowoco.server.auth.application.port.PasswordVerifier;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public final class BCryptPasswordVerifier implements PasswordVerifier {

    private static final String DUMMY_PASSWORD = "fowoco-dummy-password-check";

    private final PasswordEncoder passwordEncoder;
    private final String dummyPasswordHash;

    public BCryptPasswordVerifier(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
        this.dummyPasswordHash = passwordEncoder.encode(DUMMY_PASSWORD);
    }

    @Override
    public boolean matches(String rawPassword, String passwordHash) {
        return passwordEncoder.matches(rawPassword, passwordHash);
    }

    @Override
    public void performDummyCheck(String rawPassword) {
        passwordEncoder.matches(rawPassword, dummyPasswordHash);
    }
}
