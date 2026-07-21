package com.fowoco.server.common.error;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        @JsonProperty("request_id") String requestId,
        @JsonProperty("field_errors") List<FieldErrorResponse> fieldErrors
) {
}
