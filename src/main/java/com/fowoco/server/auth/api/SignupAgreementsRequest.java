package com.fowoco.server.auth.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fowoco.server.auth.application.SignupAgreements;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@Schema(name = "SignupAgreementsRequest", description = "가입 시 약관별 동의 결과와 버전")
public final class SignupAgreementsRequest {

    @JsonProperty("service_terms")
    @Valid
    @NotNull(message = "서비스 이용약관 동의 정보를 입력해 주세요.")
    private final AgreementAcceptanceRequest serviceTerms;

    @JsonProperty("privacy_policy")
    @Valid
    @NotNull(message = "개인정보 처리방침 동의 정보를 입력해 주세요.")
    private final AgreementAcceptanceRequest privacyPolicy;

    @Valid
    @NotNull(message = "마케팅 정보 수신 동의 정보를 입력해 주세요.")
    private final AgreementAcceptanceRequest marketing;

    @JsonCreator
    public SignupAgreementsRequest(
            @JsonProperty("service_terms") AgreementAcceptanceRequest serviceTerms,
            @JsonProperty("privacy_policy") AgreementAcceptanceRequest privacyPolicy,
            @JsonProperty("marketing") AgreementAcceptanceRequest marketing
    ) {
        this.serviceTerms = serviceTerms;
        this.privacyPolicy = privacyPolicy;
        this.marketing = marketing;
    }

    public AgreementAcceptanceRequest getServiceTerms() {
        return serviceTerms;
    }

    public AgreementAcceptanceRequest getPrivacyPolicy() {
        return privacyPolicy;
    }

    public AgreementAcceptanceRequest getMarketing() {
        return marketing;
    }

    SignupAgreements toAgreements() {
        return new SignupAgreements(
                serviceTerms.toAcceptance(),
                privacyPolicy.toAcceptance(),
                marketing.toAcceptance()
        );
    }
}
