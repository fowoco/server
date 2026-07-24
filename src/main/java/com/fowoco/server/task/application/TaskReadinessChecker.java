package com.fowoco.server.task.application;

import com.fowoco.server.task.application.port.TaskChecklistStatusRepository;
import com.fowoco.server.task.domain.Task;
import com.fowoco.server.task.domain.TaskStatus;
import org.springframework.stereotype.Component;

@Component
public class TaskReadinessChecker {

    private final TaskChecklistStatusRepository checklistStatusRepository;

    public TaskReadinessChecker(TaskChecklistStatusRepository checklistStatusRepository) {
        this.checklistStatusRepository = checklistStatusRepository;
    }

    public boolean isReadyForReview(Task task) {
        return task.status() == TaskStatus.DRAFT
                && !checklistStatusRepository.existsIncompleteRequiredItem(
                        task.taskId(),
                        task.companyId()
                );
    }
}
