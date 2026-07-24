package com.fowoco.server.auth.infrastructure.security;

import com.fowoco.server.auth.application.port.PasswordHasher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public final class BCryptPasswordHasher implements PasswordHasher {

    private final PasswordEncoder passwordEncoder;

    public BCryptPasswordHasher(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public String hash(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }
}
