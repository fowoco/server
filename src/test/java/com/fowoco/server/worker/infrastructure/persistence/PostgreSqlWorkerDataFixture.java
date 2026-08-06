package com.fowoco.server.worker.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.fowoco.server.worker.domain.Worker;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

final class PostgreSqlWorkerDataFixture {

    private static final UUID COMPANY_A =
            UUID.fromString("a9700000-0000-0000-0000-000000000001");
    private static final UUID COMPANY_B =
            UUID.fromString("b9700000-0000-0000-0000-000000000002");
    private static final UUID WORKER_A =
            UUID.fromString("a9710000-0000-0000-0000-000000000001");
    private static final UUID WORKER_B =
            UUID.fromString("b9710000-0000-0000-0000-000000000002");
    private static final UUID WORKER_DOCUMENT_A =
            UUID.fromString("a9720000-0000-0000-0000-000000000001");
    private static final UUID OUTSIDE_TRANSACTION_WORKER =
            UUID.fromString("a9730000-0000-0000-0000-000000000001");
    private static final UUID UNBOUND_TRANSACTION_WORKER =
            UUID.fromString("a9730000-0000-0000-0000-000000000002");
    private static final UUID BOUND_ROLLBACK_WORKER =
            UUID.fromString("a9730000-0000-0000-0000-000000000003");
    private static final Instant FIXTURE_TIME = Instant.parse("2026-08-06T01:00:00Z");
    private static final List<String> RLS_TABLES =
            List.of("company", "worker", "worker_document");

    private JdbcTemplate migrationJdbc;
    private TransactionTemplate migrationTransactionTemplate;
    private WorkerRow originalWorkerA;
    private WorkerRow originalWorkerB;
    private List<WorkerDocumentRow> originalWorkerADocuments;
    private boolean rowsCreated;

    List<String> rlsTables() {
        return RLS_TABLES;
    }

    void prepare(DriverManagerDataSource migrationDataSource) {
        migrationJdbc = new JdbcTemplate(migrationDataSource);
        migrationTransactionTemplate = new TransactionTemplate(
                new DataSourceTransactionManager(migrationDataSource)
        );

        migrationTransactionTemplate.executeWithoutResult(status -> {
            assertFixtureIdsAreAvailable();
            insertCompany(COMPANY_A, "Repository RLS Tenant A");
            insertCompany(COMPANY_B, "Repository RLS Tenant B");
            insertWorker(
                    WORKER_A,
                    COMPANY_A,
                    "Repository Worker A",
                    "VN",
                    "ko",
                    LocalDate.of(2027, 8, 31)
            );
            insertWorker(
                    WORKER_B,
                    COMPANY_B,
                    "Repository Worker B",
                    "PH",
                    "en",
                    LocalDate.of(2027, 9, 30)
            );
            insertWorkerDocument();
        });
        rowsCreated = true;

        originalWorkerA = workerRows(WORKER_A).get(0);
        originalWorkerB = workerRows(WORKER_B).get(0);
        originalWorkerADocuments = workerDocumentRows(WORKER_A);
    }

    void cleanup() {
        if (!rowsCreated) {
            return;
        }

        migrationTransactionTemplate.executeWithoutResult(status -> {
            migrationJdbc.update(
                    """
                    DELETE FROM public.worker_document
                    WHERE worker_document_id = ?
                       OR worker_id IN (?, ?)
                    """,
                    WORKER_DOCUMENT_A,
                    WORKER_A,
                    WORKER_B
            );
            migrationJdbc.update(
                    """
                    DELETE FROM public.worker
                    WHERE worker_id IN (?, ?, ?, ?, ?)
                    """,
                    WORKER_A,
                    WORKER_B,
                    OUTSIDE_TRANSACTION_WORKER,
                    UNBOUND_TRANSACTION_WORKER,
                    BOUND_ROLLBACK_WORKER
            );
            migrationJdbc.update(
                    "DELETE FROM public.company WHERE company_id IN (?, ?)",
                    COMPANY_A,
                    COMPANY_B
            );
        });
        rowsCreated = false;
    }

    void assertRowsUnchanged() {
        assertThat(workerRows(WORKER_A)).containsExactly(originalWorkerA);
        assertThat(workerRows(WORKER_B)).containsExactly(originalWorkerB);
        assertThat(workerDocumentRows(WORKER_A))
                .containsExactlyElementsOf(originalWorkerADocuments);
        assertThat(candidateWorkerCount()).isZero();
    }

    int candidateWorkerCount() {
        Integer count = migrationJdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM public.worker
                WHERE worker_id IN (?, ?, ?)
                """,
                Integer.class,
                OUTSIDE_TRANSACTION_WORKER,
                UNBOUND_TRANSACTION_WORKER,
                BOUND_ROLLBACK_WORKER
        );
        return count == null ? 0 : count;
    }

    Worker newWorker(UUID workerId, String displayName) {
        return Worker.create(
                workerId,
                COMPANY_A,
                displayName,
                "VN",
                "ko",
                LocalDate.of(2027, 8, 31),
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2027, 8, 31),
                FIXTURE_TIME.plusSeconds(7200)
        );
    }

    UUID companyA() {
        return COMPANY_A;
    }

    UUID companyB() {
        return COMPANY_B;
    }

    UUID workerA() {
        return WORKER_A;
    }

    UUID workerB() {
        return WORKER_B;
    }

    UUID outsideTransactionWorker() {
        return OUTSIDE_TRANSACTION_WORKER;
    }

    UUID unboundTransactionWorker() {
        return UNBOUND_TRANSACTION_WORKER;
    }

    UUID boundRollbackWorker() {
        return BOUND_ROLLBACK_WORKER;
    }

    Instant fixtureTime() {
        return FIXTURE_TIME;
    }

    private void assertFixtureIdsAreAvailable() {
        Integer existingIds = migrationJdbc.queryForObject(
                """
                SELECT
                    (SELECT COUNT(*) FROM public.company
                     WHERE company_id IN (?, ?))
                  + (SELECT COUNT(*) FROM public.worker
                     WHERE worker_id IN (?, ?, ?, ?, ?))
                  + (SELECT COUNT(*) FROM public.worker_document
                     WHERE worker_document_id = ?)
                """,
                Integer.class,
                COMPANY_A,
                COMPANY_B,
                WORKER_A,
                WORKER_B,
                OUTSIDE_TRANSACTION_WORKER,
                UNBOUND_TRANSACTION_WORKER,
                BOUND_ROLLBACK_WORKER,
                WORKER_DOCUMENT_A
        );
        if (existingIds == null || existingIds != 0) {
            throw new IllegalStateException(
                    "PostgreSQL repository RLS fixture IDs already exist; "
                            + "use an isolated test database or remove stale test rows"
            );
        }
    }

    private void insertCompany(UUID companyId, String name) {
        migrationJdbc.update(
                """
                INSERT INTO public.company (
                    company_id,
                    name,
                    status,
                    created_at,
                    updated_at,
                    version
                ) VALUES (?, ?, 'ACTIVE', ?, ?, 0)
                """,
                companyId,
                name,
                Timestamp.from(FIXTURE_TIME),
                Timestamp.from(FIXTURE_TIME)
        );
    }

    private void insertWorker(
            UUID workerId,
            UUID companyId,
            String displayName,
            String nationalityCode,
            String language,
            LocalDate stayExpiryDate
    ) {
        migrationJdbc.update(
                """
                INSERT INTO public.worker (
                    worker_id,
                    company_id,
                    display_name,
                    nationality_code,
                    preferred_language,
                    work_status,
                    stay_expiry_date,
                    contract_start_date,
                    contract_end_date,
                    created_at,
                    updated_at,
                    version
                ) VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?, ?, ?, 0)
                """,
                workerId,
                companyId,
                displayName,
                nationalityCode,
                language,
                stayExpiryDate,
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2027, 8, 31),
                Timestamp.from(FIXTURE_TIME),
                Timestamp.from(FIXTURE_TIME)
        );
    }

    private void insertWorkerDocument() {
        migrationJdbc.update(
                """
                INSERT INTO public.worker_document (
                    worker_document_id,
                    worker_id,
                    company_id,
                    document_type,
                    submission_status,
                    expiry_date,
                    destination,
                    note,
                    created_at,
                    updated_at,
                    version
                ) VALUES (?, ?, ?, 'PASSPORT_COPY', 'VERIFIED', ?, ?, ?, ?, ?, 0)
                """,
                WORKER_DOCUMENT_A,
                WORKER_A,
                COMPANY_A,
                LocalDate.of(2028, 8, 31),
                "Repository RLS fixture",
                "Must survive an unbound test-only delete",
                Timestamp.from(FIXTURE_TIME),
                Timestamp.from(FIXTURE_TIME)
        );
    }

    private List<WorkerRow> workerRows(UUID workerId) {
        return migrationJdbc.query(
                """
                SELECT
                    worker_id,
                    company_id,
                    display_name,
                    nationality_code,
                    preferred_language,
                    work_status,
                    stay_expiry_date,
                    contract_start_date,
                    contract_end_date,
                    created_at,
                    updated_at,
                    version
                FROM public.worker
                WHERE worker_id = ?
                """,
                (resultSet, rowNumber) -> new WorkerRow(
                        resultSet.getObject("worker_id", UUID.class),
                        resultSet.getObject("company_id", UUID.class),
                        resultSet.getString("display_name"),
                        resultSet.getString("nationality_code"),
                        resultSet.getString("preferred_language"),
                        resultSet.getString("work_status"),
                        resultSet.getObject("stay_expiry_date", LocalDate.class),
                        resultSet.getObject("contract_start_date", LocalDate.class),
                        resultSet.getObject("contract_end_date", LocalDate.class),
                        resultSet.getTimestamp("created_at").toInstant(),
                        resultSet.getTimestamp("updated_at").toInstant(),
                        resultSet.getLong("version")
                ),
                workerId
        );
    }

    private List<WorkerDocumentRow> workerDocumentRows(UUID workerId) {
        return migrationJdbc.query(
                """
                SELECT
                    worker_document_id,
                    worker_id,
                    company_id,
                    task_id,
                    document_type,
                    submission_status,
                    expiry_date,
                    destination,
                    note,
                    file_id,
                    created_at,
                    updated_at,
                    version
                FROM public.worker_document
                WHERE worker_id = ?
                ORDER BY worker_document_id
                """,
                (resultSet, rowNumber) -> new WorkerDocumentRow(
                        resultSet.getObject("worker_document_id", UUID.class),
                        resultSet.getObject("worker_id", UUID.class),
                        resultSet.getObject("company_id", UUID.class),
                        resultSet.getObject("task_id", UUID.class),
                        resultSet.getString("document_type"),
                        resultSet.getString("submission_status"),
                        resultSet.getObject("expiry_date", LocalDate.class),
                        resultSet.getString("destination"),
                        resultSet.getString("note"),
                        resultSet.getObject("file_id", UUID.class),
                        resultSet.getTimestamp("created_at").toInstant(),
                        resultSet.getTimestamp("updated_at").toInstant(),
                        resultSet.getLong("version")
                ),
                workerId
        );
    }

    private record WorkerRow(
            UUID workerId,
            UUID companyId,
            String displayName,
            String nationalityCode,
            String preferredLanguage,
            String workStatus,
            LocalDate stayExpiryDate,
            LocalDate contractStartDate,
            LocalDate contractEndDate,
            Instant createdAt,
            Instant updatedAt,
            long version
    ) {
    }

    private record WorkerDocumentRow(
            UUID workerDocumentId,
            UUID workerId,
            UUID companyId,
            UUID taskId,
            String documentType,
            String submissionStatus,
            LocalDate expiryDate,
            String destination,
            String note,
            UUID fileId,
            Instant createdAt,
            Instant updatedAt,
            long version
    ) {
    }
}
