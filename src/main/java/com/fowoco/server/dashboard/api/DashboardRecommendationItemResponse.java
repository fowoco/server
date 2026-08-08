package com.fowoco.server.dashboard.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fowoco.server.task.domain.Task;
import com.fowoco.server.task.domain.TaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(name = "DashboardRecommendationItemResponse", description = "Agent가 준비한 업무 항목")
public final class DashboardRecommendationItemResponse {

    @JsonProperty("task_id")
    @Schema(name = "task_id", format = "uuid")
    private final UUID taskId;

    @JsonProperty("title")
    private final String title;

    @JsonProperty("status")
    private final TaskStatus status;

    private DashboardRecommendationItemResponse(UUID taskId, String title, TaskStatus status) {
        this.taskId = taskId;
        this.title = title;
        this.status = status;
    }

    public static DashboardRecommendationItemResponse from(Task task) {
        return new DashboardRecommendationItemResponse(task.taskId(), task.title(), task.status());
    }

    public UUID getTaskId() {
        return taskId;
    }

    public String getTitle() {
        return title;
    }

    public TaskStatus getStatus() {
        return status;
    }
}
