package com.fowoco.server.reliability.application.error;

import com.fowoco.server.common.error.ApiErrorCode;
import org.springframework.http.HttpStatus;

public enum OutboxErrorCode implements ApiErrorCode {
    OUTBOX_EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Outbox 이벤트를 찾을 수 없습니다."),
    OUTBOX_EVENT_NOT_REVIEW_REQUIRED(
            HttpStatus.CONFLICT,
            "수동 확인이 필요한 Outbox 이벤트만 다시 처리할 수 있습니다."
    ),
    OUTBOX_EVENT_VERSION_CONFLICT(
            HttpStatus.CONFLICT,
            "이벤트 상태가 먼저 변경되었습니다. 최신 정보를 다시 확인해 주세요."
    ),
    OUTBOX_RETRY_IDEMPOTENCY_CONFLICT(
            HttpStatus.CONFLICT,
            "같은 Idempotency-Key가 다른 재처리 요청에 이미 사용되었습니다."
    ),
    OUTBOX_RETRY_INVALID_IDEMPOTENCY_KEY(
            HttpStatus.BAD_REQUEST,
            "Idempotency-Key를 확인해 주세요."
    );

    private final HttpStatus status;
    private final String defaultMessage;

    OutboxErrorCode(HttpStatus status, String defaultMessage) {
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
