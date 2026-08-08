package com.fowoco.server.notification.infrastructure.persistence;

import com.fowoco.server.notification.domain.Notification;
import com.fowoco.server.notification.domain.NotificationTargetType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "notification")
public class NotificationJpaEntity {

    @Id
    @Column(name = "notification_id", nullable = false, updatable = false)
    private UUID notificationId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 30, updatable = false)
    private NotificationTargetType targetType;

    @Column(name = "target_id", nullable = false, updatable = false)
    private UUID targetId;

    @Column(name = "route", nullable = false, updatable = false)
    private String route;

    @Column(name = "title", nullable = false, updatable = false)
    private String title;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected NotificationJpaEntity() {
    }

    private NotificationJpaEntity(
            UUID notificationId,
            UUID companyId,
            NotificationTargetType targetType,
            UUID targetId,
            String route,
            String title,
            boolean read,
            Instant occurredAt,
            Instant createdAt
    ) {
        this.notificationId = notificationId;
        this.companyId = companyId;
        this.targetType = targetType;
        this.targetId = targetId;
        this.route = route;
        this.title = title;
        this.read = read;
        this.occurredAt = occurredAt;
        this.createdAt = createdAt;
    }

    public static NotificationJpaEntity fromDomain(Notification notification) {
        Objects.requireNonNull(notification, "notification must not be null");
        return new NotificationJpaEntity(
                notification.notificationId(),
                notification.companyId(),
                notification.targetType(),
                notification.targetId(),
                notification.route(),
                notification.title(),
                notification.read(),
                notification.occurredAt(),
                notification.createdAt()
        );
    }

    public Notification toDomain() {
        return new Notification(
                notificationId, companyId, targetType, targetId, route, title, read, occurredAt, createdAt
        );
    }

    public void applyState(Notification notification) {
        Objects.requireNonNull(notification, "notification must not be null");
        if (!notificationId.equals(notification.notificationId())
                || !companyId.equals(notification.companyId())) {
            throw new IllegalArgumentException("immutable notification fields must not change");
        }
        this.read = notification.read();
    }
}
