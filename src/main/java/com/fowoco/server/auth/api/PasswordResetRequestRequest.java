package com.fowoco.server.auth.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "PasswordResetRequestRequest", description = "비밀번호 재설정 링크 요청")
public final class PasswordResetRequestRequest {

    @Schema(
            description = "가입 여부를 확인할 이메일. 계정 존재 여부와 관계없이 같은 응답을 반환합니다.",
            example = "name@company.com",
            format = "email",
            maxLength = 254,
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "이메일을 입력해 주세요.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    @Size(max = 254, message = "이메일은 254자 이하여야 합니다.")
    private final String email;

    @JsonCreator
    public PasswordResetRequestRequest(@JsonProperty("email") String email) {
        this.email = email == null ? null : email.strip();
    }

    public String getEmail() {
        return email;
    }
}
