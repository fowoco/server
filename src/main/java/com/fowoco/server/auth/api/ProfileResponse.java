package com.fowoco.server.auth.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fowoco.server.auth.application.ProfileSnapshot;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(name = "ProfileResponse", description = "현재 사용자의 개인 프로필")
public record ProfileResponse(
        @JsonProperty("display_name")
        @Schema(name = "display_name", description = "화면 표시 이름")
        String displayName,
        @Schema(description = "연락처 (선택 입력, 미등록 시 null)", example = "010-1234-5678")
        String phone,
        @Schema(description = "사업장 내 역할", allowableValues = {"ADMIN", "HR", "VIEWER"}, example = "HR")
        String role,
        @JsonProperty("account_status")
        @Schema(
                name = "account_status",
                description = "계정 상태",
                allowableValues = {"ACTIVE", "SUSPENDED", "DISABLED"},
                example = "ACTIVE"
        )
        String accountStatus,
        @JsonProperty("password_changed_at")
        @Schema(name = "password_changed_at", description = "마지막 비밀번호 변경 시각(가입 시 최초 설정 포함)")
        Instant passwordChangedAt,
        @JsonProperty("last_login_at")
        @Schema(name = "last_login_at", description = "가장 최근 로그인 성공 시각")
        Instant lastLoginAt,
        @JsonProperty("last_login_device")
        @Schema(name = "last_login_device", description = "가장 최근 로그인 기기 (User-Agent 기반 추정)", example = "Chrome · macOS")
        String lastLoginDevice,
        @JsonProperty("recent_device_count")
        @Schema(name = "recent_device_count", description = "최근 로그인 이력에서 확인된 서로 다른 기기 수")
        int recentDeviceCount
) {

    public static ProfileResponse from(ProfileSnapshot snapshot) {
        return new ProfileResponse(
                snapshot.account().displayName(),
                snapshot.account().phone(),
                snapshot.account().role().name(),
                snapshot.account().status().name(),
                snapshot.account().passwordChangedAt(),
                snapshot.lastLoginAt(),
                snapshot.lastLoginDevice(),
                snapshot.recentDeviceCount()
        );
    }
}
