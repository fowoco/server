package com.fowoco.server.approval.infrastructure.persistence;

import com.fowoco.server.approval.application.port.ExternalSubmissionRepository;
import com.fowoco.server.approval.domain.ExternalSubmission;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaExternalSubmissionRepository implements ExternalSubmissionRepository {

    private final SpringDataExternalSubmissionJpaRepository repository;

    public JpaExternalSubmissionRepository(SpringDataExternalSubmissionJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public ExternalSubmission save(ExternalSubmission submission) {
        return repository.save(new ExternalSubmissionJpaEntity(submission)).toDomain();
    }

    @Override
    public boolean existsByTaskIdAndCompanyId(UUID taskId, UUID companyId) {
        return repository.existsByTaskIdAndCompanyId(taskId, companyId);
    }
}
