package com.fowoco.server.auth.application;

import java.util.Objects;

public record SignupAgreements(
        AgreementAcceptance serviceTerms,
        AgreementAcceptance privacyPolicy,
        AgreementAcceptance marketing
) {
    public SignupAgreements {
        Objects.requireNonNull(serviceTerms, "serviceTerms must not be null");
        Objects.requireNonNull(privacyPolicy, "privacyPolicy must not be null");
        Objects.requireNonNull(marketing, "marketing must not be null");
    }
}
