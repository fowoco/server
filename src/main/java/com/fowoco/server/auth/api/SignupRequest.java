package com.fowoco.server.auth.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fowoco.server.auth.api.validation.Utf8ByteLength;
import com.fowoco.server.auth.application.SignupCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(name = "SignupRequest", description = "사업장과 최초 ADMIN 계정 생성 요청")
public final class SignupRequest {

    @JsonProperty("company_name")
    @Schema(
            name = "company_name",
            description = "가입할 사업장 표시 이름",
            example = "한빛정밀",
            maxLength = 120,
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "사업장명을 입력해 주세요.")
    @Size(max = 120, message = "사업장명은 120자 이하여야 합니다.")
    @Pattern(regexp = "^[^\\p{Cc}]+$", message = "사업장명에 제어 문자를 사용할 수 없습니다.")
    private final String companyName;

    @JsonProperty("display_name")
    @Schema(
            name = "display_name",
            description = "최초 담당자의 화면 표시 이름",
            example = "김경민",
            maxLength = 80,
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "담당자 이름을 입력해 주세요.")
    @Size(max = 80, message = "담당자 이름은 80자 이하여야 합니다.")
    @Pattern(regexp = "^[^\\p{Cc}]+$", message = "담당자 이름에 제어 문자를 사용할 수 없습니다.")
    private final String displayName;

    @Schema(
            description = "로그인에 사용할 이메일. 앞뒤 공백 제거 후 소문자로 정규화합니다.",
            example = "name@company.com",
            format = "email",
            maxLength = 254,
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "이메일을 입력해 주세요.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    @Size(max = 254, message = "이메일은 254자 이하여야 합니다.")
    private final String email;

    @Schema(
            description = "로그인 비밀번호. UTF-8 기준 72바이트 이하이며 "
                    + "원문은 저장하지 않고 BCrypt hash만 저장합니다.",
            format = "password",
            minLength = 8,
            maxLength = 128,
            accessMode = Schema.AccessMode.WRITE_ONLY,
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "비밀번호를 입력해 주세요.")
    @Size(min = 8, max = 128, message = "비밀번호는 8자 이상 128자 이하여야 합니다.")
    @Utf8ByteLength(max = 72, message = "비밀번호는 UTF-8 기준 72바이트 이하여야 합니다.")
    private final String password;

    @JsonCreator
    public SignupRequest(
            @JsonProperty("company_name") String companyName,
            @JsonProperty("display_name") String displayName,
            @JsonProperty("email") String email,
            @JsonProperty("password") String password
    ) {
        this.companyName = stripNullable(companyName);
        this.displayName = stripNullable(displayName);
        this.email = stripNullable(email);
        this.password = password;
    }

    public String getCompanyName() {
        return companyName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public SignupCommand toCommand() {
        return new SignupCommand(companyName, displayName, email, password);
    }

    private static String stripNullable(String value) {
        return value == null ? null : value.strip();
    }
}
