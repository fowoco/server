package com.fowoco.server.auth.infrastructure.persistence;

import com.fowoco.server.auth.domain.AgreementType;
import com.fowoco.server.auth.domain.UserAgreementConsent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_agreement_consent")
public class UserAgreementConsentJpaEntity {

    @Id
    @Column(name = "consent_id", nullable = false, updatable = false)
    private UUID consentId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "agreement_type", nullable = false, length = 30, updatable = false)
    private AgreementType agreementType;

    @Column(name = "policy_version", nullable = false, length = 40, updatable = false)
    private String policyVersion;

    @Column(name = "agreed", nullable = false, updatable = false)
    private boolean agreed;

    @Column(name = "request_id", nullable = false, length = 128, updatable = false)
    private String requestId;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    protected UserAgreementConsentJpaEntity() {
    }

    UserAgreementConsentJpaEntity(UserAgreementConsent consent) {
        consentId = consent.consentId();
        companyId = consent.companyId();
        userId = consent.userId();
        agreementType = consent.agreementType();
        policyVersion = consent.policyVersion();
        agreed = consent.agreed();
        requestId = consent.requestId();
        recordedAt = consent.recordedAt();
    }
}
