package com.fowoco.server.auth.application.error;

import com.fowoco.server.common.error.ApiException;

public final class InvalidRefreshTokenException extends ApiException {

    public InvalidRefreshTokenException() {
        super(AuthErrorCode.INVALID_REFRESH_TOKEN);
    }
}
