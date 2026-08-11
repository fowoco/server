package com.fowoco.server.notification.infrastructure.persistence;

import com.fowoco.server.notification.application.port.NotificationRepository;
import com.fowoco.server.notification.domain.Notification;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaNotificationRepository implements NotificationRepository {

    private final SpringDataNotificationJpaRepository repository;
    private final EntityManager entityManager;

    public JpaNotificationRepository(
            SpringDataNotificationJpaRepository repository,
            EntityManager entityManager
    ) {
        this.repository = repository;
        this.entityManager = entityManager;
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
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<NotificationJpaEntity> query = builder.createQuery(NotificationJpaEntity.class);
        Root<NotificationJpaEntity> notification = query.from(NotificationJpaEntity.class);
        List<Predicate> predicates = new ArrayList<>();

        predicates.add(builder.equal(notification.get("companyId"), companyId));
        predicates.add(builder.equal(notification.get("userId"), userId));
        if (unreadOnly) {
            predicates.add(builder.isFalse(notification.get("read")));
        }
        if (cursor != null) {
            predicates.add(builder.lessThan(notification.<Instant>get("occurredAt"), cursor));
        }

        query.where(predicates.toArray(Predicate[]::new));
        query.orderBy(builder.desc(notification.get("occurredAt")));

        return entityManager.createQuery(query)
                .setMaxResults(size)
                .getResultList()
                .stream()
                .map(NotificationJpaEntity::toDomain)
                .toList();
    }

    @Override
    public long countUnread(UUID companyId, UUID userId) {
        return repository.countByCompanyIdAndUserIdAndReadFalse(companyId, userId);
    }
}
