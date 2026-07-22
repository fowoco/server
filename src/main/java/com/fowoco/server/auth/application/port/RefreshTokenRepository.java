package com.fowoco.server.auth.application.port;

import com.fowoco.server.auth.domain.RefreshToken;

public interface RefreshTokenRepository {

    void save(RefreshToken refreshToken);
}
