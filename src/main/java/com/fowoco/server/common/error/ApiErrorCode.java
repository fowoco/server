package com.fowoco.server.common.error;

import org.springframework.http.HttpStatus;

public interface ApiErrorCode {

    String code();

    HttpStatus status();

    String defaultMessage();
}
