package com.fowoco.server.auth.application;

import com.fowoco.server.auth.application.error.AuthErrorCode;
import com.fowoco.server.auth.application.port.UserAccountRepository;
import com.fowoco.server.auth.domain.UserAccount;
import com.fowoco.server.auth.infrastructure.security.LoginProtectionProperties;
import com.fowoco.server.common.error.ApiException;
import com.fowoco.server.common.security.TenantDatabaseContext;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginProtectionTransaction {

    private final UserAccountRepository userAccountRepository;
    private final TenantDatabaseContext tenantDatabaseContext;
    private final LoginProtectionProperties properties;
    private final Clock clock;

    public LoginProtectionTransaction(
            UserAccountRepository userAccountRepository,
            TenantDatabaseContext tenantDatabaseContext,
            LoginProtectionProperties properties,
            Clock clock
    ) {
        this.userAccountRepository = userAccountRepository;
        this.tenantDatabaseContext = tenantDatabaseContext;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public void recordFailure(UUID userId, UUID companyId) {
        UserAccount account = lockedAccount(userId, companyId);
        UserAccount updated = account.recordFailedLogin(
                properties.maxFailedAttempts(),
                properties.lockDuration(),
                clock.instant()
        );
        userAccountRepository.update(updated);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public void verifyAndClear(UUID userId, UUID companyId) {
        UserAccount account = lockedAccount(userId, companyId);
        Instant now = clock.instant();
        if (account.isTemporarilyLocked(now)) {
            throw new ApiException(AuthErrorCode.ACCOUNT_TEMPORARILY_LOCKED);
        }
        if (account.isPasswordExpired(properties.passwordMaxAge(), now)) {
            throw new ApiException(AuthErrorCode.PASSWORD_EXPIRED);
        }
        if (account.hasLoginFailures()) {
            userAccountRepository.update(account.clearLoginFailures(now));
        }
    }

    private UserAccount lockedAccount(UUID userId, UUID companyId) {
        tenantDatabaseContext.setCompanyIdForCurrentTransaction(companyId);
        return userAccountRepository.findByUserIdAndCompanyIdWithLock(userId, companyId)
                .orElseThrow(() -> new IllegalStateException("user account was not found"));
    }
}
