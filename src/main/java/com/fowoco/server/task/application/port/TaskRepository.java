package com.fowoco.server.task.application.port;

import com.fowoco.server.task.domain.Task;
import java.util.Optional;
import java.util.UUID;

public interface TaskRepository {

    Optional<Task> findByIdAndCompanyId(UUID taskId, UUID companyId);

    Task save(Task task);
}
