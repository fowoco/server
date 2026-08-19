package com.fowoco.server.auth.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fowoco.server.auth.api.validation.Utf8ByteLength;
import com.fowoco.server.auth.api.validation.PasswordPolicy;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(name = "PasswordResetCompleteRequest", description = "비밀번호 재설정 완료 요청")
public final class PasswordResetCompleteRequest {

    @Schema(
            description = "재설정 안내에서 전달받은 1회용 token",
            example = "3q2-7w8qgXBkCjKhpO4_ZZ5cQJ0iJg3mATuwcxoOg30",
            minLength = 43,
            maxLength = 43,
            accessMode = Schema.AccessMode.WRITE_ONLY,
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "비밀번호 재설정 token을 입력해 주세요.")
    @Pattern(regexp = "^[A-Za-z0-9_-]{43}$", message = "비밀번호 재설정 token 형식이 올바르지 않습니다.")
    private final String token;

    @JsonProperty("new_password")
    @Schema(
            name = "new_password",
            description = "새 비밀번호. 원문은 저장하지 않고 BCrypt hash만 저장합니다.",
            format = "password",
            minLength = PasswordPolicy.MIN_LENGTH,
            maxLength = PasswordPolicy.MAX_LENGTH,
            accessMode = Schema.AccessMode.WRITE_ONLY,
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "새 비밀번호를 입력해 주세요.")
    @Size(
            min = PasswordPolicy.MIN_LENGTH,
            max = PasswordPolicy.MAX_LENGTH,
            message = "비밀번호는 8자 이상 128자 이하여야 합니다."
    )
    @Pattern(
            regexp = PasswordPolicy.LETTER_AND_DIGIT_PATTERN,
            message = "비밀번호에는 영문과 숫자가 각각 하나 이상 포함되어야 합니다."
    )
    @Utf8ByteLength(
            max = PasswordPolicy.MAX_UTF8_BYTES,
            message = "비밀번호는 UTF-8 기준 72바이트 이하여야 합니다."
    )
    private final String newPassword;

    @JsonCreator
    public PasswordResetCompleteRequest(
            @JsonProperty("token") String token,
            @JsonProperty("new_password") String newPassword
    ) {
        this.token = token;
        this.newPassword = newPassword;
    }

    public String getToken() {
        return token;
    }

    public String getNewPassword() {
        return newPassword;
    }
}
