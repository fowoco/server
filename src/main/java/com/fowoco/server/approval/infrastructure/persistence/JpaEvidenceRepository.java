package com.fowoco.server.approval.infrastructure.persistence;

import com.fowoco.server.approval.application.port.EvidenceRepository;
import com.fowoco.server.approval.domain.Evidence;
import com.fowoco.server.approval.domain.EvidenceType;
import java.util.Set;
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
    public Set<EvidenceType> findTypesByTaskIdAndCompanyId(UUID taskId, UUID companyId) {
        return Set.copyOf(repository.findTypesByTaskIdAndCompanyId(taskId, companyId));
    }
}
