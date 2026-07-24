package com.fowoco.server.task.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataTaskTransitionJpaRepository
        extends JpaRepository<TaskTransitionJpaEntity, UUID> {
}
