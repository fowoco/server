package com.fowoco.server.notification.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(name = "UpdateNotificationPreferenceRequest", description = "알림 유형 수신 설정 변경 요청")
public final class UpdateNotificationPreferenceRequest {

    @Schema(description = "수신 여부", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "enabled 값을 입력해 주세요.")
    private final Boolean enabled;

    @JsonCreator
    public UpdateNotificationPreferenceRequest(@JsonProperty("enabled") Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean getEnabled() {
        return enabled;
    }
}
