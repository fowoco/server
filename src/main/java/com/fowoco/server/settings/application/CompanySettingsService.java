package com.fowoco.server.settings.application;

import com.fowoco.server.audit.application.port.AuditEventRepository;
import com.fowoco.server.audit.domain.ActorType;
import com.fowoco.server.audit.domain.AuditAction;
import com.fowoco.server.audit.domain.AuditEvent;
import com.fowoco.server.audit.domain.AuditTargetType;
import com.fowoco.server.auth.application.ActorAuthorizer;
import com.fowoco.server.auth.application.ActorContext;
import com.fowoco.server.auth.domain.UserRole;
import com.fowoco.server.common.error.ApiException;
import com.fowoco.server.common.error.ErrorCode;
import com.fowoco.server.common.id.UuidGenerator;
import com.fowoco.server.common.security.TenantDatabaseContext;
import com.fowoco.server.common.web.RequestMetadata;
import com.fowoco.server.settings.application.port.CompanySettingsRepository;
import com.fowoco.server.settings.domain.CompanySettings;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompanySettingsService {

    private static final String AUDIT_EVENT_VERSION = "1";

    private final ActorAuthorizer actorAuthorizer;
    private final TenantDatabaseContext tenantDatabaseContext;
    private final CompanySettingsRepository companySettingsRepository;
    private final AuditEventRepository auditEventRepository;
    private final UuidGenerator uuidGenerator;
    private final Clock clock;

    public CompanySettingsService(
            ActorAuthorizer actorAuthorizer,
            TenantDatabaseContext tenantDatabaseContext,
            CompanySettingsRepository companySettingsRepository,
            AuditEventRepository auditEventRepository,
            UuidGenerator uuidGenerator,
            Clock clock
    ) {
        this.actorAuthorizer = actorAuthorizer;
        this.tenantDatabaseContext = tenantDatabaseContext;
        this.companySettingsRepository = companySettingsRepository;
        this.auditEventRepository = auditEventRepository;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CompanySettings get(ActorContext actor) {
        tenantDatabaseContext.setCompanyIdForCurrentTransaction(actor.companyId());
        actorAuthorizer.requireAnyRole(actor, UserRole.ADMIN, UserRole.HR, UserRole.VIEWER);
        return companySettingsRepository.findByCompanyId(actor.companyId())
                .orElseThrow(() -> new IllegalStateException(
                        "Persisted company settings are missing for company " + actor.companyId()
                ));
    }

    @Transactional
    public CompanySettings update(
            ActorContext actor,
            UpdateCompanySettingsCommand command,
            RequestMetadata metadata
    ) {
        tenantDatabaseContext.setCompanyIdForCurrentTransaction(actor.companyId());
        actorAuthorizer.requireAnyRole(actor, UserRole.ADMIN);
        CompanySettings current = companySettingsRepository.findByCompanyId(actor.companyId())
                .orElseThrow(() -> new IllegalStateException(
                        "Persisted company settings are missing for company " + actor.companyId()
                ));
        if (current.version() != command.expectedVersion()) {
            throw new ApiException(ErrorCode.CONCURRENT_MODIFICATION);
        }

        Instant now = Instant.now(clock);
        CompanySettings requested = current.update(
                command.approvalPolicy().orElse(current.approvalPolicy()),
                command.linkExpiryHours().orElse(current.linkExpiryHours()),
                command.evidenceRules().orElse(current.evidenceRules()),
                command.fileRetentionDays().orElse(current.fileRetentionDays()),
                command.aiLogRetentionDays().orElse(current.aiLogRetentionDays()),
                command.auditVisibility().orElse(current.auditVisibility()),
                now
        );
        if (samePolicies(current, requested)) {
            return current;
        }

        CompanySettings saved = companySettingsRepository.update(requested);
        CompanySettingsAuditSummary.changedFields(current, saved).forEach(summary ->
                auditEventRepository.append(new AuditEvent(
                        uuidGenerator.generate(),
                        actor.companyId(),
                        ActorType.HR_USER,
                        actor.actorId(),
                        UserRole.ADMIN,
                        AuditAction.SETTINGS_UPDATED,
                        AuditTargetType.COMPANY_SETTINGS,
                        actor.companyId(),
                        metadata.requestId(),
                        metadata.traceId(),
                        AUDIT_EVENT_VERSION,
                        summary,
                        now
                ))
        );
        return saved;
    }

    private boolean samePolicies(CompanySettings left, CompanySettings right) {
        return left.approvalPolicy() == right.approvalPolicy()
                && left.linkExpiryHours() == right.linkExpiryHours()
                && left.evidenceRules().equals(right.evidenceRules())
                && left.fileRetentionDays() == right.fileRetentionDays()
                && left.aiLogRetentionDays() == right.aiLogRetentionDays()
                && left.auditVisibility() == right.auditVisibility();
    }
}
