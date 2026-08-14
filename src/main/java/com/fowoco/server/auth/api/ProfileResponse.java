package com.fowoco.server.auth.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fowoco.server.auth.domain.UserAccount;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ProfileResponse", description = "현재 사용자의 개인 프로필")
public record ProfileResponse(
        @JsonProperty("display_name")
        @Schema(name = "display_name", description = "화면 표시 이름")
        String displayName,
        @Schema(description = "연락처 (선택 입력, 미등록 시 null)", example = "010-1234-5678")
        String phone
) {

    public static ProfileResponse from(UserAccount userAccount) {
        return new ProfileResponse(userAccount.displayName(), userAccount.phone());
    }
}
