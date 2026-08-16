package com.fowoco.server.notification.infrastructure.persistence;

import com.fowoco.server.notification.application.port.NotificationPreferenceRepository;
import com.fowoco.server.notification.domain.NotificationPreferenceKey;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcNotificationPreferenceRepository implements NotificationPreferenceRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcNotificationPreferenceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Map<NotificationPreferenceKey, Boolean> findOverrides(UUID userId, UUID companyId) {
        Map<NotificationPreferenceKey, Boolean> overrides = new EnumMap<>(NotificationPreferenceKey.class);
        jdbcTemplate.query(
                """
                SELECT pref_key, enabled
                FROM notification_preference
                WHERE user_id = ? AND company_id = ?
                """,
                resultSet -> {
                    NotificationPreferenceKey key =
                            NotificationPreferenceKey.fromKey(resultSet.getString("pref_key"));
                    if (key != null) {
                        overrides.put(key, resultSet.getBoolean("enabled"));
                    }
                },
                userId,
                companyId
        );
        return overrides;
    }

    @Override
    public void upsert(
            UUID preferenceId,
            UUID userId,
            UUID companyId,
            NotificationPreferenceKey key,
            boolean enabled,
            Instant updatedAt
    ) {
        int updated = jdbcTemplate.update(
                """
                UPDATE notification_preference
                SET enabled = ?, updated_at = ?
                WHERE user_id = ? AND company_id = ? AND pref_key = ?
                """,
                enabled,
                Timestamp.from(updatedAt),
                userId,
                companyId,
                key.key()
        );
        if (updated == 0) {
            jdbcTemplate.update(
                    """
                    INSERT INTO notification_preference
                        (notification_preference_id, user_id, company_id, pref_key, enabled, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    preferenceId,
                    userId,
                    companyId,
                    key.key(),
                    enabled,
                    Timestamp.from(updatedAt)
            );
        }
    }
}
