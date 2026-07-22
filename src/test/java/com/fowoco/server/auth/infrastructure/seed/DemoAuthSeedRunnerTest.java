package com.fowoco.server.auth.infrastructure.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fowoco.server.auth.application.port.UserAccountRepository;
import com.fowoco.server.auth.domain.UserAccount;
import com.fowoco.server.company.application.port.CompanyRepository;
import com.fowoco.server.company.domain.Company;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class DemoAuthSeedRunnerTest {

    private static final UUID COMPANY_ID = UUID.fromString("90000000-0000-0000-0000-000000000001");
    private static final UUID ADMIN_USER_ID = UUID.fromString("90000000-0000-0000-0000-000000000002");
    private static final String ADMIN_EMAIL = "demo.admin@example.com";
    private static final String ADMIN_PASSWORD = "Demo-password-1!";
    private static final Instant NOW = Instant.parse("2026-07-22T00:00:00Z");

    @Test
    void createsAnIdempotentAdminSeedAndStoresOnlyThePasswordHash() throws Exception {
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
        runner.run(new DefaultApplicationArguments(new String[0]));

        assertThat(companyRepository.companies).hasSize(1);
        assertThat(userAccountRepository.users).hasSize(1);
        UserAccount admin = userAccountRepository.users.get(ADMIN_USER_ID);
        assertThat(admin.companyId()).isEqualTo(COMPANY_ID);
        assertThat(admin.normalizedEmail()).isEqualTo(ADMIN_EMAIL);
        assertThat(admin.passwordHash()).isNotEqualTo(ADMIN_PASSWORD);
        assertThat(passwordEncoder.matches(ADMIN_PASSWORD, admin.passwordHash())).isTrue();
        assertThat(admin.role().name()).isEqualTo("ADMIN");
        assertThat(admin.canLogin()).isTrue();
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

    private DemoAuthSeedRunner runner(
            DemoAuthSeedProperties properties,
            CompanyRepository companyRepository,
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder
    ) {
        return new DemoAuthSeedRunner(
                properties,
                companyRepository,
                userAccountRepository,
                passwordEncoder,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private DemoAuthSeedProperties properties(String password) {
        return new DemoAuthSeedProperties(
                true,
                COMPANY_ID,
                "FOWOCO Demo Company",
                ADMIN_USER_ID,
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
        public Optional<UserAccount> findByNormalizedEmail(String normalizedEmail) {
            return users.values().stream()
                    .filter(user -> user.normalizedEmail().equals(normalizedEmail))
                    .findFirst();
        }

        @Override
        public Optional<UserAccount> findByUserIdAndCompanyId(UUID userId, UUID companyId) {
            return Optional.ofNullable(users.get(userId))
                    .filter(user -> user.companyId().equals(companyId));
        }
    }
}
