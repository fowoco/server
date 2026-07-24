package com.fowoco.server.task.application.port;

import java.util.UUID;

public interface TaskChecklistStatusRepository {

    boolean existsIncompleteRequiredItem(UUID taskId, UUID companyId);
}
