package com.fowoco.server.dashboard.application.error;

import com.fowoco.server.common.error.ApiErrorCode;
import org.springframework.http.HttpStatus;

public enum DashboardErrorCode implements ApiErrorCode {
    INVALID_TIMEZONE(
            HttpStatus.BAD_REQUEST,
            "유효하지 않은 timezone 값입니다. IANA 타임존 ID(예: Asia/Seoul)를 사용해 주세요."
    );

    private final HttpStatus status;
    private final String defaultMessage;

    DashboardErrorCode(HttpStatus status, String defaultMessage) {
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
