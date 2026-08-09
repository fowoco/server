package com.fowoco.server.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationTest {

    private static final UUID COMPANY_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID TARGET_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-08-09T00:00:00Z");

    @Test
    void buildsTaskRoute() {
        Notification notification = Notification.create(
                UUID.randomUUID(), COMPANY_ID, USER_ID,
                NotificationTargetType.TASK, TARGET_ID, "제목", NOW, NOW
        );

        assertThat(notification.route()).isEqualTo("/tasks/" + TARGET_ID);
    }

    @Test
    void buildsWorkerRoute() {
        Notification notification = Notification.create(
                UUID.randomUUID(), COMPANY_ID, USER_ID,
                NotificationTargetType.WORKER, TARGET_ID, "제목", NOW, NOW
        );

        assertThat(notification.route()).isEqualTo("/workers/" + TARGET_ID + "/detail");
    }

    @Test
    void buildsDocumentRoute() {
        Notification notification = Notification.create(
                UUID.randomUUID(), COMPANY_ID, USER_ID,
                NotificationTargetType.DOCUMENT, TARGET_ID, "제목", NOW, NOW
        );

        assertThat(notification.route()).isEqualTo("/documents/" + TARGET_ID);
    }

    @Test
    void routeAlwaysMatchesTargetId() {
        Notification notification = Notification.create(
                UUID.randomUUID(), COMPANY_ID, USER_ID,
                NotificationTargetType.TASK, TARGET_ID, "제목", NOW, NOW
        );

        assertThat(notification.route()).contains(notification.targetId().toString());
    }

    @Test
    void markAsReadIsIdempotent() {
        Notification notification = Notification.create(
                UUID.randomUUID(), COMPANY_ID, USER_ID,
                NotificationTargetType.TASK, TARGET_ID, "제목", NOW, NOW
        );

        Notification firstRead = notification.markAsRead();
        Notification secondRead = firstRead.markAsRead();

        assertThat(firstRead.read()).isTrue();
        assertThat(secondRead).isSameAs(firstRead);
    }
}
