package com.fowoco.server.task.application.port;

import com.fowoco.server.task.domain.TaskStatus;
import java.time.Instant;
import java.util.UUID;

public interface TaskTransitionRecorder {

    void record(
            UUID transitionId,
            UUID taskId,
            UUID companyId,
            TaskStatus fromStatus,
            TaskStatus toStatus,
            UUID actorId,
            String reason,
            String requestId,
            Instant createdAt
    );
}
