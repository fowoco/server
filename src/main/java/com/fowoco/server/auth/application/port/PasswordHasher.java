package com.fowoco.server.auth.application.port;

@FunctionalInterface
public interface PasswordHasher {

    String hash(String rawPassword);
}
