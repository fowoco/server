package com.fowoco.server.approval.infrastructure.persistence;

import com.fowoco.server.approval.application.port.EvidenceRepository;
import com.fowoco.server.approval.domain.Evidence;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaEvidenceRepository implements EvidenceRepository {

    private final SpringDataEvidenceJpaRepository repository;

    public JpaEvidenceRepository(SpringDataEvidenceJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Evidence save(Evidence evidence) {
        return repository.save(new EvidenceJpaEntity(evidence)).toDomain();
    }

    @Override
    public boolean existsByTaskIdAndCompanyId(UUID taskId, UUID companyId) {
        return repository.existsByTaskIdAndCompanyId(taskId, companyId);
    }
}
