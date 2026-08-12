package com.fowoco.server.task.application;

import com.fowoco.server.task.domain.Task;
import java.util.Objects;

public record TaskSummaryView(
        Task task,
        TaskAssigneeView assignee
) {
    public TaskSummaryView {
        Objects.requireNonNull(task, "task must not be null");
        Objects.requireNonNull(assignee, "assignee must not be null");
    }
}
