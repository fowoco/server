package com.fowoco.server.audit.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fowoco.server.audit.application.WorkerActivityPageResult;
import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record WorkerActivityPageResponse(
        List<WorkerActivityResponse> items,
        String nextCursor
) {
    static WorkerActivityPageResponse from(WorkerActivityPageResult result) {
        return new WorkerActivityPageResponse(
                result.items().stream().map(WorkerActivityResponse::from).toList(),
                result.nextCursor()
        );
    }
}
