package com.fowoco.server.workerimport.application.error;

import com.fowoco.server.common.error.ApiErrorCode;
import org.springframework.http.HttpStatus;

public enum WorkerImportErrorCode implements ApiErrorCode {
    IMPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "가져오기 작업을 찾을 수 없습니다."),
    IMPORT_FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "가져오기 파일은 5MB를 넘을 수 없습니다."),
    IMPORT_FILE_TYPE_UNSUPPORTED(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "CSV 또는 XLSX 파일만 사용할 수 있습니다."),
    IMPORT_FILE_INVALID(HttpStatus.UNPROCESSABLE_CONTENT, "파일 구조 또는 문자 인코딩을 확인해 주세요."),
    IMPORT_FILE_EMPTY(HttpStatus.UNPROCESSABLE_CONTENT, "헤더와 한 개 이상의 데이터 행이 필요합니다."),
    IMPORT_FILE_LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_CONTENT, "파일은 최대 1,000행, 50열까지 처리할 수 있습니다."),
    IMPORT_FORMULA_NOT_ALLOWED(HttpStatus.UNPROCESSABLE_CONTENT, "수식이 포함된 셀은 가져올 수 없습니다."),
    IMPORT_SENSITIVE_COLUMN_NOT_ALLOWED(HttpStatus.UNPROCESSABLE_CONTENT, "여권번호·외국인등록번호·계좌번호 열은 가져올 수 없습니다."),
    IMPORT_MAPPING_INVALID(HttpStatus.UNPROCESSABLE_CONTENT, "열 연결 정보를 확인해 주세요."),
    IMPORT_STATE_INVALID(HttpStatus.CONFLICT, "현재 단계에서는 요청한 작업을 수행할 수 없습니다."),
    IMPORT_VERSION_CONFLICT(HttpStatus.CONFLICT, "다른 사용자가 먼저 수정했습니다. 새로고침 후 다시 시도해 주세요."),
    IMPORT_IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "같은 Idempotency-Key가 다른 요청에 사용되었습니다."),
    IMPORT_NO_VALID_ROWS(HttpStatus.UNPROCESSABLE_CONTENT, "등록할 수 있는 정상 행이 없습니다.");

    private final HttpStatus status;
    private final String message;

    WorkerImportErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
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
        return message;
    }
}
