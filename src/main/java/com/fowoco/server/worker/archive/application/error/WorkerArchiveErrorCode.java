package com.fowoco.server.worker.archive.application.error;

import com.fowoco.server.common.error.ApiErrorCode;
import org.springframework.http.HttpStatus;

public enum WorkerArchiveErrorCode implements ApiErrorCode {
    WORKER_ARCHIVE_NOT_ALLOWED(HttpStatus.CONFLICT, "진행 중인 업무 또는 현재 근무상태 때문에 보관할 수 없습니다."),
    WORKER_ALREADY_ARCHIVED(HttpStatus.CONFLICT, "이미 보관 처리된 근로자입니다."),
    WORKER_ARCHIVE_VERSION_CONFLICT(HttpStatus.CONFLICT, "근로자 정보가 변경되었습니다. 새로고침 후 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String defaultMessage;

    WorkerArchiveErrorCode(HttpStatus status, String defaultMessage) {
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
