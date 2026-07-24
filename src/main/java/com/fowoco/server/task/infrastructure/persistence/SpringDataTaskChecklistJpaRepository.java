package com.fowoco.server.task.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataTaskChecklistJpaRepository
        extends JpaRepository<TaskChecklistItemJpaEntity, UUID> {

    List<TaskChecklistItemJpaEntity> findAllByTaskIdAndCompanyIdOrderByCreatedAtAsc(
            UUID taskId,
            UUID companyId
    );

    Optional<TaskChecklistItemJpaEntity> findByChecklistItemIdAndTaskIdAndCompanyId(
            UUID checklistItemId,
            UUID taskId,
            UUID companyId
    );
}
