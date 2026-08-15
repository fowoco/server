package com.fowoco.server.file.application;

import java.util.Objects;

/**
 * 문서 미리보기 변환 실패를 외부 Provider의 세부 구현과 분리해 표현합니다.
 */
public final class DocumentPreviewConversionException extends RuntimeException {

    private final Reason reason;

    public DocumentPreviewConversionException(Reason reason, String safeMessage) {
        super(safeMessage);
        this.reason = Objects.requireNonNull(reason, "reason must not be null");
    }

    public DocumentPreviewConversionException(Reason reason, String safeMessage, Throwable cause) {
        super(safeMessage, cause);
        this.reason = Objects.requireNonNull(reason, "reason must not be null");
    }

    public Reason reason() {
        return reason;
    }

    public enum Reason {
        INVALID_DOCUMENT,
        UNAVAILABLE
    }
}
