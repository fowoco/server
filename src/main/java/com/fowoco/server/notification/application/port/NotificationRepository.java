package com.fowoco.server.notification.application.port;

import com.fowoco.server.notification.domain.Notification;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository {

    void insert(Notification notification);

    Notification update(Notification notification);

    Optional<Notification> findByIdAndCompanyId(UUID notificationId, UUID companyId);

    List<Notification> findPage(UUID companyId, boolean unreadOnly, Instant cursor, int size);

    long countUnread(UUID companyId);
}
