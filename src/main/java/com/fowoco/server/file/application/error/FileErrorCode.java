package com.fowoco.server.file.application.error;

import com.fowoco.server.common.error.ApiErrorCode;
import org.springframework.http.HttpStatus;

public enum FileErrorCode implements ApiErrorCode {
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "파일 크기가 허용 범위를 초과했습니다."),
    UNSUPPORTED_FILE_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 파일 형식입니다."),
    FILE_PREVIEW_UNSUPPORTED(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "미리보기를 지원하지 않는 파일 형식입니다."),
    FILE_PREVIEW_INVALID(HttpStatus.UNPROCESSABLE_ENTITY, "문서를 PDF 미리보기로 변환할 수 없습니다."),
    FILE_PREVIEW_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "문서 미리보기 변환 서비스를 사용할 수 없습니다."),
    FILE_NOT_FOUND(HttpStatus.NOT_FOUND, "파일을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String defaultMessage;

    FileErrorCode(HttpStatus status, String defaultMessage) {
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
