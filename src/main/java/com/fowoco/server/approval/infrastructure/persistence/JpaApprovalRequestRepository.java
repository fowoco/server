package com.fowoco.server.approval.infrastructure.persistence;

import com.fowoco.server.approval.application.port.ApprovalRequestRepository;
import com.fowoco.server.approval.domain.ApprovalRequest;
import com.fowoco.server.approval.domain.ApprovalStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaApprovalRequestRepository implements ApprovalRequestRepository {

    private final SpringDataApprovalRequestJpaRepository repository;

    public JpaApprovalRequestRepository(SpringDataApprovalRequestJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<ApprovalRequest> findByIdAndCompanyId(UUID approvalRequestId, UUID companyId) {
        return repository.findByApprovalRequestIdAndCompanyId(approvalRequestId, companyId)
                .map(ApprovalRequestJpaEntity::toDomain);
    }

    @Override
    public Optional<ApprovalRequest> findPendingByTaskIdAndCompanyId(UUID taskId, UUID companyId) {
        return repository.findFirstByTaskIdAndCompanyIdAndStatusOrderByRequestedAtDesc(
                        taskId,
                        companyId,
                        ApprovalStatus.PENDING
                )
                .map(ApprovalRequestJpaEntity::toDomain);
    }

    @Override
    public Optional<ApprovalRequest> findLatestApprovedByTaskIdAndCompanyId(
            UUID taskId,
            UUID companyId
    ) {
        return repository.findFirstByTaskIdAndCompanyIdAndStatusOrderByDecidedAtDesc(
                        taskId,
                        companyId,
                        ApprovalStatus.APPROVED
                )
                .map(ApprovalRequestJpaEntity::toDomain);
    }

    @Override
    public List<ApprovalRequest> findActiveByTaskIdAndCompanyId(UUID taskId, UUID companyId) {
        return repository.findAllByTaskIdAndCompanyIdAndStatusIn(
                        taskId,
                        companyId,
                        List.of(ApprovalStatus.PENDING, ApprovalStatus.APPROVED)
                )
                .stream()
                .map(ApprovalRequestJpaEntity::toDomain)
                .toList();
    }

    @Override
    public ApprovalRequest save(ApprovalRequest approvalRequest) {
        ApprovalRequestJpaEntity entity = repository
                .findByApprovalRequestIdAndCompanyId(
                        approvalRequest.approvalRequestId(),
                        approvalRequest.companyId()
                )
                .map(existing -> {
                    existing.apply(approvalRequest);
                    return existing;
                })
                .orElseGet(() -> new ApprovalRequestJpaEntity(approvalRequest));
        return repository.saveAndFlush(entity).toDomain();
    }
}
