package com.fowoco.server.approval.application.error;

import com.fowoco.server.common.error.ApiErrorCode;
import org.springframework.http.HttpStatus;

public enum ApprovalErrorCode implements ApiErrorCode {
    APPROVAL_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "승인 요청을 찾을 수 없습니다."),
    PENDING_APPROVAL_EXISTS(HttpStatus.CONFLICT, "처리 중인 승인 요청이 이미 있습니다."),
    APPROVAL_NOT_PENDING(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "이미 처리되었거나 무효화된 승인 요청입니다."
    ),
    APPROVAL_VERSION_MISMATCH(
            HttpStatus.CONFLICT,
            "승인 요청 이후 업무가 변경되었습니다. 다시 검토해 주세요."
    ),
    APPROVAL_INVALIDATED(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "현재 업무 내용과 일치하는 유효한 승인이 없습니다."
    ),
    SENSITIVE_SNAPSHOT_REJECTED(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "승인 Snapshot에 저장할 수 없는 개인정보 또는 Secret이 포함되어 있습니다."
    ),
    INVALID_EXTERNAL_SUBMISSION(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "외부 제출처와 안전한 접수 참조값을 확인해 주세요."
    ),
    INVALID_EVIDENCE(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "증빙 유형과 파일 참조 또는 안전한 메모를 확인해 주세요."
    ),
    INVALID_AUDIT_CURSOR(HttpStatus.BAD_REQUEST, "감사 조회 cursor 형식이 올바르지 않습니다.");

    private final HttpStatus status;
    private final String defaultMessage;

    ApprovalErrorCode(HttpStatus status, String defaultMessage) {
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
