package com.fowoco.server.auth.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record UserAgreementConsent(
        UUID consentId,
        UUID companyId,
        UUID userId,
        AgreementType agreementType,
        String policyVersion,
        boolean agreed,
        String requestId,
        Instant recordedAt
) {
    public UserAgreementConsent {
        Objects.requireNonNull(consentId, "consentId must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(agreementType, "agreementType must not be null");
        policyVersion = requireText(policyVersion, "policyVersion", 40);
        requestId = requireText(requestId, "requestId", 128);
        Objects.requireNonNull(recordedAt, "recordedAt must not be null");
    }

    private static String requireText(String value, String name, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        String stripped = value.strip();
        if (stripped.length() > maxLength) {
            throw new IllegalArgumentException(name + " must not exceed " + maxLength + " characters");
        }
        return stripped;
    }
}
