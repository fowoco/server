package com.fowoco.server.approval.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataExternalSubmissionJpaRepository
        extends JpaRepository<ExternalSubmissionJpaEntity, UUID> {

    boolean existsByTaskIdAndCompanyId(UUID taskId, UUID companyId);
}
