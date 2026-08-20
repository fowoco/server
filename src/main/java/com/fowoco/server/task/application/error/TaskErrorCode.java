package com.fowoco.server.task.application.error;

import com.fowoco.server.common.error.ApiErrorCode;
import org.springframework.http.HttpStatus;

public enum TaskErrorCode implements ApiErrorCode {
    TASK_NOT_FOUND(HttpStatus.NOT_FOUND, "업무카드를 찾을 수 없습니다."),
    TASK_ASSIGNEE_NOT_FOUND(HttpStatus.NOT_FOUND, "지정할 담당자를 찾을 수 없습니다."),
    TASK_ASSIGNEE_NOT_ASSIGNABLE(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "활성 상태의 HR 또는 관리자만 업무 담당자로 지정할 수 있습니다."
    ),
    WORKER_NOT_FOUND(HttpStatus.NOT_FOUND, "근로자를 찾을 수 없습니다."),
    WORKFLOW_NOT_FOUND(HttpStatus.NOT_FOUND, "Workflow를 찾을 수 없습니다."),
    WORKFLOW_TASK_TYPE_MISMATCH(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "선택한 Workflow에서 지원하지 않는 업무 유형입니다."
    ),
    INVALID_TASK_TARGET(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "업무 대상과 근로자·Case 정보의 조합을 확인해 주세요."
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
    INVALID_AI_CANDIDATE_TASK_DATA(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "AI 업무 후보의 Workflow 또는 필수정보를 확인해 주세요."
    ),
    RENEWAL_EXECUTION_NOT_ALLOWED(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "이 업무카드에서는 재계약·연장 Agent를 실행할 수 없습니다."
    ),
    RENEWAL_REQUEST_CONTRACT_INVALID(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "재계약·연장 Agent 요청 정보가 현재 계약과 일치하지 않습니다."
    ),
    RENEWAL_WORKFLOW_MISMATCH(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "업무카드와 Agent가 선택한 Workflow가 일치하지 않습니다."
    ),
    INVALID_RENEWAL_SLOT_ANSWER(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "재계약·연장 업무에 제출한 추가 정보를 확인해 주세요."
    ),
    RENEWAL_RUNTIME_UNAVAILABLE(
            HttpStatus.SERVICE_UNAVAILABLE,
            "재계약·연장 분석을 일시적으로 실행할 수 없습니다."
    ),
    CHECKLIST_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "체크리스트 항목을 찾을 수 없습니다."),
    CASE_WORKER_MISMATCH(HttpStatus.CONFLICT, "Case와 업무카드의 근로자가 일치하지 않습니다."),
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
