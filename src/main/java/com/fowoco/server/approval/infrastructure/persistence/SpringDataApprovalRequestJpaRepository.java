package com.fowoco.server.approval.infrastructure.persistence;

import com.fowoco.server.approval.domain.ApprovalStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataApprovalRequestJpaRepository
        extends JpaRepository<ApprovalRequestJpaEntity, UUID> {

    Optional<ApprovalRequestJpaEntity> findByApprovalRequestIdAndCompanyId(
            UUID approvalRequestId,
            UUID companyId
    );

    Optional<ApprovalRequestJpaEntity> findFirstByTaskIdAndCompanyIdAndStatusOrderByRequestedAtDesc(
            UUID taskId,
            UUID companyId,
            ApprovalStatus status
    );

    Optional<ApprovalRequestJpaEntity> findFirstByTaskIdAndCompanyIdAndStatusOrderByDecidedAtDesc(
            UUID taskId,
            UUID companyId,
            ApprovalStatus status
    );

    List<ApprovalRequestJpaEntity> findAllByTaskIdAndCompanyIdAndStatusIn(
            UUID taskId,
            UUID companyId,
            List<ApprovalStatus> statuses
    );
}
