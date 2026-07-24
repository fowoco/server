package com.fowoco.server.approval.application.port;

import com.fowoco.server.approval.domain.ApprovalRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApprovalRequestRepository {

    Optional<ApprovalRequest> findByIdAndCompanyId(UUID approvalRequestId, UUID companyId);

    Optional<ApprovalRequest> findPendingByTaskIdAndCompanyId(UUID taskId, UUID companyId);

    Optional<ApprovalRequest> findLatestApprovedByTaskIdAndCompanyId(UUID taskId, UUID companyId);

    List<ApprovalRequest> findActiveByTaskIdAndCompanyId(UUID taskId, UUID companyId);

    ApprovalRequest save(ApprovalRequest approvalRequest);
}
