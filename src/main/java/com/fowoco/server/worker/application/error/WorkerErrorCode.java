package com.fowoco.server.worker.application.error;

import com.fowoco.server.common.error.ApiErrorCode;
import org.springframework.http.HttpStatus;

public enum WorkerErrorCode implements ApiErrorCode {
    WORKER_NOT_FOUND(HttpStatus.NOT_FOUND, "근로자를 찾을 수 없습니다."),
    WORKER_VERSION_CONFLICT(HttpStatus.CONFLICT, "다른 사용자가 먼저 수정했습니다. 새로고침 후 다시 시도해 주세요."),
    WORKER_DOCUMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "서류를 찾을 수 없습니다."),
    WORKER_DOCUMENT_VERSION_CONFLICT(HttpStatus.CONFLICT, "다른 사용자가 먼저 수정했습니다. 새로고침 후 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String defaultMessage;

    WorkerErrorCode(HttpStatus status, String defaultMessage) {
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
