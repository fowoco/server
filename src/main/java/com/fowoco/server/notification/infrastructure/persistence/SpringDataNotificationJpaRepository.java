package com.fowoco.server.notification.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataNotificationJpaRepository extends JpaRepository<NotificationJpaEntity, UUID> {

    @Query("""
            SELECT n
              FROM NotificationJpaEntity n
             WHERE n.notificationId = :notificationId
               AND n.companyId = :companyId
            """)
    java.util.Optional<NotificationJpaEntity> findByIdAndCompanyId(
            @Param("notificationId") UUID notificationId,
            @Param("companyId") UUID companyId
    );

    @Query("""
            SELECT n
              FROM NotificationJpaEntity n
             WHERE n.companyId = :companyId
               AND (:unreadOnly = false OR n.read = false)
               AND (:cursor IS NULL OR n.occurredAt < :cursor)
             ORDER BY n.occurredAt DESC
            """)
    java.util.List<NotificationJpaEntity> findPage(
            @Param("companyId") UUID companyId,
            @Param("unreadOnly") boolean unreadOnly,
            @Param("cursor") Instant cursor,
            Pageable pageable
    );

    long countByCompanyIdAndReadFalse(UUID companyId);
}
