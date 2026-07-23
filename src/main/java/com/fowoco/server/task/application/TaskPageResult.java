package com.fowoco.server.task.application;

import com.fowoco.server.task.domain.Task;
import java.util.List;

public record TaskPageResult(
        List<Task> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public TaskPageResult {
        items = List.copyOf(items);
    }
}
