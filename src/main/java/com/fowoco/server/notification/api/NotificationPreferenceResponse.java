package com.fowoco.server.notification.api;

import com.fowoco.server.notification.application.NotificationPreferenceService.PreferenceState;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "NotificationPreferenceResponse", description = "알림 유형별 수신 설정")
public record NotificationPreferenceResponse(
        @Schema(description = "알림 유형 key", example = "due-soon")
        String key,
        @Schema(description = "수신 여부")
        boolean enabled,
        @Schema(description = "필수 알림 여부. true면 enabled를 false로 바꿀 수 없습니다.")
        boolean required
) {

    public static NotificationPreferenceResponse from(PreferenceState state) {
        return new NotificationPreferenceResponse(
                state.key().key(),
                state.enabled(),
                state.key().required()
        );
    }
}
