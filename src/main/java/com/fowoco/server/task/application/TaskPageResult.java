package com.fowoco.server.task.application;

import java.util.List;

public record TaskPageResult(
        List<TaskSummaryView> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public TaskPageResult {
        items = List.copyOf(items);
    }
}
