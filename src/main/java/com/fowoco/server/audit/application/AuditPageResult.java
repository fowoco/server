package com.fowoco.server.audit.application;

import java.util.List;

public record AuditPageResult(List<AuditEventView> items, String nextCursor) {

    public AuditPageResult {
        items = List.copyOf(items);
    }
}
