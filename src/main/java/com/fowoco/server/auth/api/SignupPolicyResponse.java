package com.fowoco.server.auth.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fowoco.server.auth.api.validation.PasswordPolicy;
import com.fowoco.server.auth.infrastructure.security.AgreementPolicyProperties;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "SignupPolicyResponse", description = "회원가입 화면이 적용할 현재 정책")
public record SignupPolicyResponse(
        @JsonProperty("password_policy") PasswordPolicyResponse passwordPolicy,
        AgreementsPolicyResponse agreements
) {

    private static final String SERVICE_TERMS_PATH = "/legal/terms";
    private static final String PRIVACY_POLICY_PATH = "/legal/privacy";

    public static SignupPolicyResponse from(AgreementPolicyProperties policy) {
        return new SignupPolicyResponse(
                new PasswordPolicyResponse(
                        PasswordPolicy.MIN_LENGTH,
                        PasswordPolicy.MAX_LENGTH,
                        true,
                        true
                ),
                new AgreementsPolicyResponse(
                        new AgreementPolicyResponse(
                                policy.serviceTermsVersion(),
                                true,
                                SERVICE_TERMS_PATH
                        ),
                        new AgreementPolicyResponse(
                                policy.privacyPolicyVersion(),
                                true,
                                PRIVACY_POLICY_PATH
                        ),
                        new AgreementPolicyResponse(
                                policy.marketingVersion(),
                                false,
                                null
                        )
                )
        );
    }

    @Schema(name = "PasswordPolicyResponse", description = "비밀번호 생성 규칙")
    public record PasswordPolicyResponse(
            @JsonProperty("min_length") int minLength,
            @JsonProperty("max_length") int maxLength,
            @JsonProperty("require_letter") boolean requireLetter,
            @JsonProperty("require_digit") boolean requireDigit
    ) {
    }

    @Schema(name = "AgreementsPolicyResponse", description = "현재 약관별 가입 정책")
    public record AgreementsPolicyResponse(
            @JsonProperty("service_terms") AgreementPolicyResponse serviceTerms,
            @JsonProperty("privacy_policy") AgreementPolicyResponse privacyPolicy,
            AgreementPolicyResponse marketing
    ) {
    }

    @Schema(name = "AgreementPolicyResponse", description = "개별 약관의 현재 버전과 필수 여부")
    public record AgreementPolicyResponse(
            String version,
            boolean required,
            @JsonProperty("content_path") String contentPath
    ) {
    }
}
