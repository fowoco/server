package com.fowoco.server.audit.infrastructure.persistence;

import com.fowoco.server.audit.application.AuditSearchCriteria;
import com.fowoco.server.audit.application.port.AuditEventRepository;
import com.fowoco.server.audit.domain.AuditEvent;
import com.fowoco.server.audit.domain.AuditTargetType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
public class JpaAuditEventRepository implements AuditEventRepository {

    private final SpringDataAuditEventJpaRepository repository;

    public JpaAuditEventRepository(SpringDataAuditEventJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void append(AuditEvent event) {
        repository.save(new AuditEventJpaEntity(event));
    }

    @Override
    public List<AuditEvent> findTaskActivities(UUID companyId, UUID taskId) {
        return repository
                .findTop200ByCompanyIdAndTargetTypeAndTargetIdOrderByCreatedAtAscAuditEventIdAsc(
                        companyId,
                        AuditTargetType.TASK,
                        taskId
                )
                .stream()
                .map(AuditEventJpaEntity::toDomain)
                .toList();
    }

    @Override
    public List<AuditEvent> search(AuditSearchCriteria criteria) {
        return repository.search(
                        criteria.companyId(),
                        criteria.actorType(),
                        criteria.action(),
                        criteria.targetType(),
                        criteria.targetId(),
                        criteria.traceId(),
                        criteria.createdFrom(),
                        criteria.createdTo(),
                        criteria.beforeCreatedAt(),
                        criteria.beforeAuditEventId(),
                        PageRequest.of(0, criteria.limit())
                )
                .stream()
                .map(AuditEventJpaEntity::toDomain)
                .toList();
    }
}
