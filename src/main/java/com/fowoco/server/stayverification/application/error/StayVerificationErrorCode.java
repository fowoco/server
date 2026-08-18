package com.fowoco.server.stayverification.application.error;

import com.fowoco.server.common.error.ApiErrorCode;
import org.springframework.http.HttpStatus;

public enum StayVerificationErrorCode implements ApiErrorCode {
    STAY_VERIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "체류상태 확인 Case를 찾을 수 없습니다."),
    STAY_VERIFICATION_VERSION_CONFLICT(HttpStatus.CONFLICT, "다른 담당자가 먼저 상태를 변경했습니다."),
    STAY_VERIFICATION_EVIDENCE_REQUIRED(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "상태를 확정하려면 승인 결과 서류 또는 공식 확인 메모가 필요합니다."
    ),
    STAY_VERIFICATION_NEW_EXPIRY_REQUIRED(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "승인 완료 상태에는 기존 만료일보다 늦은 새 체류 만료일이 필요합니다."
    ),
    STAY_VERIFICATION_PENDING_DETAILS_REQUIRED(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "심사 중 상태에는 신청일, 재확인일과 접수 증빙 또는 공식 확인 메모가 필요합니다."
    ),
    STAY_VERIFICATION_EMPLOYMENT_END_NOTE_REQUIRED(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "고용 종료 확인에는 확인 시각과 공식 확인 메모가 필요합니다."
    ),
    STAY_VERIFICATION_DOCUMENT_NOT_FOUND(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "이 근로자에게 연결된 증빙 서류를 찾을 수 없습니다."
    );

    private final HttpStatus status;
    private final String defaultMessage;

    StayVerificationErrorCode(HttpStatus status, String defaultMessage) {
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
