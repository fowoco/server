package com.fowoco.server.notification.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class Notification {

    private final UUID notificationId;
    private final UUID companyId;
    private final UUID userId;
    private final NotificationTargetType targetType;
    private final UUID targetId;
    private final String route;
    private final String title;
    private final boolean read;
    private final Instant occurredAt;
    private final Instant createdAt;

    public Notification(
            UUID notificationId,
            UUID companyId,
            UUID userId,
            NotificationTargetType targetType,
            UUID targetId,
            String route,
            String title,
            boolean read,
            Instant occurredAt,
            Instant createdAt
    ) {
        this.notificationId = Objects.requireNonNull(notificationId, "notificationId must not be null");
        this.companyId = Objects.requireNonNull(companyId, "companyId must not be null");
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.targetType = Objects.requireNonNull(targetType, "targetType must not be null");
        this.targetId = Objects.requireNonNull(targetId, "targetId must not be null");
        this.route = requireNonBlank(route, "route");
        this.title = requireNonBlank(title, "title");
        this.read = read;
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public static Notification create(
            UUID notificationId,
            UUID companyId,
            UUID userId,
            NotificationTargetType targetType,
            UUID targetId,
            String route,
            String title,
            Instant occurredAt,
            Instant now
    ) {
        return new Notification(
                notificationId, companyId, userId, targetType, targetId, route, title, false, occurredAt, now
        );
    }

    public Notification markAsRead() {
        if (read) {
            return this;
        }
        return new Notification(
                notificationId, companyId, userId, targetType, targetId, route, title, true, occurredAt, createdAt
        );
    }

    public UUID notificationId() {
        return notificationId;
    }

    public UUID companyId() {
        return companyId;
    }

    public UUID userId() {
        return userId;
    }

    public NotificationTargetType targetType() {
        return targetType;
    }

    public UUID targetId() {
        return targetId;
    }

    public String route() {
        return route;
    }

    public String title() {
        return title;
    }

    public boolean read() {
        return read;
    }

    public Instant occurredAt() {
        return occurredAt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
