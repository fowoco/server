package com.fowoco.server.notification.application;

import com.fowoco.server.auth.application.ActorContext;
import com.fowoco.server.common.error.ApiException;
import com.fowoco.server.common.id.UuidGenerator;
import com.fowoco.server.common.security.TenantDatabaseContext;
import com.fowoco.server.notification.application.error.NotificationErrorCode;
import com.fowoco.server.notification.application.port.NotificationPreferenceRepository;
import com.fowoco.server.notification.domain.NotificationPreferenceKey;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final TenantDatabaseContext tenantDatabaseContext;
    private final UuidGenerator uuidGenerator;
    private final Clock clock;

    public NotificationPreferenceService(
            NotificationPreferenceRepository notificationPreferenceRepository,
            TenantDatabaseContext tenantDatabaseContext,
            UuidGenerator uuidGenerator,
            Clock clock
    ) {
        this.notificationPreferenceRepository = notificationPreferenceRepository;
        this.tenantDatabaseContext = tenantDatabaseContext;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    public record PreferenceState(NotificationPreferenceKey key, boolean enabled) {
    }

    @Transactional(readOnly = true)
    public List<PreferenceState> list(ActorContext actor) {
        tenantDatabaseContext.setCompanyIdForCurrentTransaction(actor.companyId());
        Map<NotificationPreferenceKey, Boolean> overrides =
                notificationPreferenceRepository.findOverrides(actor.actorId(), actor.companyId());
        return NotificationPreferenceKey.defaults().entrySet().stream()
                .map(entry -> new PreferenceState(
                        entry.getKey(),
                        overrides.getOrDefault(entry.getKey(), entry.getValue())
                ))
                .toList();
    }

    @Transactional
    public List<PreferenceState> update(ActorContext actor, String rawKey, boolean enabled) {
        tenantDatabaseContext.setCompanyIdForCurrentTransaction(actor.companyId());
        NotificationPreferenceKey key = NotificationPreferenceKey.fromKey(rawKey);
        if (key == null) {
            throw new ApiException(NotificationErrorCode.NOTIFICATION_PREFERENCE_NOT_FOUND);
        }
        if (key.required() && !enabled) {
            throw new ApiException(NotificationErrorCode.NOTIFICATION_PREFERENCE_REQUIRED);
        }
        notificationPreferenceRepository.upsert(
                uuidGenerator.generate(),
                actor.actorId(),
                actor.companyId(),
                key,
                enabled,
                clock.instant()
        );
        return list(actor);
    }
}
