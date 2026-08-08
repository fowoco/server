package com.fowoco.server.workerimport;

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
class PostgreSqlWorkerImportRlsTest {

    private static final UUID COMPANY_A = UUID.fromString("e1000000-0000-0000-0000-000000000001");
    private static final UUID COMPANY_B = UUID.fromString("e2000000-0000-0000-0000-000000000002");
    private static final UUID USER_A = UUID.fromString("e3000000-0000-0000-0000-000000000001");
    private static final UUID USER_B = UUID.fromString("e4000000-0000-0000-0000-000000000002");
    private static final UUID FILE_A = UUID.fromString("e5000000-0000-0000-0000-000000000001");
    private static final UUID FILE_B = UUID.fromString("e6000000-0000-0000-0000-000000000002");
    private static final UUID IMPORT_A = UUID.fromString("e7000000-0000-0000-0000-000000000001");
    private static final UUID IMPORT_B = UUID.fromString("e8000000-0000-0000-0000-000000000002");
    private static final List<String> TABLES = List.of(
            "worker_import_job",
            "worker_import_row",
            "worker_import_commit_idempotency"
    );

    @Test
    void importTablesFailClosedAndHideOtherCompanyRows() throws Exception {
        String url = required("POSTGRES_TEST_URL");
        String migrationUser = required("POSTGRES_TEST_USERNAME");
        String migrationPassword = required("POSTGRES_TEST_PASSWORD");
        String runtimeRole = "worker_import_rls_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String runtimePassword = "Worker-import-" + UUID.randomUUID();

        try (PostgreSqlRlsTestLock ignored = PostgreSqlRlsTestLock.acquire(url, migrationUser, migrationPassword)) {
            Flyway.configure().dataSource(url, migrationUser, migrationPassword)
                    .locations("classpath:db/migration", "classpath:db/migration-postgresql")
                    .load().migrate();
            try (Connection admin = DriverManager.getConnection(url, migrationUser, migrationPassword);
                 PostgreSqlRlsStateFixture state = PostgreSqlRlsStateFixture.capture(admin, TABLES)) {
                state.disableRowLevelSecurityForFixtureSetup();
                prepare(admin, runtimeRole, runtimePassword);
                state.enableRowLevelSecurity();
                try (Connection runtime = DriverManager.getConnection(url, runtimeRole, runtimePassword)) {
                    runtime.setAutoCommit(false);
                    assertThat(count(runtime, "SELECT COUNT(*) FROM worker_import_job")).isZero();
                    runtime.rollback();

                    bind(runtime, COMPANY_A);
                    assertThat(count(runtime, "SELECT COUNT(*) FROM worker_import_job")).isEqualTo(1);
                    assertThat(count(runtime, "SELECT COUNT(*) FROM worker_import_row")).isEqualTo(1);
                    assertThat(count(runtime, "SELECT COUNT(*) FROM worker_import_commit_idempotency")).isEqualTo(1);
                    runtime.rollback();

                    bind(runtime, COMPANY_A);
                    assertThatThrownBy(() -> runtime.createStatement().executeUpdate("""
                            INSERT INTO worker_import_row (
                                import_row_id, import_id, company_id, row_number,
                                source_values_json, override_values_json, normalized_values_json,
                                validation_errors_json, status, created_at, updated_at
                            ) VALUES (
                                'e9000000-0000-0000-0000-000000000009',
                                'e8000000-0000-0000-0000-000000000002',
                                'e2000000-0000-0000-0000-000000000002',
                                3, '{}', '{}', '{}', '[]', 'PENDING',
                                CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                            )
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
            statement.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.worker_import_job,"
                    + " public.worker_import_row, public.worker_import_commit_idempotency TO "
                    + quoteIdentifier(role));
            statement.execute("INSERT INTO company (company_id, name, status) VALUES "
                    + "('" + COMPANY_A + "', 'Import RLS A', 'ACTIVE'),"
                    + "('" + COMPANY_B + "', 'Import RLS B', 'ACTIVE')");
            statement.execute("INSERT INTO user_account "
                    + "(user_id, company_id, email, normalized_email, password_hash, role, status) VALUES "
                    + "('" + USER_A + "','" + COMPANY_A + "','import-a@example.com','import-a@example.com','hash','HR','ACTIVE'),"
                    + "('" + USER_B + "','" + COMPANY_B + "','import-b@example.com','import-b@example.com','hash','HR','ACTIVE')");
            statement.execute("INSERT INTO stored_file "
                    + "(stored_file_id, company_id, name, mime_type, size, purpose, storage_key, scan_status, verified) VALUES "
                    + "('" + FILE_A + "','" + COMPANY_A + "','a.csv','text/csv',10,'WORKER_IMPORT_SOURCE','a','NOT_SCANNED',false),"
                    + "('" + FILE_B + "','" + COMPANY_B + "','b.csv','text/csv',10,'WORKER_IMPORT_SOURCE','b','NOT_SCANNED',false)");
            insertJob(statement, IMPORT_A, COMPANY_A, FILE_A, USER_A, "a");
            insertJob(statement, IMPORT_B, COMPANY_B, FILE_B, USER_B, "b");
            insertRow(statement, "ea000000-0000-0000-0000-000000000001", IMPORT_A, COMPANY_A);
            insertRow(statement, "eb000000-0000-0000-0000-000000000002", IMPORT_B, COMPANY_B);
            insertCommit(statement, IMPORT_A, COMPANY_A, "c");
            insertCommit(statement, IMPORT_B, COMPANY_B, "d");
        }
    }

    private void insertJob(Statement statement, UUID importId, UUID companyId, UUID fileId, UUID userId, String key)
            throws SQLException {
        statement.execute("INSERT INTO worker_import_job "
                + "(import_id, company_id, source_file_id, created_by, status, source_headers_json, mapping_json,"
                + " create_idempotency_key_hash, create_request_hash, total_rows, source_file_expires_at, created_at, updated_at) VALUES "
                + "('" + importId + "','" + companyId + "','" + fileId + "','" + userId
                + "','UPLOADED','[\"name\"]','{}',repeat('" + key + "',64),repeat('" + key
                + "',64),1,CURRENT_TIMESTAMP + INTERVAL '7 day',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
    }

    private void insertRow(Statement statement, String rowId, UUID importId, UUID companyId) throws SQLException {
        statement.execute("INSERT INTO worker_import_row "
                + "(import_row_id, import_id, company_id, row_number, source_values_json, override_values_json,"
                + " normalized_values_json, validation_errors_json, status, created_at, updated_at) VALUES "
                + "('" + rowId + "','" + importId + "','" + companyId
                + "',2,'{}','{}','{}','[]','PENDING',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
    }

    private void insertCommit(Statement statement, UUID importId, UUID companyId, String key) throws SQLException {
        statement.execute("INSERT INTO worker_import_commit_idempotency "
                + "(company_id, import_id, idempotency_key_hash, request_hash, response_snapshot_json, created_at) VALUES "
                + "('" + companyId + "','" + importId + "',repeat('" + key + "',64),repeat('" + key
                + "',64),'{}',CURRENT_TIMESTAMP)");
    }

    private void bind(Connection connection, UUID companyId) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT set_config('app.company_id', ?, true)")) {
            statement.setString(1, companyId.toString());
            statement.execute();
        }
    }

    private int count(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
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
        statement.execute("DELETE FROM worker_import_commit_idempotency WHERE company_id IN ('"
                + COMPANY_A + "','" + COMPANY_B + "')");
        statement.execute("DELETE FROM worker_import_row WHERE company_id IN ('" + COMPANY_A + "','" + COMPANY_B + "')");
        statement.execute("DELETE FROM worker_import_job WHERE company_id IN ('" + COMPANY_A + "','" + COMPANY_B + "')");
        statement.execute("DELETE FROM stored_file WHERE company_id IN ('" + COMPANY_A + "','" + COMPANY_B + "')");
        statement.execute("DELETE FROM user_account WHERE company_id IN ('" + COMPANY_A + "','" + COMPANY_B + "')");
        statement.execute("DELETE FROM company WHERE company_id IN ('" + COMPANY_A + "','" + COMPANY_B + "')");
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
