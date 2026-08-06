package com.fowoco.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class V20TenantIntegrityMigrationTest {

    private static final String COMPANY_A = "10000000-0000-0000-0000-000000000001";
    private static final String COMPANY_B = "20000000-0000-0000-0000-000000000002";
    private static final String WORKER_A = "12000000-0000-0000-0000-000000000001";
    private static final String WORKER_A2 = "12000000-0000-0000-0000-000000000002";
    private static final String TASK_A = "13000000-0000-0000-0000-000000000001";
    private static final String LINK_A = "14000000-0000-0000-0000-000000000001";
    private static final String RESPONSE_A = "15000000-0000-0000-0000-000000000001";
    private static final String FILE_A = "16000000-0000-0000-0000-000000000001";
    private static final String FILE_B = "26000000-0000-0000-0000-000000000002";

    @Test
    void backfillsCompanyIdsBeforeEnforcingTenantAwareRelationships() throws SQLException {
        try (Connection connection = dataSource().getConnection()) {
            createPreV20Schema(connection);
            insertBaseFixture(connection, FILE_A, COMPANY_A);
            execute(connection, """
                    INSERT INTO worker_response_upload (response_id, stored_file_id)
                    VALUES ('%s', '%s')
                    """.formatted(RESPONSE_A, FILE_A));
            execute(connection, """
                    INSERT INTO worker_document_upload_idempotency (
                        worker_link_id, client_request_id, stored_file_id
                    ) VALUES ('%s', 'upload-request-1', '%s')
                    """.formatted(LINK_A, FILE_A));
            execute(connection, workerDocumentInsert(WORKER_A, TASK_A));

            applyV20(connection);

            assertThat(queryString(
                    connection,
                    "SELECT company_id FROM worker_response_upload WHERE response_id = '" + RESPONSE_A + "'"
            )).isEqualTo(COMPANY_A);
            assertThat(queryString(
                    connection,
                    "SELECT company_id FROM worker_document_upload_idempotency "
                            + "WHERE worker_link_id = '" + LINK_A + "'"
            )).isEqualTo(COMPANY_A);
        }
    }

    @Test
    void rejectsExistingCrossTenantStoredFileRelationship() throws SQLException {
        try (Connection connection = dataSource().getConnection()) {
            createPreV20Schema(connection);
            insertBaseFixture(connection, FILE_B, COMPANY_B);
            execute(connection, """
                    INSERT INTO worker_document_upload_idempotency (
                        worker_link_id, client_request_id, stored_file_id
                    ) VALUES ('%s', 'cross-tenant-upload', '%s')
                    """.formatted(LINK_A, FILE_B));

            assertThatThrownBy(() -> applyV20(connection))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Test
    void rejectsExistingTaskLinkedToAnotherWorker() throws SQLException {
        try (Connection connection = dataSource().getConnection()) {
            createPreV20Schema(connection);
            insertBaseFixture(connection, FILE_A, COMPANY_A);
            execute(connection, workerDocumentInsert(WORKER_A2, TASK_A));

            assertThatThrownBy(() -> applyV20(connection))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Test
    void preventsStoredFileFromBeingLinkedToMultipleResponses() throws SQLException {
        try (Connection connection = dataSource().getConnection()) {
            createPreV20Schema(connection);
            insertBaseFixture(connection, FILE_A, COMPANY_A);
            applyV20(connection);

            String secondResponseId = "15000000-0000-0000-0000-000000000002";
            execute(connection, """
                    INSERT INTO worker_response (
                        response_id, worker_link_id, company_id
                    ) VALUES ('%s', '%s', '%s')
                    """.formatted(secondResponseId, LINK_A, COMPANY_A));
            execute(connection, """
                    INSERT INTO worker_response_upload (
                        response_id, stored_file_id, company_id
                    ) VALUES ('%s', '%s', '%s')
                    """.formatted(RESPONSE_A, FILE_A, COMPANY_A));

            assertThatThrownBy(() -> execute(connection, """
                    INSERT INTO worker_response_upload (
                        response_id, stored_file_id, company_id
                    ) VALUES ('%s', '%s', '%s')
                    """.formatted(secondResponseId, FILE_A, COMPANY_A)))
                    .isInstanceOf(SQLException.class);
        }
    }

    private void createPreV20Schema(Connection connection) {
        String sql = """
                CREATE TABLE task (
                    task_id UUID NOT NULL,
                    worker_id UUID NOT NULL,
                    company_id UUID NOT NULL,
                    CONSTRAINT pk_task PRIMARY KEY (task_id),
                    CONSTRAINT uq_task_id_company UNIQUE (task_id, company_id)
                );

                CREATE TABLE worker_document (
                    worker_document_id UUID NOT NULL,
                    worker_id UUID NOT NULL,
                    company_id UUID NOT NULL,
                    task_id UUID,
                    CONSTRAINT pk_worker_document PRIMARY KEY (worker_document_id),
                    CONSTRAINT fk_worker_document_task_company
                        FOREIGN KEY (task_id, company_id)
                        REFERENCES task (task_id, company_id) ON DELETE RESTRICT
                );

                CREATE TABLE worker_link (
                    worker_link_id UUID NOT NULL,
                    company_id UUID NOT NULL,
                    replaces_link_id UUID,
                    CONSTRAINT pk_worker_link PRIMARY KEY (worker_link_id),
                    CONSTRAINT fk_worker_link_replaces
                        FOREIGN KEY (replaces_link_id)
                        REFERENCES worker_link (worker_link_id) ON DELETE SET NULL
                );

                CREATE TABLE worker_response (
                    response_id UUID NOT NULL,
                    worker_link_id UUID NOT NULL,
                    company_id UUID NOT NULL,
                    CONSTRAINT pk_worker_response PRIMARY KEY (response_id),
                    CONSTRAINT fk_worker_response_link
                        FOREIGN KEY (worker_link_id)
                        REFERENCES worker_link (worker_link_id) ON DELETE RESTRICT
                );

                CREATE TABLE stored_file (
                    stored_file_id UUID NOT NULL,
                    company_id UUID NOT NULL,
                    CONSTRAINT pk_stored_file PRIMARY KEY (stored_file_id)
                );

                CREATE TABLE worker_response_upload (
                    response_id UUID NOT NULL,
                    stored_file_id UUID NOT NULL,
                    CONSTRAINT pk_worker_response_upload PRIMARY KEY (response_id, stored_file_id),
                    CONSTRAINT fk_worker_response_upload_response
                        FOREIGN KEY (response_id)
                        REFERENCES worker_response (response_id) ON DELETE CASCADE,
                    CONSTRAINT fk_worker_response_upload_file
                        FOREIGN KEY (stored_file_id)
                        REFERENCES stored_file (stored_file_id) ON DELETE RESTRICT
                );

                CREATE TABLE worker_document_upload_idempotency (
                    worker_link_id UUID NOT NULL,
                    client_request_id VARCHAR(100) NOT NULL,
                    stored_file_id UUID NOT NULL,
                    CONSTRAINT pk_worker_document_upload_idempotency
                        PRIMARY KEY (worker_link_id, client_request_id),
                    CONSTRAINT fk_worker_document_upload_idempotency_link
                        FOREIGN KEY (worker_link_id)
                        REFERENCES worker_link (worker_link_id) ON DELETE RESTRICT,
                    CONSTRAINT fk_worker_document_upload_idempotency_file
                        FOREIGN KEY (stored_file_id)
                        REFERENCES stored_file (stored_file_id) ON DELETE RESTRICT
                );
                """;
        ScriptUtils.executeSqlScript(
                connection,
                new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8))
        );
    }

    private void insertBaseFixture(
            Connection connection,
            String storedFileId,
            String storedFileCompanyId
    ) throws SQLException {
        execute(connection, """
                INSERT INTO task (task_id, worker_id, company_id)
                VALUES ('%s', '%s', '%s')
                """.formatted(TASK_A, WORKER_A, COMPANY_A));
        execute(connection, """
                INSERT INTO stored_file (stored_file_id, company_id)
                VALUES ('%s', '%s')
                """.formatted(storedFileId, storedFileCompanyId));
        execute(connection, """
                INSERT INTO worker_link (worker_link_id, company_id)
                VALUES ('%s', '%s')
                """.formatted(LINK_A, COMPANY_A));
        execute(connection, """
                INSERT INTO worker_response (response_id, worker_link_id, company_id)
                VALUES ('%s', '%s', '%s')
                """.formatted(RESPONSE_A, LINK_A, COMPANY_A));
    }

    private String workerDocumentInsert(String workerId, String taskId) {
        return """
                INSERT INTO worker_document (
                    worker_document_id, worker_id, company_id, task_id
                ) VALUES ('%s', '%s', '%s', '%s')
                """.formatted(UUID.randomUUID(), workerId, COMPANY_A, taskId);
    }

    private void applyV20(Connection connection) {
        ScriptUtils.executeSqlScript(
                connection,
                new ClassPathResource("db/migration/V20__harden_tenant_integrity.sql")
        );
    }

    private DataSource dataSource() {
        String url = "jdbc:h2:mem:v20_" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH";
        return new DriverManagerDataSource(url, "sa", "");
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private String queryString(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getString(1);
        }
    }
}
