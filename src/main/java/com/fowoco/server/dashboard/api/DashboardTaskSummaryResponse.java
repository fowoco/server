package com.fowoco.server.dashboard.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fowoco.server.task.domain.Task;
import com.fowoco.server.task.domain.TaskStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.UUID;

@Schema(name = "DashboardTaskSummaryResponse", description = "대시보드에 표시할 업무 요약")
public final class DashboardTaskSummaryResponse {

    @JsonProperty("task_id")
    @Schema(name = "task_id", format = "uuid")
    private final UUID taskId;

    @JsonProperty("worker_id")
    @Schema(name = "worker_id", format = "uuid")
    private final UUID workerId;

    @JsonProperty("title")
    private final String title;

    @JsonProperty("status")
    private final TaskStatus status;

    @JsonProperty("due_date")
    @Schema(name = "due_date", format = "date")
    private final LocalDate dueDate;

    private DashboardTaskSummaryResponse(UUID taskId, UUID workerId, String title, TaskStatus status, LocalDate dueDate) {
        this.taskId = taskId;
        this.workerId = workerId;
        this.title = title;
        this.status = status;
        this.dueDate = dueDate;
    }

    public static DashboardTaskSummaryResponse from(Task task) {
        return new DashboardTaskSummaryResponse(
                task.taskId(),
                task.workerId(),
                task.title(),
                task.status(),
                task.dueDate()
        );
    }

    public UUID getTaskId() {
        return taskId;
    }

    public UUID getWorkerId() {
        return workerId;
    }

    public String getTitle() {
        return title;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }
}
