package com.fowoco.server.settings;

import static org.assertj.core.api.Assertions.assertThat;

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
class PostgreSqlCompanyMemberRlsTest {

    private static final UUID COMPANY_A = UUID.fromString("d1000000-0000-0000-0000-000000000001");
    private static final UUID COMPANY_B = UUID.fromString("d2000000-0000-0000-0000-000000000002");

    @Test
    void userAccountDirectoryFailsClosedAndOnlyReadsTheBoundCompany() throws Exception {
        String url = required("POSTGRES_TEST_URL");
        String migrationUser = required("POSTGRES_TEST_USERNAME");
        String migrationPassword = required("POSTGRES_TEST_PASSWORD");
        String runtimeRole = "company_member_rls_"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String runtimePassword = "Company-member-" + UUID.randomUUID();

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
                         List.of("user_account")
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
                    assertThat(count(runtime)).isEqualTo(2);
                    runtime.rollback();

                    bind(runtime, COMPANY_B);
                    assertThat(count(runtime)).isEqualTo(1);
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
            statement.execute("GRANT SELECT ON TABLE public.user_account TO "
                    + quoteIdentifier(role));
            statement.execute("INSERT INTO company (company_id, name, status) VALUES "
                    + "('" + COMPANY_A + "', 'Member RLS A', 'ACTIVE'),"
                    + "('" + COMPANY_B + "', 'Member RLS B', 'ACTIVE')");
            statement.execute("""
                    INSERT INTO user_account (
                        user_id, company_id, email, normalized_email,
                        password_hash, role, status, display_name
                    ) VALUES
                    ('d1100000-0000-0000-0000-000000000001',
                     'd1000000-0000-0000-0000-000000000001',
                     'member.rls.a1@example.com', 'member.rls.a1@example.com',
                     'hash-a1', 'ADMIN', 'ACTIVE', 'RLS A1'),
                    ('d1100000-0000-0000-0000-000000000002',
                     'd1000000-0000-0000-0000-000000000001',
                     'member.rls.a2@example.com', 'member.rls.a2@example.com',
                     'hash-a2', 'HR', 'SUSPENDED', 'RLS A2'),
                    ('d2100000-0000-0000-0000-000000000001',
                     'd2000000-0000-0000-0000-000000000002',
                     'member.rls.b1@example.com', 'member.rls.b1@example.com',
                     'hash-b1', 'VIEWER', 'ACTIVE', 'RLS B1')
                    """);
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
             var result = statement.executeQuery("SELECT COUNT(*) FROM user_account")) {
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
        statement.execute("DELETE FROM user_account WHERE company_id IN ('"
                + COMPANY_A + "','" + COMPANY_B + "')");
        statement.execute("DELETE FROM company WHERE company_id IN ('"
                + COMPANY_A + "','" + COMPANY_B + "')");
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
