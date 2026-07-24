package com.fowoco.server.task.application.error;

import com.fowoco.server.common.error.ApiErrorCode;
import org.springframework.http.HttpStatus;

public enum TaskErrorCode implements ApiErrorCode {
    TASK_NOT_FOUND(HttpStatus.NOT_FOUND, "업무카드를 찾을 수 없습니다."),
    WORKER_NOT_FOUND(HttpStatus.NOT_FOUND, "근로자를 찾을 수 없습니다."),
    WORKFLOW_NOT_FOUND(HttpStatus.NOT_FOUND, "Workflow를 찾을 수 없습니다."),
    WORKFLOW_TASK_TYPE_MISMATCH(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "선택한 Workflow에서 지원하지 않는 업무 유형입니다."
    ),
    INVALID_TASK_FILTER(HttpStatus.BAD_REQUEST, "업무카드 조회 조건을 확인해 주세요."),
    WORKER_NOT_ELIGIBLE(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "현재 근무 상태의 근로자에게는 새 업무를 만들 수 없습니다."
    ),
    SENSITIVE_TASK_DATA_REJECTED(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "업무카드에 저장할 수 없는 개인정보 또는 Secret이 포함되어 있습니다."
    ),
    CHECKLIST_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "체크리스트 항목을 찾을 수 없습니다."),
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "업무카드가 다른 요청에서 변경되었습니다."),
    TASK_TRANSITION_NOT_ALLOWED(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "현재 상태에서는 요청한 업무 처리를 수행할 수 없습니다."
    ),
    TASK_REQUIREMENTS_MISSING(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "필수정보와 필수 체크리스트를 먼저 확인해 주세요."
    ),
    APPROVAL_REQUIRED(HttpStatus.UNPROCESSABLE_CONTENT, "현재 업무 버전에 대한 HR 승인이 필요합니다."),
    EVIDENCE_REQUIRED(HttpStatus.UNPROCESSABLE_CONTENT, "업무 완료에 필요한 증빙이 없습니다.");

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
