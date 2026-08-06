package com.fowoco.server.casework.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fowoco.server.casework.application.CaseReadiness;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CaseReadinessResponse(
        int completedChecklistItems,
        int totalChecklistItems,
        int verifiedDocuments,
        int totalDocuments,
        int pendingApprovals,
        int approvedApprovals,
        int workerResponses,
        int evidenceItems
) {
    static CaseReadinessResponse from(CaseReadiness readiness) {
        return new CaseReadinessResponse(
                readiness.completedChecklistItems(),
                readiness.totalChecklistItems(),
                readiness.verifiedDocuments(),
                readiness.totalDocuments(),
                readiness.pendingApprovals(),
                readiness.approvedApprovals(),
                readiness.workerResponses(),
                readiness.evidenceItems()
        );
    }
}
