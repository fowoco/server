package com.fowoco.server.airun.application.error;

import com.fowoco.server.common.error.ApiErrorCode;
import org.springframework.http.HttpStatus;

public enum AiRunErrorCode implements ApiErrorCode {
    AI_RUN_NOT_FOUND(HttpStatus.NOT_FOUND, "AI 분석 요청을 찾을 수 없습니다."),
    AI_RUN_IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "같은 Idempotency-Key가 다른 요청에 이미 사용되었습니다."),
    AI_RUN_VERSION_CONFLICT(HttpStatus.CONFLICT, "다른 요청에서 먼저 변경했습니다. 최신 상태를 다시 확인해 주세요."),
    AI_RUN_ANSWERS_NOT_ALLOWED(HttpStatus.UNPROCESSABLE_ENTITY, "현재 상태에서는 추가 답변을 제출할 수 없습니다."),
    AI_RUN_INVALID_INSTRUCTION(HttpStatus.BAD_REQUEST, "업무 요청 문장을 확인해 주세요."),
    AI_RUN_INVALID_IDEMPOTENCY_KEY(HttpStatus.BAD_REQUEST, "Idempotency-Key를 확인해 주세요."),
    AI_RUN_INVALID_ANSWER(HttpStatus.BAD_REQUEST, "추가 답변의 항목과 값을 확인해 주세요.");

    private final HttpStatus status;
    private final String defaultMessage;

    AiRunErrorCode(HttpStatus status, String defaultMessage) {
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
