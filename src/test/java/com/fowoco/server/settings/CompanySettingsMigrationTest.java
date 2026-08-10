package com.fowoco.server.settings;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

class CompanySettingsMigrationTest {

    @Test
    void migrationBackfillsFrozenDefaultsForExistingCompanies() throws Exception {
        String url = "jdbc:h2:mem:company-settings-migration-" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1";
        try (Connection keepAlive = DriverManager.getConnection(url, "sa", "")) {
            SingleConnectionDataSource dataSource = new SingleConnectionDataSource(keepAlive, true);
            Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .target(MigrationVersion.fromVersion("32"))
                    .load()
                    .migrate();

            UUID companyId = UUID.fromString("10000000-0000-0000-0000-000000000001");
            try (Statement statement = keepAlive.createStatement()) {
                statement.executeUpdate("""
                        INSERT INTO company (company_id, name, status)
                        VALUES ('10000000-0000-0000-0000-000000000001', 'Existing Company', 'ACTIVE')
                        """);
            }

            Flyway flyway = Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .load();
            flyway.migrate();
            flyway.validate();

            try (var statement = keepAlive.prepareStatement("""
                     SELECT approval_policy, link_expiry_hours, evidence_rules_json,
                            file_retention_days, ai_log_retention_days,
                            audit_visibility, version
                     FROM company_settings
                     WHERE company_id = ?
                     """)) {
                statement.setObject(1, companyId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    assertThat(resultSet.next()).isTrue();
                    assertThat(resultSet.getString("approval_policy")).isEqualTo("ADMIN_OR_HR");
                    assertThat(resultSet.getLong("link_expiry_hours")).isEqualTo(72L);
                    assertThat(resultSet.getString("evidence_rules_json")).isEqualTo("{}");
                    assertThat(resultSet.getInt("file_retention_days")).isEqualTo(365);
                    assertThat(resultSet.getInt("ai_log_retention_days")).isEqualTo(90);
                    assertThat(resultSet.getString("audit_visibility")).isEqualTo("ADMIN_ONLY");
                    assertThat(resultSet.getLong("version")).isZero();
                    assertThat(resultSet.next()).isFalse();
                }
            }
        }
    }
}
