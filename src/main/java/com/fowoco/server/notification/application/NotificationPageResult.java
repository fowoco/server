package com.fowoco.server.notification.application;

import com.fowoco.server.notification.domain.Notification;
import java.util.List;

public record NotificationPageResult(
        List<Notification> items,
        long unreadCount,
        String nextCursor
) {
}
