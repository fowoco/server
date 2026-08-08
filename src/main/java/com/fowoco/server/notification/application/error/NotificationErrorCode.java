package com.fowoco.server.notification.application.error;

import com.fowoco.server.common.error.ApiErrorCode;
import org.springframework.http.HttpStatus;

public enum NotificationErrorCode implements ApiErrorCode {
    NOTIFICATION_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "알림을 찾을 수 없습니다."
    );

    private final HttpStatus status;
    private final String defaultMessage;

    NotificationErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String defaultMessage() {
        return defaultMessage;
    }
}
