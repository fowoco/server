package com.fowoco.server.notification.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fowoco.server.notification.domain.Notification;
import com.fowoco.server.notification.domain.NotificationTargetType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(name = "NotificationItemResponse", description = "알림 항목")
public final class NotificationItemResponse {

    @JsonProperty("id")
    @Schema(name = "id", format = "uuid")
    private final UUID id;

    @JsonProperty("target_type")
    @Schema(name = "target_type", description = "알림 대상 종류")
    private final NotificationTargetType targetType;

    @JsonProperty("target_id")
    @Schema(name = "target_id", format = "uuid")
    private final UUID targetId;

    @JsonProperty("route")
    @Schema(name = "route", description = "허용된 화면으로 이동할 안전한 내부 경로")
    private final String route;

    @JsonProperty("title")
    private final String title;

    @JsonProperty("read")
    private final boolean read;

    @JsonProperty("occurred_at")
    @Schema(name = "occurred_at", format = "date-time")
    private final Instant occurredAt;

    private NotificationItemResponse(
            UUID id, NotificationTargetType targetType, UUID targetId,
            String route, String title, boolean read, Instant occurredAt
    ) {
        this.id = id;
        this.targetType = targetType;
        this.targetId = targetId;
        this.route = route;
        this.title = title;
        this.read = read;
        this.occurredAt = occurredAt;
    }

    public static NotificationItemResponse from(Notification notification) {
        return new NotificationItemResponse(
                notification.notificationId(),
                notification.targetType(),
                notification.targetId(),
                notification.route(),
                notification.title(),
                notification.read(),
                notification.occurredAt()
        );
    }

    public UUID getId() {
        return id;
    }

    public NotificationTargetType getTargetType() {
        return targetType;
    }

    public UUID getTargetId() {
        return targetId;
    }

    public String getRoute() {
        return route;
    }

    public String getTitle() {
        return title;
    }

    public boolean isRead() {
        return read;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
