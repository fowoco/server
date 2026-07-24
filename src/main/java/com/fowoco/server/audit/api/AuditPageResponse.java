package com.fowoco.server.audit.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fowoco.server.audit.application.AuditPageResult;
import java.util.List;

public record AuditPageResponse(
        List<AuditEventResponse> items,
        @JsonProperty("next_cursor") String nextCursor
) {

    public static AuditPageResponse from(AuditPageResult result) {
        return new AuditPageResponse(
                result.items().stream().map(AuditEventResponse::from).toList(),
                result.nextCursor()
        );
    }
}
