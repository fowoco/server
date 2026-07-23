package com.fowoco.server.task.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataTaskJpaRepository extends JpaRepository<TaskJpaEntity, UUID> {

    Optional<TaskJpaEntity> findByTaskIdAndCompanyId(UUID taskId, UUID companyId);
}
