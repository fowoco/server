package com.fowoco.server.auth.infrastructure.seed;

import com.fowoco.server.auth.application.port.UserAccountRepository;
import com.fowoco.server.auth.domain.UserAccount;
import com.fowoco.server.auth.domain.UserRole;
import com.fowoco.server.company.application.port.CompanyRepository;
import com.fowoco.server.company.application.port.CompanySettingsProvisioner;
import com.fowoco.server.company.domain.Company;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@Order(0)
class DemoAuthSeedRunner implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(DemoAuthSeedRunner.class);
    private static final int MINIMUM_DEMO_PASSWORD_LENGTH = 12;
    private static final List<DemoUser> DEMO_USERS = List.of(
            demoUser("90000000-0000-0000-0000-000000000003", "데모 운영관리자", "demo.ops@example.com", UserRole.ADMIN),
            demoUser("90000000-0000-0000-0000-000000000004", "김하늘", "demo.hr01@example.com", UserRole.HR),
            demoUser("90000000-0000-0000-0000-000000000005", "이도윤", "demo.hr02@example.com", UserRole.HR),
            demoUser("90000000-0000-0000-0000-000000000006", "박서연", "demo.hr03@example.com", UserRole.HR),
            demoUser("90000000-0000-0000-0000-000000000007", "최민준", "demo.hr04@example.com", UserRole.HR),
            demoUser("90000000-0000-0000-0000-000000000008", "정수빈", "demo.hr05@example.com", UserRole.HR),
            demoUser("90000000-0000-0000-0000-000000000009", "강지훈", "demo.hr06@example.com", UserRole.HR),
            demoUser("90000000-0000-0000-0000-000000000010", "윤가은", "demo.hr07@example.com", UserRole.HR),
            demoUser("90000000-0000-0000-0000-000000000011", "조현우", "demo.hr08@example.com", UserRole.HR),
            demoUser("90000000-0000-0000-0000-000000000012", "임채원", "demo.hr09@example.com", UserRole.HR),
            demoUser("90000000-0000-0000-0000-000000000013", "한준서", "demo.hr10@example.com", UserRole.HR),
            demoUser("90000000-0000-0000-0000-000000000014", "오유진", "demo.hr11@example.com", UserRole.HR),
            demoUser("90000000-0000-0000-0000-000000000015", "서지민", "demo.hr12@example.com", UserRole.HR),
            demoUser("90000000-0000-0000-0000-000000000016", "데모 조회자 01", "demo.viewer01@example.com", UserRole.VIEWER),
            demoUser("90000000-0000-0000-0000-000000000017", "데모 조회자 02", "demo.viewer02@example.com", UserRole.VIEWER),
            demoUser("90000000-0000-0000-0000-000000000018", "데모 조회자 03", "demo.viewer03@example.com", UserRole.VIEWER),
            demoUser("90000000-0000-0000-0000-000000000019", "데모 조회자 04", "demo.viewer04@example.com", UserRole.VIEWER),
            demoUser("90000000-0000-0000-0000-000000000020", "데모 조회자 05", "demo.viewer05@example.com", UserRole.VIEWER),
            demoUser("90000000-0000-0000-0000-000000000021", "데모 조회자 06", "demo.viewer06@example.com", UserRole.VIEWER)
    );
    private static final List<DemoUser> TEST_USERS = List.of(
            demoUser("91000000-0000-0000-0000-000000000002", "테스트 관리자", "test.admin@example.com", UserRole.ADMIN),
            demoUser("91000000-0000-0000-0000-000000000003", "테스트 HR", "test.hr@example.com", UserRole.HR),
            demoUser("91000000-0000-0000-0000-000000000004", "테스트 조회자", "test.viewer@example.com", UserRole.VIEWER)
    );

    private final DemoAuthSeedProperties properties;
    private final CompanyRepository companyRepository;
    private final CompanySettingsProvisioner companySettingsProvisioner;
    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    DemoAuthSeedRunner(
            DemoAuthSeedProperties properties,
            CompanyRepository companyRepository,
            CompanySettingsProvisioner companySettingsProvisioner,
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder,
            Clock clock
    ) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.companyRepository = Objects.requireNonNull(
                companyRepository,
                "companyRepository must not be null"
        );
        this.companySettingsProvisioner = Objects.requireNonNull(
                companySettingsProvisioner,
                "companySettingsProvisioner must not be null"
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
        Instant now = clock.instant();
        ensureCompany(properties.companyId(), properties.companyName(), now);
        ensureCompany(properties.testCompanyId(), properties.testCompanyName(), now);

        seedUser(properties.companyId(), new DemoUser(
                properties.adminUserId(),
                properties.adminDisplayName(),
                properties.adminEmail(),
                UserRole.ADMIN
        ), now);
        DEMO_USERS.forEach(user -> seedUser(properties.companyId(), user, now));
        TEST_USERS.forEach(user -> seedUser(properties.testCompanyId(), user, now));
        LOGGER.info(
                "demo_auth_seed ready company_count={} user_count={}",
                2,
                DEMO_USERS.size() + TEST_USERS.size() + 1
        );
    }

    private void validateConfiguration() {
        Objects.requireNonNull(properties.companyId(), "demo seed companyId must not be null");
        Objects.requireNonNull(properties.testCompanyId(), "demo seed testCompanyId must not be null");
        Objects.requireNonNull(properties.adminUserId(), "demo seed adminUserId must not be null");
        requireText(properties.companyName(), "demo seed companyName");
        requireText(properties.testCompanyName(), "demo seed testCompanyName");
        requireText(properties.adminDisplayName(), "demo seed adminDisplayName");
        requireText(properties.adminEmail(), "demo seed adminEmail");
        String password = requireText(properties.adminPassword(), "DEMO_SEED_ADMIN_PASSWORD");
        if (password.length() < MINIMUM_DEMO_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                    "DEMO_SEED_ADMIN_PASSWORD must contain at least "
                            + MINIMUM_DEMO_PASSWORD_LENGTH
                            + " characters"
            );
        }
        if (properties.companyId().equals(properties.testCompanyId())) {
            throw new IllegalStateException("demo seed company ids must be different");
        }
        if (reservedUsers().stream().anyMatch(user -> user.userId().equals(properties.adminUserId()))) {
            throw new IllegalStateException("demo seed adminUserId conflicts with a reserved demo user id");
        }
        String normalizedAdminEmail = UserAccount.normalizeEmail(properties.adminEmail());
        if (reservedUsers().stream().anyMatch(user ->
                UserAccount.normalizeEmail(user.email()).equals(normalizedAdminEmail))) {
            throw new IllegalStateException("demo seed adminEmail conflicts with a reserved demo user email");
        }
    }

    private void ensureCompany(UUID companyId, String companyName, Instant now) {
        Optional<Company> existingCompany = companyRepository.findById(companyId);
        if (existingCompany.isEmpty()) {
            companyRepository.insert(Company.create(
                    companyId,
                    companyName,
                    now
            ));
            companySettingsProvisioner.provisionDefaults(companyId, now);
            return;
        }
        if (!existingCompany.orElseThrow().isActive()) {
            throw new IllegalStateException("the configured demo company is not active");
        }
    }

    private void seedUser(UUID companyId, DemoUser demoUser, Instant now) {
        String normalizedEmail = UserAccount.normalizeEmail(demoUser.email());
        Optional<UserAccount> existingByEmail =
                userAccountRepository.findByNormalizedEmail(normalizedEmail);
        if (existingByEmail.isPresent()) {
            verifyExistingUser(existingByEmail.orElseThrow(), companyId, demoUser);
            return;
        }
        if (userAccountRepository
                .findByUserIdAndCompanyId(demoUser.userId(), companyId)
                .isPresent()) {
            throw new IllegalStateException("a reserved demo user id already belongs to another email");
        }
        userAccountRepository.insert(UserAccount.create(
                demoUser.userId(),
                companyId,
                demoUser.displayName(),
                null,
                demoUser.email(),
                passwordEncoder.encode(properties.adminPassword()),
                demoUser.role(),
                now
        ));
    }

    private void verifyExistingUser(UserAccount userAccount, UUID companyId, DemoUser demoUser) {
        if (!demoUser.userId().equals(userAccount.userId())
                || !companyId.equals(userAccount.companyId())
                || userAccount.role() != demoUser.role()
                || !userAccount.canLogin()) {
            throw new IllegalStateException(
                    "a configured demo email already belongs to a different or inactive account"
            );
        }
    }

    private String requireText(String value, String propertyName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(propertyName + " must be configured when demo seed is enabled");
        }
        return value.strip();
    }

    private static DemoUser demoUser(String userId, String displayName, String email, UserRole role) {
        return new DemoUser(UUID.fromString(userId), displayName, email, role);
    }

    private static List<DemoUser> reservedUsers() {
        return java.util.stream.Stream.concat(DEMO_USERS.stream(), TEST_USERS.stream()).toList();
    }

    private record DemoUser(UUID userId, String displayName, String email, UserRole role) {
    }
}
