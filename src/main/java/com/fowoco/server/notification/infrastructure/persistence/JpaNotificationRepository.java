package com.fowoco.server.notification.infrastructure.persistence;

import com.fowoco.server.notification.application.port.NotificationRepository;
import com.fowoco.server.notification.domain.Notification;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
public class JpaNotificationRepository implements NotificationRepository {

    private final SpringDataNotificationJpaRepository repository;

    public JpaNotificationRepository(SpringDataNotificationJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void insert(Notification notification) {
        repository.save(NotificationJpaEntity.fromDomain(notification));
    }

    @Override
    public Notification update(Notification notification) {
        NotificationJpaEntity entity = repository
                .findByIdAndCompanyId(notification.notificationId(), notification.companyId())
                .orElseThrow(() -> new IllegalStateException("notification not found for update"));
        entity.applyState(notification);
        return repository.saveAndFlush(entity).toDomain();
    }

    @Override
    public Optional<Notification> findByIdAndCompanyId(UUID notificationId, UUID companyId) {
        return repository.findByIdAndCompanyId(notificationId, companyId).map(NotificationJpaEntity::toDomain);
    }

    @Override
    public List<Notification> findPage(UUID companyId, UUID userId, boolean unreadOnly, Instant cursor, int size) {
        return repository.findPage(companyId, userId, unreadOnly, cursor, PageRequest.of(0, size)).stream()
                .map(NotificationJpaEntity::toDomain)
                .toList();
    }

    @Override
    public long countUnread(UUID companyId, UUID userId) {
        return repository.countByCompanyIdAndUserIdAndReadFalse(companyId, userId);
    }
}
