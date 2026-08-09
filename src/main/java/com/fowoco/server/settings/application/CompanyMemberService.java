package com.fowoco.server.settings.application;

import com.fowoco.server.auth.application.ActorAuthorizer;
import com.fowoco.server.auth.application.ActorContext;
import com.fowoco.server.auth.application.CompanyMemberAccount;
import com.fowoco.server.auth.application.port.CompanyMemberDirectory;
import com.fowoco.server.auth.domain.UserRole;
import com.fowoco.server.common.error.ApiException;
import com.fowoco.server.common.error.ErrorCode;
import com.fowoco.server.common.security.TenantDatabaseContext;
import com.fowoco.server.settings.application.port.CompanySettingsRepository;
import com.fowoco.server.settings.domain.ApprovalPolicy;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompanyMemberService {

    private static final Set<UserRole> DETAILED_VIEW_ROLES = Set.of(UserRole.ADMIN, UserRole.HR);

    private final ActorAuthorizer actorAuthorizer;
    private final TenantDatabaseContext tenantDatabaseContext;
    private final CompanyMemberDirectory companyMemberDirectory;
    private final CompanySettingsRepository companySettingsRepository;

    public CompanyMemberService(
            ActorAuthorizer actorAuthorizer,
            TenantDatabaseContext tenantDatabaseContext,
            CompanyMemberDirectory companyMemberDirectory,
            CompanySettingsRepository companySettingsRepository
    ) {
        this.actorAuthorizer = actorAuthorizer;
        this.tenantDatabaseContext = tenantDatabaseContext;
        this.companyMemberDirectory = companyMemberDirectory;
        this.companySettingsRepository = companySettingsRepository;
    }

    @Transactional(readOnly = true)
    public List<CompanyMemberView> findAll(
            ActorContext actor,
            UserRole role,
            Boolean approvalCapable,
            boolean activeOnly
    ) {
        tenantDatabaseContext.setCompanyIdForCurrentTransaction(actor.companyId());
        actorAuthorizer.requireAnyRole(actor, UserRole.ADMIN, UserRole.HR, UserRole.VIEWER);
        boolean detailed = actor.hasAnyRole(DETAILED_VIEW_ROLES);
        if (!detailed && (role != null || approvalCapable != null || !activeOnly)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }

        List<CompanyMemberAccount> accounts = companyMemberDirectory.findByCompanyId(
                actor.companyId(),
                role,
                activeOnly
        );
        if (!detailed) {
            return accounts.stream()
                    .map(account -> (CompanyMemberView) new MinimalCompanyMemberView(
                            account.userId(),
                            account.displayName()
                    ))
                    .toList();
        }

        ApprovalPolicy approvalPolicy = companySettingsRepository.findByCompanyId(actor.companyId())
                .orElseThrow(() -> new IllegalStateException(
                        "Persisted company settings are missing for company " + actor.companyId()
                ))
                .approvalPolicy();
        return accounts.stream()
                .map(account -> detailedView(account, approvalPolicy))
                .filter(member -> approvalCapable == null
                        || member.approvalPermission() == approvalCapable)
                .map(member -> (CompanyMemberView) member)
                .toList();
    }

    private DetailedCompanyMemberView detailedView(
            CompanyMemberAccount account,
            ApprovalPolicy approvalPolicy
    ) {
        boolean active = account.active();
        return new DetailedCompanyMemberView(
                account.userId(),
                account.displayName(),
                List.of(account.role()),
                active,
                active && approvalPolicy.permits(account.role())
        );
    }
}
