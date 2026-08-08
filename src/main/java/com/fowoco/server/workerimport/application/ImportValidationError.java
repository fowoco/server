package com.fowoco.server.workerimport.application;

public record ImportValidationError(String field, String code, String message) {
}
