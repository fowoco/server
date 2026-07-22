package com.fowoco.server.auth.infrastructure.seed;

import com.fowoco.server.auth.application.port.UserAccountRepository;
import com.fowoco.server.auth.domain.UserAccount;
import com.fowoco.server.auth.domain.UserRole;
import com.fowoco.server.company.application.port.CompanyRepository;
import com.fowoco.server.company.domain.Company;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

class DemoAuthSeedRunner implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(DemoAuthSeedRunner.class);
    private static final int MINIMUM_DEMO_PASSWORD_LENGTH = 12;

    private final DemoAuthSeedProperties properties;
    private final CompanyRepository companyRepository;
    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    DemoAuthSeedRunner(
            DemoAuthSeedProperties properties,
            CompanyRepository companyRepository,
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder,
            Clock clock
    ) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.companyRepository = Objects.requireNonNull(
                companyRepository,
                "companyRepository must not be null"
        );
        this.userAccountRepository = Objects.requireNonNull(
                userAccountRepository,
                "userAccountRepository must not be null"
        );
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        validateConfiguration();
        String normalizedEmail = UserAccount.normalizeEmail(properties.adminEmail());
        Optional<UserAccount> existingUser =
                userAccountRepository.findByNormalizedEmail(normalizedEmail);
        if (existingUser.isPresent()) {
            verifyExistingUser(existingUser.orElseThrow());
            LOGGER.info(
                    "demo_auth_seed already_exists company_id={} user_id={}",
                    properties.companyId(),
                    properties.adminUserId()
            );
            return;
        }

        Instant now = clock.instant();
        Optional<Company> existingCompany = companyRepository.findById(properties.companyId());
        if (existingCompany.isEmpty()) {
            companyRepository.insert(Company.create(
                    properties.companyId(),
                    properties.companyName(),
                    now
            ));
        } else if (!existingCompany.orElseThrow().isActive()) {
            throw new IllegalStateException("the configured demo company is not active");
        }

        UserAccount demoAdmin = UserAccount.create(
                properties.adminUserId(),
                properties.companyId(),
                properties.adminEmail(),
                passwordEncoder.encode(properties.adminPassword()),
                UserRole.ADMIN,
                now
        );
        userAccountRepository.insert(demoAdmin);
        LOGGER.info(
                "demo_auth_seed created company_id={} user_id={}",
                properties.companyId(),
                properties.adminUserId()
        );
    }

    private void validateConfiguration() {
        Objects.requireNonNull(properties.companyId(), "demo seed companyId must not be null");
        Objects.requireNonNull(properties.adminUserId(), "demo seed adminUserId must not be null");
        requireText(properties.companyName(), "demo seed companyName");
        requireText(properties.adminEmail(), "demo seed adminEmail");
        String password = requireText(properties.adminPassword(), "DEMO_SEED_ADMIN_PASSWORD");
        if (password.length() < MINIMUM_DEMO_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                    "DEMO_SEED_ADMIN_PASSWORD must contain at least "
                            + MINIMUM_DEMO_PASSWORD_LENGTH
                            + " characters"
            );
        }
    }

    private void verifyExistingUser(UserAccount userAccount) {
        if (!properties.adminUserId().equals(userAccount.userId())
                || !properties.companyId().equals(userAccount.companyId())
                || userAccount.role() != UserRole.ADMIN
                || !userAccount.canLogin()) {
            throw new IllegalStateException(
                    "the configured demo email already belongs to a different or inactive account"
            );
        }
    }

    private String requireText(String value, String propertyName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(propertyName + " must be configured when demo seed is enabled");
        }
        return value.strip();
    }
}
