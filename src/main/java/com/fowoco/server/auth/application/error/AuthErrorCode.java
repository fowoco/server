package com.fowoco.server.auth.application.error;

import com.fowoco.server.common.error.ApiErrorCode;
import org.springframework.http.HttpStatus;

public enum AuthErrorCode implements ApiErrorCode {
    EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
    INVALID_AGREEMENT_CONSENT(HttpStatus.UNPROCESSABLE_ENTITY, "필수 약관 동의와 약관 버전을 확인해 주세요."),
    INVALID_PASSWORD_RESET_TOKEN(HttpStatus.BAD_REQUEST, "비밀번호 재설정 링크가 유효하지 않습니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호를 확인해 주세요."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "로그인 정보를 갱신할 수 없습니다. 다시 로그인해 주세요.");

    private final HttpStatus status;
    private final String defaultMessage;

    AuthErrorCode(HttpStatus status, String defaultMessage) {
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
