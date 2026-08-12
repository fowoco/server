package com.fowoco.server.audit.application;

import java.util.List;

public record WorkerActivityPageResult(
        List<WorkerActivityView> items,
        String nextCursor
) {
    public WorkerActivityPageResult {
        items = List.copyOf(items);
    }
}
