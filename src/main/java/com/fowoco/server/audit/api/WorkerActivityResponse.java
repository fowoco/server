package com.fowoco.server.audit.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fowoco.server.audit.application.WorkerActivityType;
import com.fowoco.server.audit.application.WorkerActivityView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record WorkerActivityResponse(
        @Schema(description = "활동 ID") UUID activityId,
        @Schema(description = "화면용 활동 종류") WorkerActivityType type,
        @Schema(description = "관련 업무카드 ID") UUID taskId,
        @Schema(description = "관련 업무카드 제목") String taskTitle,
        @Schema(description = "개인정보를 제외한 화면용 설명") String summary,
        @Schema(description = "발생 시각") Instant occurredAt
) {
    static WorkerActivityResponse from(WorkerActivityView view) {
        return new WorkerActivityResponse(
                view.activityId(),
                view.type(),
                view.taskId(),
                view.taskTitle(),
                view.summary(),
                view.occurredAt()
        );
    }
}
