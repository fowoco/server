package com.fowoco.server.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import com.fowoco.server.audit.application.port.AuditEventRepository;
import com.fowoco.server.auth.application.ActorContext;
import com.fowoco.server.auth.domain.UserRole;
import com.fowoco.server.common.web.RequestMetadata;
import com.fowoco.server.settings.application.CompanySettingsService;
import com.fowoco.server.settings.application.PatchField;
import com.fowoco.server.settings.application.UpdateCompanySettingsCommand;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ActiveProfiles("test")
@SpringBootTest
class CompanySettingsAuditRollbackIntegrationTest {

    private static final UUID COMPANY_ID =
            UUID.fromString("63000000-0000-0000-0000-000000000001");
    private static final UUID ACTOR_ID =
            UUID.fromString("63100000-0000-0000-0000-000000000001");

    @Autowired
    private CompanySettingsService companySettingsService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private AuditEventRepository auditEventRepository;

    @BeforeEach
    void seedSettings() {
        reset(auditEventRepository);
        jdbcTemplate.update("DELETE FROM company WHERE company_id = ?", COMPANY_ID);
        jdbcTemplate.update(
                "INSERT INTO company (company_id, name, status) VALUES (?, 'Audit rollback', 'ACTIVE')",
                COMPANY_ID
        );
        jdbcTemplate.update(
                """
                INSERT INTO company_settings (company_id, link_expiry_hours, version)
                VALUES (?, 48, 2)
                """,
                COMPANY_ID
        );
    }

    @Test
    void settingsUpdateRollsBackWhenAuditAppendFails() {
        doThrow(new IllegalStateException("audit unavailable"))
                .when(auditEventRepository)
                .append(any());
        UpdateCompanySettingsCommand command = new UpdateCompanySettingsCommand(
                2,
                PatchField.absent(),
                PatchField.of(72L),
                PatchField.absent(),
                PatchField.absent(),
                PatchField.absent(),
                PatchField.absent()
        );

        assertThatThrownBy(() -> companySettingsService.update(
                new ActorContext(ACTOR_ID, COMPANY_ID, java.util.Set.of(UserRole.ADMIN)),
                command,
                new RequestMetadata("settings-audit-rollback", null)
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("audit unavailable");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT link_expiry_hours FROM company_settings WHERE company_id = ?",
                Long.class,
                COMPANY_ID
        )).isEqualTo(48L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT version FROM company_settings WHERE company_id = ?",
                Long.class,
                COMPANY_ID
        )).isEqualTo(2L);
    }
}
