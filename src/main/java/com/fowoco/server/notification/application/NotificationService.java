package com.fowoco.server.notification.application;

import com.fowoco.server.auth.application.ActorContext;
import com.fowoco.server.common.error.ApiException;
import com.fowoco.server.common.security.TenantDatabaseContext;
import com.fowoco.server.notification.application.error.NotificationErrorCode;
import com.fowoco.server.notification.application.port.NotificationRepository;
import com.fowoco.server.notification.domain.Notification;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final NotificationRepository notificationRepository;
    private final TenantDatabaseContext tenantDatabaseContext;

    public NotificationService(
            NotificationRepository notificationRepository,
            TenantDatabaseContext tenantDatabaseContext
    ) {
        this.notificationRepository = notificationRepository;
        this.tenantDatabaseContext = tenantDatabaseContext;
    }

    @Transactional(readOnly = true)
    public NotificationPageResult findPage(ActorContext actor, Boolean unreadOnly, Instant cursor, Integer size) {
        tenantDatabaseContext.setCompanyIdForCurrentTransaction(actor.companyId());
        UUID companyId = actor.companyId();
        int effectiveSize = normalizeSize(size);

        List<Notification> items = notificationRepository.findPage(
                companyId, unreadOnly != null && unreadOnly, cursor, effectiveSize
        );
        long unreadCount = notificationRepository.countUnread(companyId);
        String nextCursor = items.size() == effectiveSize && !items.isEmpty()
                ? items.get(items.size() - 1).occurredAt().toString()
                : null;

        return new NotificationPageResult(items, unreadCount, nextCursor);
    }

    @Transactional
    public void markAsRead(UUID notificationId, ActorContext actor) {
        tenantDatabaseContext.setCompanyIdForCurrentTransaction(actor.companyId());
        Notification notification = notificationRepository
                .findByIdAndCompanyId(notificationId, actor.companyId())
                .orElseThrow(() -> new ApiException(NotificationErrorCode.NOTIFICATION_NOT_FOUND));
        Notification updated = notification.markAsRead();
        if (updated != notification) {
            notificationRepository.update(updated);
        }
    }

    private int normalizeSize(Integer size) {
        if (size == null) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }
}
