package com.fowoco.server.stayverification.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fowoco.server.stayverification.domain.StayVerificationCase;
import com.fowoco.server.stayverification.domain.StayVerificationStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record StayVerificationResponse(
        @JsonProperty("stay_verification_id") UUID stayVerificationId,
        @JsonProperty("worker_id") UUID workerId,
        @JsonProperty("worker_display_name") String workerDisplayName,
        @JsonProperty("source_stay_expiry_date") LocalDate sourceStayExpiryDate,
        @JsonProperty("verification_status") StayVerificationStatus verificationStatus,
        @JsonProperty("status_checked_at") Instant statusCheckedAt,
        @JsonProperty("extension_applied_at") LocalDate extensionAppliedAt,
        @JsonProperty("extension_receipt_document_id") UUID extensionReceiptDocumentId,
        @JsonProperty("approval_result_document_id") UUID approvalResultDocumentId,
        @JsonProperty("new_stay_expiry_date") LocalDate newStayExpiryDate,
        @JsonProperty("official_consultation_note") String officialConsultationNote,
        @JsonProperty("employment_end_confirmed_at") Instant employmentEndConfirmedAt,
        @JsonProperty("recheck_date") LocalDate recheckDate,
        @JsonProperty("employment_change_candidate_available") boolean employmentChangeCandidateAvailable,
        @JsonProperty("suggested_workflow_id") String suggestedWorkflowId,
        long version
) {
    public static StayVerificationResponse from(StayVerificationCase value) {
        return new StayVerificationResponse(
                value.stayVerificationId(),
                value.workerId(),
                value.workerDisplayName(),
                value.sourceStayExpiryDate(),
                value.verificationStatus(),
                value.statusCheckedAt(),
                value.extensionAppliedAt(),
                value.extensionReceiptDocumentId(),
                value.approvalResultDocumentId(),
                value.newStayExpiryDate(),
                value.officialConsultationNote(),
                value.employmentEndConfirmedAt(),
                value.recheckDate(),
                value.employmentChangeCandidateAvailable(),
                value.employmentChangeCandidateAvailable() ? "WF-CHG-001" : null,
                value.version()
        );
    }
}
