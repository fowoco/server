package com.fowoco.server.task.application.port;

import java.time.Instant;
import java.util.UUID;

public interface TaskCaseLifecycleUpdater {

    boolean completeIfAllTasksFinished(UUID caseId, UUID companyId, Instant completedAt);
}
