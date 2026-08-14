package com.fowoco.server.notification.application.port;

import com.fowoco.server.notification.domain.NotificationPreferenceKey;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public interface NotificationPreferenceRepository {

    /** Only rows the user has explicitly overridden from the default. */
    Map<NotificationPreferenceKey, Boolean> findOverrides(UUID userId, UUID companyId);

    void upsert(
            UUID preferenceId,
            UUID userId,
            UUID companyId,
            NotificationPreferenceKey key,
            boolean enabled,
            Instant updatedAt
    );
}
