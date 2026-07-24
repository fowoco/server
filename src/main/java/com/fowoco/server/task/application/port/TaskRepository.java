package com.fowoco.server.task.application.port;

import com.fowoco.server.task.domain.Task;
import com.fowoco.server.task.domain.TaskStatus;
import com.fowoco.server.task.domain.TaskType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskRepository {

    Optional<Task> findByIdAndCompanyId(UUID taskId, UUID companyId);

    TaskPage findAll(TaskSearchCriteria criteria);

    Task save(Task task);

    record TaskSearchCriteria(
            UUID companyId,
            TaskStatus status,
            TaskType taskType,
            UUID workerId,
            LocalDate dueFrom,
            LocalDate dueTo,
            String keyword,
            int page,
            int size
    ) {
    }

    record TaskPage(
            List<Task> items,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
        public TaskPage {
            items = List.copyOf(items);
        }
    }
}
