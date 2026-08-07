package com.fowoco.server.document.application.error;

import com.fowoco.server.common.error.ApiErrorCode;
import org.springframework.http.HttpStatus;

public enum DocumentErrorCode implements ApiErrorCode {
    DOCUMENT_REQUEST_DRAFT_VERSION_CONFLICT(
            HttpStatus.CONFLICT,
            "다른 사용자가 먼저 수정했습니다. 새로고침 후 다시 시도해 주세요."
    ),
    DOCUMENT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "문서를 찾을 수 없습니다."
    );

    private final HttpStatus status;
    private final String defaultMessage;

    DocumentErrorCode(HttpStatus status, String defaultMessage) {
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
