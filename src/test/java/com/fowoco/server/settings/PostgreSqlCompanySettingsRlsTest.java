package com.fowoco.server.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fowoco.server.common.security.PostgreSqlRlsStateFixture;
import com.fowoco.server.common.security.PostgreSqlRlsTestLock;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "POSTGRES_TEST_ENABLED", matches = "true")
class PostgreSqlCompanySettingsRlsTest {

    private static final UUID COMPANY_A = UUID.fromString("c1000000-0000-0000-0000-000000000001");
    private static final UUID COMPANY_B = UUID.fromString("c2000000-0000-0000-0000-000000000002");
    private static final UUID COMPANY_C = UUID.fromString("c3000000-0000-0000-0000-000000000003");

    @Test
    void companySettingsFailClosedAndRejectCrossTenantWrites() throws Exception {
        String url = required("POSTGRES_TEST_URL");
        String migrationUser = required("POSTGRES_TEST_USERNAME");
        String migrationPassword = required("POSTGRES_TEST_PASSWORD");
        String runtimeRole = "company_settings_rls_"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String runtimePassword = "Company-settings-" + UUID.randomUUID();

        try (PostgreSqlRlsTestLock ignored = PostgreSqlRlsTestLock.acquire(
                url,
                migrationUser,
                migrationPassword
        )) {
            Flyway.configure()
                    .dataSource(url, migrationUser, migrationPassword)
                    .locations("classpath:db/migration", "classpath:db/migration-postgresql")
                    .load()
                    .migrate();
            try (Connection admin = DriverManager.getConnection(url, migrationUser, migrationPassword);
                 PostgreSqlRlsStateFixture state = PostgreSqlRlsStateFixture.capture(
                         admin,
                         List.of("company_settings")
                 )) {
                state.disableRowLevelSecurityForFixtureSetup();
                prepare(admin, runtimeRole, runtimePassword);
                state.enableRowLevelSecurity();
                try (Connection runtime = DriverManager.getConnection(
                        url,
                        runtimeRole,
                        runtimePassword
                )) {
                    runtime.setAutoCommit(false);
                    assertThat(count(runtime)).isZero();
                    runtime.rollback();

                    bind(runtime, COMPANY_A);
                    assertThat(count(runtime)).isEqualTo(1);
                    runtime.rollback();

                    bind(runtime, COMPANY_A);
                    assertThatThrownBy(() -> runtime.createStatement().executeUpdate("""
                            INSERT INTO company_settings (company_id)
                            VALUES ('c3000000-0000-0000-0000-000000000003')
                            """))
                            .isInstanceOf(SQLException.class)
                            .hasMessageContaining("row-level security");
                    runtime.rollback();
                } finally {
                    state.disableRowLevelSecurityForFixtureSetup();
                    cleanup(admin, runtimeRole);
                }
            }
        }
    }

    private void prepare(Connection connection, String role, String password) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            cleanupRows(statement);
            statement.execute("CREATE ROLE " + quoteIdentifier(role)
                    + " LOGIN PASSWORD " + quoteLiteral(password)
                    + " NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS");
            statement.execute("GRANT CONNECT ON DATABASE " + quoteIdentifier(connection.getCatalog())
                    + " TO " + quoteIdentifier(role));
            statement.execute("GRANT USAGE ON SCHEMA public TO " + quoteIdentifier(role));
            statement.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.company_settings TO "
                    + quoteIdentifier(role));
            statement.execute("INSERT INTO company (company_id, name, status) VALUES "
                    + "('" + COMPANY_A + "', 'Settings RLS A', 'ACTIVE'),"
                    + "('" + COMPANY_B + "', 'Settings RLS B', 'ACTIVE'),"
                    + "('" + COMPANY_C + "', 'Settings RLS C', 'ACTIVE')");
            statement.execute("INSERT INTO company_settings (company_id) VALUES "
                    + "('" + COMPANY_A + "'),('" + COMPANY_B + "')");
        }
    }

    private void bind(Connection connection, UUID companyId) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT set_config('app.company_id', ?, true)"
        )) {
            statement.setString(1, companyId.toString());
            statement.execute();
        }
    }

    private int count(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             var result = statement.executeQuery("SELECT COUNT(*) FROM company_settings")) {
            result.next();
            return result.getInt(1);
        }
    }

    private void cleanup(Connection connection, String role) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            cleanupRows(statement);
            statement.execute("DROP OWNED BY " + quoteIdentifier(role));
            statement.execute("DROP ROLE IF EXISTS " + quoteIdentifier(role));
        }
    }

    private void cleanupRows(Statement statement) throws SQLException {
        statement.execute("DELETE FROM company WHERE company_id IN ('"
                + COMPANY_A + "','" + COMPANY_B + "','" + COMPANY_C + "')");
    }

    private String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required");
        }
        return value;
    }

    private String quoteIdentifier(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private String quoteLiteral(String value) {
        return "'" + value.replace("'", "''") + "'";
    }
}
