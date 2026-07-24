package com.fowoco.server.auth.infrastructure.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(properties = {
        "app.demo-seed.enabled=true",
        "app.demo-seed.admin-password=Demo-password-1!"
})
class DemoAuthSeedIntegrationTest {

    private static final UUID COMPANY_ID = UUID.fromString("90000000-0000-0000-0000-000000000001");
    private static final UUID ADMIN_USER_ID = UUID.fromString("90000000-0000-0000-0000-000000000002");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void enabledSeedCreatesAnActiveCompanyAndHashedAdminAfterFlyway() {
        String passwordHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM user_account WHERE user_id = ? AND company_id = ?",
                String.class,
                ADMIN_USER_ID,
                COMPANY_ID
        );

        assertThat(passwordHash).isNotEqualTo("Demo-password-1!");
        assertThat(passwordEncoder.matches("Demo-password-1!", passwordHash)).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM company WHERE company_id = ? AND status = 'ACTIVE'",
                Integer.class,
                COMPANY_ID
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_account WHERE user_id = ? AND display_name = '데모 관리자' "
                        + "AND role = 'ADMIN' AND status = 'ACTIVE'",
                Integer.class,
                ADMIN_USER_ID
        )).isEqualTo(1);
    }
}
