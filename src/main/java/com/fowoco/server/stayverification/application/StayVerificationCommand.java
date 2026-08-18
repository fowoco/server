package com.fowoco.server.stayverification.application;

import com.fowoco.server.stayverification.domain.StayVerificationStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record StayVerificationCommand(
        UUID stayVerificationId,
        StayVerificationStatus status,
        LocalDate extensionAppliedAt,
        UUID extensionReceiptDocumentId,
        UUID approvalResultDocumentId,
        LocalDate newStayExpiryDate,
        String officialConsultationNote,
        Instant employmentEndConfirmedAt,
        LocalDate recheckDate,
        long expectedVersion
) {
}
