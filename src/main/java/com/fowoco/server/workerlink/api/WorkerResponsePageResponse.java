package com.fowoco.server.workerlink.api;

import com.fowoco.server.workerlink.application.WorkerResponsePageResult;
import java.util.List;

public record WorkerResponsePageResponse(
        List<WorkerResponseItemResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    static WorkerResponsePageResponse from(WorkerResponsePageResult result) {
        return new WorkerResponsePageResponse(
                result.items().stream().map(WorkerResponseItemResponse::from).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages()
        );
    }
}
