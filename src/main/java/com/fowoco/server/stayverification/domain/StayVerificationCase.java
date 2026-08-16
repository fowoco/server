package com.fowoco.server.stayverification.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record StayVerificationCase(
        UUID stayVerificationId,
        UUID companyId,
        UUID workerId,
        String workerDisplayName,
        LocalDate sourceStayExpiryDate,
        StayVerificationStatus verificationStatus,
        Instant statusCheckedAt,
        LocalDate extensionAppliedAt,
        UUID extensionReceiptDocumentId,
        UUID approvalResultDocumentId,
        LocalDate newStayExpiryDate,
        String officialConsultationNote,
        Instant employmentEndConfirmedAt,
        LocalDate recheckDate,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    public boolean employmentChangeCandidateAvailable() {
        return verificationStatus == StayVerificationStatus.EMPLOYMENT_ENDED;
    }
}
