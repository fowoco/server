package com.fowoco.server.notification.infrastructure.persistence;

import java.util.UUID;
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

    long countByCompanyIdAndUserIdAndReadFalse(UUID companyId, UUID userId);
}
