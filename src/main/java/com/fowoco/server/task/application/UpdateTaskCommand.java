package com.fowoco.server.task.application;

import java.time.LocalDate;
import java.util.Map;

public record UpdateTaskCommand(
        String title,
        String description,
        LocalDate dueDate,
        Map<String, Object> businessData,
        long expectedVersion
) {
}
