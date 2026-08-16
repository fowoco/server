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
    WORKER_LINK_SMS_REQUEST_MISMATCH(
            HttpStatus.CONFLICT,
            "발급한 링크와 SMS 발송 요청이 일치하지 않습니다. 링크를 다시 발급해 주세요."
    ),
    WORKER_LINK_SMS_PROVIDER_DISABLED(
            HttpStatus.SERVICE_UNAVAILABLE,
            "SMS 발송 기능이 설정되지 않았습니다. 링크를 복사해 직접 전달해 주세요."
    ),
    WORKER_LINK_SMS_DELIVERY_FAILED(
            HttpStatus.BAD_GATEWAY,
            "SMS를 발송하지 못했습니다. 잠시 후 다시 시도하거나 링크를 직접 전달해 주세요."
    ),
    WORKER_LINK_SMS_DELIVERY_REVIEW_REQUIRED(
            HttpStatus.CONFLICT,
            "SMS 발송 결과를 확정할 수 없습니다. 자동 재발송하지 말고 발송 내역을 확인해 주세요."
    ),
    WORKER_LINK_CONTENT_NOT_READY(
            HttpStatus.CONFLICT,
            "근로자에게 표시할 요청 안내가 아직 준비되지 않았습니다."
    ),

    WORKER_LINK_NOT_FOUND(HttpStatus.GONE, "링크를 찾을 수 없거나 더 이상 사용할 수 없습니다."),
    UPLOAD_NOT_AVAILABLE(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "업로드된 파일을 찾을 수 없거나 이미 사용된 파일입니다."
    ),
    IDEMPOTENCY_CONFLICT(
            HttpStatus.CONFLICT,
            "같은 Idempotency-Key가 다른 문서 업로드 요청에 이미 사용되었습니다."
    ),
    WORKER_SLOT_ANSWER_INVALID(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "요청하지 않았거나 허용되지 않은 근로자 답변입니다."
    ),
    WORKER_RESPONSE_IDEMPOTENCY_CONFLICT(
            HttpStatus.CONFLICT,
            "같은 idempotency_key가 다른 근로자 응답에 이미 사용되었습니다."
    ),
    WORKER_RESPONSE_NOT_FOUND(HttpStatus.NOT_FOUND, "근로자 응답을 찾을 수 없습니다."),
    WORKER_RESPONSE_NOT_DOCUMENT_SUBMISSION(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "서류 제출 응답만 공식 근로자 서류로 등록할 수 있습니다."
    ),
    WORKER_RESPONSE_DOCUMENT_TYPE_INVALID(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "제출 파일의 서류 유형을 확인할 수 없습니다."
    ),
    WORKER_RESPONSE_DOCUMENTS_INCOMPLETE(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "요청한 서류가 모두 제출된 뒤 공식 서류로 등록할 수 있습니다."
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
