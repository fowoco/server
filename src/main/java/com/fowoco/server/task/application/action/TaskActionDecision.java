package com.fowoco.server.task.application.action;

import java.util.List;

public record TaskActionDecision(
        TaskAvailableAction nextAction,
        List<TaskAvailableAction> availableActions,
        String blockedReason
) {
    public TaskActionDecision {
        availableActions = List.copyOf(availableActions);
    }

    public static TaskActionDecision of(
            TaskAvailableAction nextAction,
            String blockedReason,
            TaskAvailableAction... availableActions
    ) {
        return new TaskActionDecision(nextAction, List.of(availableActions), blockedReason);
    }

    public static TaskActionDecision terminal() {
        return new TaskActionDecision(null, List.of(), "TASK_TERMINAL");
    }
}
