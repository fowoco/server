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
    ),
    DOCUMENT_OCR_DISABLED(
            HttpStatus.SERVICE_UNAVAILABLE,
            "OCR 기능이 아직 활성화되지 않았습니다."
    ),
    DOCUMENT_OCR_UNSUPPORTED_TYPE(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "OCR을 지원하지 않는 서류 유형입니다."
    ),
    DOCUMENT_OCR_UNSUPPORTED_COUNTRY(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "현재 OCR을 지원하지 않는 여권 발급 국가입니다."
    ),
    DOCUMENT_OCR_FILE_REQUIRED(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "OCR을 실행할 파일이 연결되어 있지 않습니다."
    ),
    DOCUMENT_OCR_FILE_MISMATCH(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "서류와 파일의 근로자 정보가 일치하지 않습니다."
    ),
    DOCUMENT_OCR_RUN_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "OCR 실행 이력을 찾을 수 없습니다."
    ),
    DOCUMENT_OCR_IDEMPOTENCY_CONFLICT(
            HttpStatus.CONFLICT,
            "같은 Idempotency-Key가 다른 OCR 요청에 사용되었습니다."
    ),
    DOCUMENT_OCR_VERSION_CONFLICT(
            HttpStatus.CONFLICT,
            "다른 사용자가 OCR 결과를 먼저 검토했습니다. 새로고침 후 다시 시도해 주세요."
    ),
    DOCUMENT_OCR_NOT_REVIEWABLE(
            HttpStatus.CONFLICT,
            "현재 상태에서는 OCR 결과를 검토할 수 없습니다."
    ),
    DOCUMENT_OCR_CORRECTION_INVALID(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "OCR 수정 필드가 문서 유형 또는 입력 규칙에 맞지 않습니다."
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
