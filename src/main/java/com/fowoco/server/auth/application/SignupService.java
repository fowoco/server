package com.fowoco.server.auth.application;

import com.fowoco.server.auth.application.error.AuthErrorCode;
import com.fowoco.server.auth.application.port.AuthAuditPort;
import com.fowoco.server.auth.application.port.AuthTenantBootstrap;
import com.fowoco.server.auth.application.port.PasswordHasher;
import com.fowoco.server.auth.application.port.UserAccountRepository;
import com.fowoco.server.auth.domain.UserAccount;
import com.fowoco.server.auth.domain.UserRole;
import com.fowoco.server.common.error.ApiException;
import com.fowoco.server.common.id.UuidGenerator;
import com.fowoco.server.common.security.TenantDatabaseContext;
import com.fowoco.server.company.application.port.CompanyRepository;
import com.fowoco.server.company.domain.Company;
import java.time.Clock;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SignupService {

    private final CompanyRepository companyRepository;
    private final UserAccountRepository userAccountRepository;
    private final AuthTenantBootstrap authTenantBootstrap;
    private final TenantDatabaseContext tenantDatabaseContext;
    private final PasswordHasher passwordHasher;
    private final AuthAuditPort authAuditPort;
    private final UuidGenerator uuidGenerator;
    private final Clock clock;

    public SignupService(
            CompanyRepository companyRepository,
            UserAccountRepository userAccountRepository,
            AuthTenantBootstrap authTenantBootstrap,
            TenantDatabaseContext tenantDatabaseContext,
            PasswordHasher passwordHasher,
            AuthAuditPort authAuditPort,
            UuidGenerator uuidGenerator,
            Clock clock
    ) {
        this.companyRepository = companyRepository;
        this.userAccountRepository = userAccountRepository;
        this.authTenantBootstrap = authTenantBootstrap;
        this.tenantDatabaseContext = tenantDatabaseContext;
        this.passwordHasher = passwordHasher;
        this.authAuditPort = authAuditPort;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional
    public SignupResult signup(SignupCommand command) {
        String normalizedEmail = UserAccount.normalizeEmail(command.email());
        if (authTenantBootstrap.findCompanyIdByNormalizedEmail(normalizedEmail).isPresent()) {
            throw duplicateEmail();
        }

        Instant now = clock.instant();
        Company company = Company.create(
                uuidGenerator.generate(),
                command.companyName(),
                now
        );
        tenantDatabaseContext.setCompanyIdForCurrentTransaction(company.companyId());
        UserAccount initialAdmin = UserAccount.create(
                uuidGenerator.generate(),
                company.companyId(),
                command.displayName(),
                command.email(),
                passwordHasher.hash(command.password()),
                UserRole.ADMIN,
                now
        );

        companyRepository.insert(company);
        try {
            userAccountRepository.insert(initialAdmin);
        } catch (DataIntegrityViolationException exception) {
            throw duplicateEmail();
        }

        authAuditPort.record(AuthAuditEvent.account(
                AuthAuditEvent.Action.SIGNUP_SUCCEEDED,
                initialAdmin.userId(),
                company.companyId(),
                now
        ));
        return new SignupResult(
                initialAdmin.userId(),
                company.companyId(),
                company.name(),
                initialAdmin.displayName(),
                initialAdmin.email(),
                initialAdmin.role(),
                now
        );
    }

    private ApiException duplicateEmail() {
        authAuditPort.record(AuthAuditEvent.anonymous(
                AuthAuditEvent.Action.SIGNUP_REJECTED,
                clock.instant()
        ));
        return new ApiException(AuthErrorCode.EMAIL_ALREADY_REGISTERED);
    }
}
