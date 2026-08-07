package com.fowoco.server.auth.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fowoco.server.auth.application.AgreementAcceptance;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(name = "AgreementAcceptanceRequest", description = "가입 시 화면에 표시된 약관의 동의 결과")
public final class AgreementAcceptanceRequest {

    @Schema(description = "동의 여부", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "약관 동의 여부를 입력해 주세요.")
    private final Boolean agreed;

    @Schema(description = "동의 화면에 표시된 약관 버전", example = "1.0", maxLength = 40)
    @NotBlank(message = "약관 버전을 입력해 주세요.")
    @Size(max = 40, message = "약관 버전은 40자 이하여야 합니다.")
    private final String version;

    @JsonCreator
    public AgreementAcceptanceRequest(
            @JsonProperty("agreed") Boolean agreed,
            @JsonProperty("version") String version
    ) {
        this.agreed = agreed;
        this.version = version == null ? null : version.strip();
    }

    public Boolean getAgreed() {
        return agreed;
    }

    public String getVersion() {
        return version;
    }

    AgreementAcceptance toAcceptance() {
        return new AgreementAcceptance(Boolean.TRUE.equals(agreed), version);
    }
}
