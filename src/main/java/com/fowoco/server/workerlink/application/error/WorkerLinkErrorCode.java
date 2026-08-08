package com.fowoco.server.workerlink.application.error;

import com.fowoco.server.common.error.ApiErrorCode;
import org.springframework.http.HttpStatus;

public enum WorkerLinkErrorCode implements ApiErrorCode {
    TASK_NOT_FOUND(HttpStatus.NOT_FOUND, "업무카드를 찾을 수 없습니다."),
    TASK_NOT_APPROVED(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "승인된 업무카드에서만 근로자 링크를 발급할 수 있습니다."
    ),
    TASK_WORKER_TARGET_REQUIRED(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "근로자 대상 업무카드에서만 근로자 링크를 발급할 수 있습니다."
    ),
    WORKER_LINK_ISSUANCE_CONFLICT(
            HttpStatus.CONFLICT,
            "이미 유효한 근로자 링크가 있습니다. rotateExisting=true로 재발급해 주세요."
    ),
    WORKER_LINK_RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "근로자 링크를 찾을 수 없습니다."),
    WORKER_LINK_NOT_ACTIVE(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "현재 사용할 수 있는 근로자 링크만 전달 완료로 기록할 수 있습니다."
    ),

    WORKER_LINK_NOT_FOUND(HttpStatus.GONE, "링크를 찾을 수 없거나 더 이상 사용할 수 없습니다."),
    UPLOAD_NOT_AVAILABLE(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "업로드된 파일을 찾을 수 없거나 이미 사용된 파일입니다."
    );

    private final HttpStatus status;
    private final String defaultMessage;

    WorkerLinkErrorCode(HttpStatus status, String defaultMessage) {
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
