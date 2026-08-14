package com.fowoco.server.auth.application;

import com.fowoco.server.auth.application.error.AuthErrorCode;
import com.fowoco.server.auth.application.port.AuthAuditPort;
import com.fowoco.server.auth.application.port.AuthTenantBootstrap;
import com.fowoco.server.auth.application.port.PasswordHasher;
import com.fowoco.server.auth.application.port.UserAccountRepository;
import com.fowoco.server.auth.application.port.UserAgreementConsentRepository;
import com.fowoco.server.auth.domain.UserAccount;
import com.fowoco.server.auth.domain.UserAgreementConsent;
import com.fowoco.server.auth.domain.AgreementType;
import com.fowoco.server.auth.domain.UserRole;
import com.fowoco.server.auth.infrastructure.security.AgreementPolicyProperties;
import com.fowoco.server.audit.application.port.AuditEventRepository;
import com.fowoco.server.audit.domain.ActorType;
import com.fowoco.server.audit.domain.AuditAction;
import com.fowoco.server.audit.domain.AuditEvent;
import com.fowoco.server.audit.domain.AuditTargetType;
import com.fowoco.server.common.error.ApiException;
import com.fowoco.server.common.id.UuidGenerator;
import com.fowoco.server.common.security.TenantDatabaseContext;
import com.fowoco.server.company.application.port.CompanyRepository;
import com.fowoco.server.company.application.port.CompanySettingsProvisioner;
import com.fowoco.server.company.domain.Company;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SignupService {

    private final CompanyRepository companyRepository;
    private final CompanySettingsProvisioner companySettingsProvisioner;
    private final UserAccountRepository userAccountRepository;
    private final UserAgreementConsentRepository consentRepository;
    private final AuthTenantBootstrap authTenantBootstrap;
    private final TenantDatabaseContext tenantDatabaseContext;
    private final PasswordHasher passwordHasher;
    private final AuthAuditPort authAuditPort;
    private final AuditEventRepository auditEventRepository;
    private final AgreementPolicyProperties agreementPolicy;
    private final UuidGenerator uuidGenerator;
    private final Clock clock;

    public SignupService(
            CompanyRepository companyRepository,
            CompanySettingsProvisioner companySettingsProvisioner,
            UserAccountRepository userAccountRepository,
            UserAgreementConsentRepository consentRepository,
            AuthTenantBootstrap authTenantBootstrap,
            TenantDatabaseContext tenantDatabaseContext,
            PasswordHasher passwordHasher,
            AuthAuditPort authAuditPort,
            AuditEventRepository auditEventRepository,
            AgreementPolicyProperties agreementPolicy,
            UuidGenerator uuidGenerator,
            Clock clock
    ) {
        this.companyRepository = companyRepository;
        this.companySettingsProvisioner = companySettingsProvisioner;
        this.userAccountRepository = userAccountRepository;
        this.consentRepository = consentRepository;
        this.authTenantBootstrap = authTenantBootstrap;
        this.tenantDatabaseContext = tenantDatabaseContext;
        this.passwordHasher = passwordHasher;
        this.authAuditPort = authAuditPort;
        this.auditEventRepository = auditEventRepository;
        this.agreementPolicy = agreementPolicy;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional
    public SignupResult signup(SignupCommand command) {
        validateAgreements(command.agreements());
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
                command.phone(),
                command.email(),
                passwordHasher.hash(command.password()),
                UserRole.ADMIN,
                now
        );

        companyRepository.insert(company);
        companySettingsProvisioner.provisionDefaults(company.companyId(), now);
        try {
            userAccountRepository.insert(initialAdmin);
        } catch (DataIntegrityViolationException exception) {
            throw duplicateEmail();
        }

        consentRepository.insertAll(consents(command, initialAdmin, now));
        auditEventRepository.append(new AuditEvent(
                uuidGenerator.generate(),
                company.companyId(),
                ActorType.HR_USER,
                initialAdmin.userId(),
                initialAdmin.role(),
                AuditAction.USER_AGREEMENTS_RECORDED,
                AuditTargetType.USER_ACCOUNT,
                initialAdmin.userId(),
                command.requestId(),
                null,
                "1.0",
                "가입 시 표시된 필수·선택 약관 동의 이력을 기록함",
                now
        ));

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

    private void validateAgreements(SignupAgreements agreements) {
        boolean valid = agreements.serviceTerms().agreed()
                && agreements.privacyPolicy().agreed()
                && agreementPolicy.serviceTermsVersion().equals(agreements.serviceTerms().version())
                && agreementPolicy.privacyPolicyVersion().equals(agreements.privacyPolicy().version())
                && agreementPolicy.marketingVersion().equals(agreements.marketing().version());
        if (!valid) {
            throw new ApiException(AuthErrorCode.INVALID_AGREEMENT_CONSENT);
        }
    }

    private List<UserAgreementConsent> consents(
            SignupCommand command,
            UserAccount account,
            Instant now
    ) {
        return List.of(
                consent(account, AgreementType.SERVICE_TERMS, command.agreements().serviceTerms(), command.requestId(), now),
                consent(account, AgreementType.PRIVACY_POLICY, command.agreements().privacyPolicy(), command.requestId(), now),
                consent(account, AgreementType.MARKETING, command.agreements().marketing(), command.requestId(), now)
        );
    }

    private UserAgreementConsent consent(
            UserAccount account,
            AgreementType type,
            AgreementAcceptance acceptance,
            String requestId,
            Instant now
    ) {
        return new UserAgreementConsent(
                uuidGenerator.generate(),
                account.companyId(),
                account.userId(),
                type,
                acceptance.version(),
                acceptance.agreed(),
                requestId,
                now
        );
    }
}
