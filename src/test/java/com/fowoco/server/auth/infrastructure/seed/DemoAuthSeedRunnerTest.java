package com.fowoco.server.auth.infrastructure.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fowoco.server.auth.application.port.UserAccountRepository;
import com.fowoco.server.auth.domain.UserAccount;
import com.fowoco.server.auth.domain.UserRole;
import com.fowoco.server.company.application.port.CompanyRepository;
import com.fowoco.server.company.application.port.CompanySettingsProvisioner;
import com.fowoco.server.company.domain.Company;
import com.fowoco.server.company.domain.CompanyStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class DemoAuthSeedRunnerTest {

    private static final UUID COMPANY_ID = UUID.fromString("90000000-0000-0000-0000-000000000001");
    private static final UUID TEST_COMPANY_ID =
            UUID.fromString("91000000-0000-0000-0000-000000000001");
    private static final UUID ADMIN_USER_ID = UUID.fromString("90000000-0000-0000-0000-000000000002");
    private static final String ADMIN_DISPLAY_NAME = "데모 관리자";
    private static final String ADMIN_EMAIL = "demo.admin@example.com";
    private static final String ADMIN_PASSWORD = "Demo-password-1!";
    private static final Instant NOW = Instant.parse("2026-07-22T00:00:00Z");

    @Test
    void createsAnIdempotentAdminSeedAndStoresOnlyThePasswordHash() throws Exception {
        InMemoryCompanyRepository companyRepository = new InMemoryCompanyRepository();
        InMemoryUserAccountRepository userAccountRepository = new InMemoryUserAccountRepository();
        RecordingCompanySettingsProvisioner settingsProvisioner =
                new RecordingCompanySettingsProvisioner();
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
        DemoAuthSeedRunner runner = runner(
                properties(ADMIN_PASSWORD),
                companyRepository,
                settingsProvisioner,
                userAccountRepository,
                passwordEncoder
        );

        runner.run(new DefaultApplicationArguments(new String[0]));
        runner.run(new DefaultApplicationArguments(new String[0]));

        assertThat(companyRepository.companies).hasSize(2);
        assertThat(settingsProvisioner.companyIds)
                .containsExactly(COMPANY_ID, TEST_COMPANY_ID);
        assertThat(companyRepository.companies.get(TEST_COMPANY_ID).name())
                .isEqualTo("FOWOCO Test Company");
        assertThat(userAccountRepository.users).hasSize(23);
        UserAccount admin = userAccountRepository.users.get(ADMIN_USER_ID);
        assertThat(admin.companyId()).isEqualTo(COMPANY_ID);
        assertThat(admin.displayName()).isEqualTo(ADMIN_DISPLAY_NAME);
        assertThat(admin.normalizedEmail()).isEqualTo(ADMIN_EMAIL);
        assertThat(admin.passwordHash()).isNotEqualTo(ADMIN_PASSWORD);
        assertThat(passwordEncoder.matches(ADMIN_PASSWORD, admin.passwordHash())).isTrue();
        assertThat(admin.role().name()).isEqualTo("ADMIN");
        assertThat(admin.canLogin()).isTrue();
        assertThat(userAccountRepository.users.values())
                .filteredOn(user -> user.role() == UserRole.ADMIN)
                .hasSize(3);
        assertThat(userAccountRepository.users.values())
                .filteredOn(user -> user.role() == UserRole.HR)
                .hasSize(13);
        assertThat(userAccountRepository.users.values())
                .filteredOn(user -> user.role() == UserRole.VIEWER)
                .hasSize(7);
        assertThat(userAccountRepository.users.values())
                .filteredOn(user -> user.companyId().equals(TEST_COMPANY_ID))
                .extracting(UserAccount::role)
                .containsExactlyInAnyOrder(UserRole.ADMIN, UserRole.HR, UserRole.VIEWER);
        assertThat(userAccountRepository.users.values())
                .allMatch(user -> passwordEncoder.matches(ADMIN_PASSWORD, user.passwordHash()));
    }

    @Test
    void refusesToStartWhenTheEnabledSeedHasNoPassword() {
        InMemoryCompanyRepository companyRepository = new InMemoryCompanyRepository();
        InMemoryUserAccountRepository userAccountRepository = new InMemoryUserAccountRepository();
        DemoAuthSeedRunner runner = runner(
                properties(""),
                companyRepository,
                userAccountRepository,
                new BCryptPasswordEncoder(4)
        );

        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DEMO_SEED_ADMIN_PASSWORD");
        assertThat(companyRepository.companies).isEmpty();
        assertThat(userAccountRepository.users).isEmpty();
    }

    @Test
    void configurationTextNeverExposesTheDemoPassword() {
        assertThat(properties(ADMIN_PASSWORD).toString())
                .contains("adminPassword=<redacted>")
                .doesNotContain(ADMIN_PASSWORD);
    }

    @Test
    void existingAdminDoesNotHideAnInactiveDemoCompany() throws Exception {
        InMemoryCompanyRepository companyRepository = new InMemoryCompanyRepository();
        InMemoryUserAccountRepository userAccountRepository = new InMemoryUserAccountRepository();
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
        DemoAuthSeedRunner runner = runner(
                properties(ADMIN_PASSWORD),
                companyRepository,
                userAccountRepository,
                passwordEncoder
        );
        runner.run(new DefaultApplicationArguments(new String[0]));
        companyRepository.companies.put(
                COMPANY_ID,
                new Company(COMPANY_ID, "FOWOCO Demo Company", CompanyStatus.SUSPENDED, NOW, NOW, 1L)
        );

        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not active");
    }

    @Test
    void refusesToStartWhenAReservedUserIdBelongsToAnotherEmail() {
        InMemoryCompanyRepository companyRepository = new InMemoryCompanyRepository();
        InMemoryUserAccountRepository userAccountRepository = new InMemoryUserAccountRepository();
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
        userAccountRepository.insert(UserAccount.create(
                ADMIN_USER_ID,
                COMPANY_ID,
                "ID collision",
                "different@example.com",
                passwordEncoder.encode(ADMIN_PASSWORD),
                UserRole.ADMIN,
                NOW
        ));
        DemoAuthSeedRunner runner = runner(
                properties(ADMIN_PASSWORD),
                companyRepository,
                userAccountRepository,
                passwordEncoder
        );

        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("reserved demo user id");
    }

    @Test
    void refusesToStartWhenAReservedEmailBelongsToAnotherAccount() {
        InMemoryCompanyRepository companyRepository = new InMemoryCompanyRepository();
        InMemoryUserAccountRepository userAccountRepository = new InMemoryUserAccountRepository();
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
        userAccountRepository.insert(UserAccount.create(
                UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001"),
                TEST_COMPANY_ID,
                "Email collision",
                ADMIN_EMAIL,
                passwordEncoder.encode(ADMIN_PASSWORD),
                UserRole.HR,
                NOW
        ));
        DemoAuthSeedRunner runner = runner(
                properties(ADMIN_PASSWORD),
                companyRepository,
                userAccountRepository,
                passwordEncoder
        );

        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("configured demo email");
    }

    private DemoAuthSeedRunner runner(
            DemoAuthSeedProperties properties,
            CompanyRepository companyRepository,
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder
    ) {
        return runner(
                properties,
                companyRepository,
                (companyId, now) -> {
                },
                userAccountRepository,
                passwordEncoder
        );
    }

    private DemoAuthSeedRunner runner(
            DemoAuthSeedProperties properties,
            CompanyRepository companyRepository,
            CompanySettingsProvisioner companySettingsProvisioner,
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder
    ) {
        return new DemoAuthSeedRunner(
                properties,
                companyRepository,
                companySettingsProvisioner,
                userAccountRepository,
                passwordEncoder,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private static final class RecordingCompanySettingsProvisioner
            implements CompanySettingsProvisioner {

        private final java.util.List<UUID> companyIds = new java.util.ArrayList<>();

        @Override
        public void provisionDefaults(UUID companyId, Instant now) {
            companyIds.add(companyId);
        }
    }

    private DemoAuthSeedProperties properties(String password) {
        return new DemoAuthSeedProperties(
                true,
                COMPANY_ID,
                "FOWOCO Demo Company",
                TEST_COMPANY_ID,
                "FOWOCO Test Company",
                ADMIN_USER_ID,
                ADMIN_DISPLAY_NAME,
                ADMIN_EMAIL,
                password
        );
    }

    private static final class InMemoryCompanyRepository implements CompanyRepository {

        private final Map<UUID, Company> companies = new LinkedHashMap<>();

        @Override
        public Optional<Company> findById(UUID companyId) {
            return Optional.ofNullable(companies.get(companyId));
        }

        @Override
        public void insert(Company company) {
            if (companies.putIfAbsent(company.companyId(), company) != null) {
                throw new IllegalStateException("duplicate company");
            }
        }

        @Override
        public List<UUID> findAllIds() {
            return List.copyOf(companies.keySet());
        }
    }

    private static final class InMemoryUserAccountRepository implements UserAccountRepository {

        private final Map<UUID, UserAccount> users = new LinkedHashMap<>();

        @Override
        public void insert(UserAccount userAccount) {
            if (users.putIfAbsent(userAccount.userId(), userAccount) != null) {
                throw new IllegalStateException("duplicate user");
            }
        }

        @Override
        public void update(UserAccount userAccount) {
            if (!users.containsKey(userAccount.userId())) {
                throw new IllegalStateException("missing user");
            }
            users.put(userAccount.userId(), userAccount);
        }

        @Override
        public boolean existsByNormalizedEmail(String normalizedEmail) {
            return users.values().stream()
                    .anyMatch(user -> user.normalizedEmail().equals(normalizedEmail));
        }

        @Override
        public Optional<UserAccount> findByNormalizedEmail(String normalizedEmail) {
            return users.values().stream()
                    .filter(user -> user.normalizedEmail().equals(normalizedEmail))
                    .findFirst();
        }

        @Override
        public Optional<UserAccount> findByNormalizedEmailWithLock(String normalizedEmail) {
            return findByNormalizedEmail(normalizedEmail);
        }

        @Override
        public Optional<UserAccount> findByUserIdAndCompanyId(UUID userId, UUID companyId) {
            return Optional.ofNullable(users.get(userId))
                    .filter(user -> user.companyId().equals(companyId));
        }

        @Override
        public Optional<UserAccount> findByUserIdAndCompanyIdWithLock(UUID userId, UUID companyId) {
            return findByUserIdAndCompanyId(userId, companyId);
        }
    }
}
