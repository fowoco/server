package com.fowoco.server.task.application.error;

import com.fowoco.server.common.error.ApiErrorCode;
import org.springframework.http.HttpStatus;

public enum TaskErrorCode implements ApiErrorCode {
    TASK_NOT_FOUND(HttpStatus.NOT_FOUND, "업무카드를 찾을 수 없습니다."),
    WORKER_NOT_FOUND(HttpStatus.NOT_FOUND, "근로자를 찾을 수 없습니다."),
    WORKFLOW_NOT_FOUND(HttpStatus.NOT_FOUND, "Workflow를 찾을 수 없습니다."),
    TASK_VERSION_CONFLICT(HttpStatus.CONFLICT, "업무카드가 다른 요청에서 변경되었습니다."),
    INVALID_TASK_TRANSITION(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "현재 상태에서는 요청한 업무 처리를 수행할 수 없습니다."
    ),
    TASK_REQUIREMENTS_MISSING(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "필수정보와 필수 체크리스트를 먼저 확인해 주세요."
    ),
    APPROVAL_REQUIRED(HttpStatus.UNPROCESSABLE_ENTITY, "현재 업무 버전에 대한 HR 승인이 필요합니다."),
    EVIDENCE_REQUIRED(HttpStatus.UNPROCESSABLE_ENTITY, "업무 완료에 필요한 증빙이 없습니다.");

    private final HttpStatus status;
    private final String defaultMessage;

    TaskErrorCode(HttpStatus status, String defaultMessage) {
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
