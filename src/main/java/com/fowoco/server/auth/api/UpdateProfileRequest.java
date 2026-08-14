package com.fowoco.server.auth.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(name = "UpdateProfileRequest", description = "현재 사용자의 개인 프로필 수정 요청")
public final class UpdateProfileRequest {

    @JsonProperty("display_name")
    @Schema(
            name = "display_name",
            description = "화면 표시 이름",
            example = "김경민",
            maxLength = 80,
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "표시 이름을 입력해 주세요.")
    @Size(max = 80, message = "표시 이름은 80자 이하여야 합니다.")
    @Pattern(regexp = "^[^\\p{Cc}]+$", message = "표시 이름에 제어 문자를 사용할 수 없습니다.")
    private final String displayName;

    @Schema(
            description = "연락처 (선택, 빈 문자열이면 삭제)",
            example = "010-1234-5678",
            maxLength = 30,
            requiredMode = Schema.RequiredMode.NOT_REQUIRED
    )
    @Size(max = 30, message = "연락처는 30자 이하여야 합니다.")
    @Pattern(regexp = "^[0-9+()\\-\\s]*$", message = "연락처 형식이 올바르지 않습니다.")
    private final String phone;

    @JsonCreator
    public UpdateProfileRequest(
            @JsonProperty("display_name") String displayName,
            @JsonProperty("phone") String phone
    ) {
        this.displayName = displayName == null ? null : displayName.strip();
        this.phone = phone == null ? null : phone.strip();
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPhone() {
        return phone;
    }
}
