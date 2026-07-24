package com.fowoco.server.task.infrastructure.persistence;

import com.fowoco.server.task.application.port.TaskTransitionRecorder;
import com.fowoco.server.task.domain.TaskStatus;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaTaskTransitionRecorder implements TaskTransitionRecorder {

    private final SpringDataTaskTransitionJpaRepository repository;

    public JpaTaskTransitionRecorder(SpringDataTaskTransitionJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void record(
            UUID transitionId,
            UUID taskId,
            UUID companyId,
            TaskStatus fromStatus,
            TaskStatus toStatus,
            UUID actorId,
            String reason,
            String requestId,
            Instant createdAt
    ) {
        repository.save(new TaskTransitionJpaEntity(
                transitionId,
                taskId,
                companyId,
                fromStatus,
                toStatus,
                actorId,
                reason,
                requestId,
                createdAt
        ));
    }
}
