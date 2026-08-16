package com.fowoco.server.stayverification.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fowoco.server.stayverification.domain.StayVerificationStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record StayVerificationUpdateRequest(
        @NotNull StayVerificationStatus status,
        @JsonProperty("extension_applied_at") LocalDate extensionAppliedAt,
        @JsonProperty("extension_receipt_document_id") UUID extensionReceiptDocumentId,
        @JsonProperty("approval_result_document_id") UUID approvalResultDocumentId,
        @JsonProperty("new_stay_expiry_date") LocalDate newStayExpiryDate,
        @JsonProperty("official_consultation_note")
        @Size(max = 1000) String officialConsultationNote,
        @JsonProperty("employment_end_confirmed_at") Instant employmentEndConfirmedAt,
        @JsonProperty("recheck_date") LocalDate recheckDate,
        @JsonProperty("expected_version") @NotNull @PositiveOrZero Long expectedVersion
) {
}
