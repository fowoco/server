package com.fowoco.server.workerlink.application;

import java.util.List;

public record WorkerResponsePageResult(
        List<WorkerResponseQueryResult> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public WorkerResponsePageResult {
        items = List.copyOf(items);
    }
}
