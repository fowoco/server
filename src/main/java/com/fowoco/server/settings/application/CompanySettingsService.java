package com.fowoco.server.settings.application;

import com.fowoco.server.auth.application.ActorAuthorizer;
import com.fowoco.server.auth.application.ActorContext;
import com.fowoco.server.auth.domain.UserRole;
import com.fowoco.server.common.security.TenantDatabaseContext;
import com.fowoco.server.settings.application.port.CompanySettingsRepository;
import com.fowoco.server.settings.domain.CompanySettings;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompanySettingsService {

    private final ActorAuthorizer actorAuthorizer;
    private final TenantDatabaseContext tenantDatabaseContext;
    private final CompanySettingsRepository companySettingsRepository;

    public CompanySettingsService(
            ActorAuthorizer actorAuthorizer,
            TenantDatabaseContext tenantDatabaseContext,
            CompanySettingsRepository companySettingsRepository
    ) {
        this.actorAuthorizer = actorAuthorizer;
        this.tenantDatabaseContext = tenantDatabaseContext;
        this.companySettingsRepository = companySettingsRepository;
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
}
