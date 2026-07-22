package com.fowoco.server.auth.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fowoco.server.auth.api.validation.Utf8ByteLength;
import com.fowoco.server.auth.application.LoginCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "LoginRequest", description = "사업장 사용자 로그인 요청")
public final class LoginRequest {

    @Schema(
            description = "로그인 이메일. 앞뒤 공백을 제거하고 소문자로 정규화해 조회합니다.",
            example = "hr@example.com",
            format = "email",
            maxLength = 254,
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "이메일을 입력해 주세요.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    @Size(max = 254, message = "이메일은 254자 이하여야 합니다.")
    private final String email;

    @Schema(
            description = "로그인 비밀번호. 128자 및 UTF-8 기준 72바이트 이하여야 하며 로그에 남기지 않습니다.",
            format = "password",
            maxLength = 128,
            accessMode = Schema.AccessMode.WRITE_ONLY,
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "비밀번호를 입력해 주세요.")
    @Size(max = 128, message = "비밀번호는 128자 이하여야 합니다.")
    @Utf8ByteLength(max = 72, message = "비밀번호는 UTF-8 기준 72바이트 이하여야 합니다.")
    private final String password;

    @JsonCreator
    public LoginRequest(
            @JsonProperty("email") String email,
            @JsonProperty("password") String password
    ) {
        this.email = email;
        this.password = password;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public LoginCommand toCommand() {
        return new LoginCommand(email, password);
    }
}
