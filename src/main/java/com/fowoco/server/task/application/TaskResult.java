package com.fowoco.server.task.application;

import com.fowoco.server.task.domain.Task;
import com.fowoco.server.task.domain.TaskChecklistItem;
import java.util.List;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record TaskResult(
        Task task,
        Map<String, Object> businessData,
        List<TaskChecklistItem> checklistItems,
        List<String> missingRequiredSlots
) {
    public TaskResult {
        businessData = Collections.unmodifiableMap(new LinkedHashMap<>(businessData));
        checklistItems = List.copyOf(checklistItems);
        missingRequiredSlots = List.copyOf(missingRequiredSlots);
    }
}
