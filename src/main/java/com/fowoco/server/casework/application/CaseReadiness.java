package com.fowoco.server.casework.application;

public record CaseReadiness(
        int completedChecklistItems,
        int totalChecklistItems,
        int verifiedDocuments,
        int totalDocuments,
        int pendingApprovals,
        int approvedApprovals,
        int workerResponses,
        int evidenceItems
) {
}
