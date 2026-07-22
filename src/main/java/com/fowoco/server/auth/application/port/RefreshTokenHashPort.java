package com.fowoco.server.auth.application.port;

public interface RefreshTokenHashPort {

    String hash(String rawToken);
}
