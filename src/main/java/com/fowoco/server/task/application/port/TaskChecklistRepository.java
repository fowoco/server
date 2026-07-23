package com.fowoco.server.task.application.port;

import com.fowoco.server.task.domain.TaskChecklistItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskChecklistRepository {

    List<TaskChecklistItem> findAllByTaskIdAndCompanyId(UUID taskId, UUID companyId);

    Optional<TaskChecklistItem> findByIdAndTaskIdAndCompanyId(
            UUID checklistItemId,
            UUID taskId,
            UUID companyId
    );

    List<TaskChecklistItem> saveAll(List<TaskChecklistItem> items);

    TaskChecklistItem save(TaskChecklistItem item);
}
