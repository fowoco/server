package com.fowoco.server.common.error;

public record FieldErrorResponse(
        String field,
        String message
) {
}
