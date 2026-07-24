package com.fowoco.server.task.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fowoco.server.task.application.TaskPageResult;
import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TaskPageResponse(
        List<TaskSummaryResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    static TaskPageResponse from(TaskPageResult result) {
        return new TaskPageResponse(
                result.items().stream().map(TaskSummaryResponse::from).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages()
        );
    }
}
