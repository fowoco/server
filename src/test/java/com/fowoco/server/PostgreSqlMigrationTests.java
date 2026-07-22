package com.fowoco.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "POSTGRES_TEST_ENABLED", matches = "true")
class PostgreSqlMigrationTests {

    private static final String COMPANY_A = "10000000-0000-0000-0000-000000000001";
    private static final String COMPANY_B = "20000000-0000-0000-0000-000000000002";

    private static final Set<String> TABLES = Set.of(
            "company",
            "user_account",
            "worker",
            "task",
            "ticket",
            "document"
    );

    @Test
    void migrationsCreateAndProtectThePostgreSqlSchema() throws Exception {
        String url = requiredEnvironmentVariable("POSTGRES_TEST_URL");
        String username = requiredEnvironmentVariable("POSTGRES_TEST_USERNAME");
        String password = requiredEnvironmentVariable("POSTGRES_TEST_PASSWORD");

        Flyway flyway = Flyway.configure()
                .dataSource(url, username, password)
                .locations("classpath:db/migration")
                .load();

        flyway.migrate();
        flyway.validate();

        assertThat(flyway.info().current()).isNotNull();
        assertThat(flyway.info().pending()).isEmpty();

        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            assertTestAdministrator(connection);
            assertSchemaStructure(connection);
            cleanupTestData(connection);
            try {
                assertConstraintBehavior(connection);
                seedTenantData(connection);
                assertRowLevelSecurity(connection);
            } finally {
                cleanupTestData(connection);
            }
        }
    }

    private void assertSchemaStructure(Connection connection) throws SQLException {
        assertThat(queryStrings(connection, """
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_type = 'BASE TABLE'
                  AND table_name <> 'flyway_schema_history'
                """))
                .containsExactlyInAnyOrderElementsOf(TABLES);

        Map<String, ColumnSpec> actualColumns = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("""
                     SELECT table_name, column_name, udt_name, is_nullable
                     FROM information_schema.columns
                     WHERE table_schema = 'public'
                       AND table_name IN (
                           'company', 'user_account', 'worker', 'task',
                           'ticket', 'document'
                       )
                     """)) {
            while (resultSet.next()) {
                actualColumns.put(
                        resultSet.getString("table_name") + "." + resultSet.getString("column_name"),
                        new ColumnSpec(
                                resultSet.getString("udt_name"),
                                "YES".equals(resultSet.getString("is_nullable"))
                        )
                );
            }
        }
        assertThat(actualColumns).containsExactlyInAnyOrderEntriesOf(expectedColumns());

        assertConstraintNames(connection, "p", Set.of(
                "pk_company",
                "pk_user_account",
                "pk_worker",
                "pk_task",
                "pk_ticket",
                "pk_document"
        ));
        assertConstraintNames(connection, "u", Set.of(
                "uq_user_account_id",
                "uq_ticket_link_token_hash"
        ));
        assertConstraintNames(connection, "c", Set.of(
                "ck_user_account_role",
                "ck_worker_contract_dates",
                "ck_ticket_messages_json_array",
                "ck_document_has_tenant_link"
        ));
        assertForeignKeys(connection);

        assertIndexes(connection);

        assertThat(queryStrings(connection, """
                SELECT relname
                FROM pg_class
                JOIN pg_namespace ON pg_namespace.oid = pg_class.relnamespace
                WHERE pg_namespace.nspname = 'public'
                  AND pg_class.relkind = 'r'
                  AND pg_class.relrowsecurity
                  AND pg_class.relforcerowsecurity
                  AND relname <> 'flyway_schema_history'
                """))
                .containsExactlyInAnyOrderElementsOf(TABLES);

        Map<String, String> policies = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("""
                     SELECT policyname, cmd
                     FROM pg_policies
                     WHERE schemaname = 'public'
                     """)) {
            while (resultSet.next()) {
                policies.put(resultSet.getString("policyname"), resultSet.getString("cmd"));
            }
        }
        assertThat(policies).containsExactlyInAnyOrderEntriesOf(Map.of(
                "pol_company_tenant_all", "ALL",
                "pol_user_account_tenant_all", "ALL",
                "pol_worker_tenant_all", "ALL",
                "pol_task_tenant_all", "ALL",
                "pol_ticket_tenant_all", "ALL",
                "pol_document_tenant_all", "ALL"
        ));

        for (String table : Set.of(
                "user_account", "worker", "task", "ticket", "document"
        )) {
            assertThat(columnDefault(connection, table, "created_at"))
                    .as(table + ".created_at default")
                    .isEqualTo("CURRENT_TIMESTAMP");
        }
        assertThat(columnDefault(connection, "ticket", "messages_json"))
                .isEqualTo("'[]'::jsonb");

        for (String uuidPrimaryKey : Set.of(
                "company.company_name",
                "ticket.ticket_id",
                "document.document_id"
        )) {
            String[] parts = uuidPrimaryKey.split("\\.");
            assertThat(columnDefault(connection, parts[0], parts[1]))
                    .as(uuidPrimaryKey + " is generated by the application")
                    .isNull();
        }

        assertThat(columnComment(connection, "user_account", "password"))
                .containsIgnoringCase("one-way")
                .containsIgnoringCase("plaintext");
    }

    private void assertConstraintBehavior(Connection connection) throws SQLException {
        execute(connection, """
                INSERT INTO company (company_name, name)
                VALUES ('30000000-0000-0000-0000-000000000003', 'constraint-test-company')
                """);

        assertSqlState(connection, "23514", """
                INSERT INTO user_account (user_id, company_name, role, id, password)
                VALUES (
                    'invalid-role-user',
                    '30000000-0000-0000-0000-000000000003',
                    'ADMIN',
                    'invalid-role@test.com',
                    'test-only-not-a-real-password-hash'
                )
                """);

        execute(connection, """
                INSERT INTO user_account (user_id, company_name, role, id, password)
                VALUES (
                    'constraint-user',
                    '30000000-0000-0000-0000-000000000003',
                    'OWNER',
                    'unique-login@test.com',
                    'test-only-not-a-real-password-hash'
                )
                """);
        assertSqlState(connection, "23505", """
                INSERT INTO user_account (user_id, company_name, role, id, password)
                VALUES (
                    'duplicate-login-user',
                    '30000000-0000-0000-0000-000000000003',
                    'HR_STAFF',
                    'unique-login@test.com',
                    'test-only-not-a-real-password-hash'
                )
                """);

        assertSqlState(connection, "23514", """
                INSERT INTO worker (
                    worker_id, legal_name_enc, company_name, employee_no,
                    nationality, visa_type, stay_expiry,
                    contract_start, contract_end, arrival_date
                ) VALUES (
                    'invalid-date-worker', decode('00', 'hex'),
                    '30000000-0000-0000-0000-000000000003', 'INVALID-DATE',
                    'TEST', 'E-9', DATE '2027-12-31',
                    DATE '2027-12-31', DATE '2027-01-01', DATE '2026-01-01'
                )
                """);

        execute(connection, """
                INSERT INTO task (task_id, company_name, task_type, status)
                VALUES (
                    'constraint-task',
                    '30000000-0000-0000-0000-000000000003',
                    'TEST',
                    'DRAFT'
                )
                """);
        assertSqlState(connection, "23514", """
                INSERT INTO ticket (ticket_id, task_id, status, messages_json)
                VALUES (
                    '30000000-0000-0000-0000-000000000030',
                    'constraint-task',
                    'OPEN',
                    '{"not": "an array"}'::jsonb
                )
                """);

        execute(connection, """
                INSERT INTO ticket (ticket_id, task_id, status)
                VALUES (
                    '30000000-0000-0000-0000-000000000031',
                    'constraint-task',
                    'OPEN'
                )
                """);
        assertThat(queryString(connection, """
                SELECT messages_json::text
                FROM ticket
                WHERE ticket_id = '30000000-0000-0000-0000-000000000031'
                """))
                .isEqualTo("[]");

        assertSqlState(connection, "23514", """
                INSERT INTO document (document_id, doc_type, status)
                VALUES (
                    '30000000-0000-0000-0000-000000000032',
                    'TEST',
                    'MISSING'
                )
                """);

        execute(connection, """
                DELETE FROM ticket
                WHERE ticket_id = '30000000-0000-0000-0000-000000000031'
                """);
        execute(connection, "DELETE FROM task WHERE task_id = 'constraint-task'");
        execute(connection, "DELETE FROM user_account WHERE user_id = 'constraint-user'");
        execute(connection, """
                DELETE FROM company
                WHERE company_name = '30000000-0000-0000-0000-000000000003'
                """);
    }

    private void seedTenantData(Connection connection) throws SQLException {
        execute(connection, """
                INSERT INTO company (company_name, name) VALUES
                    ('10000000-0000-0000-0000-000000000001', 'tenant-a'),
                    ('20000000-0000-0000-0000-000000000002', 'tenant-b')
                """);
        execute(connection, """
                INSERT INTO user_account (user_id, company_name, role, id, password) VALUES
                    ('user-a', '10000000-0000-0000-0000-000000000001', 'OWNER',
                     'owner-a@test.com', 'test-only-not-a-real-password-hash'),
                    ('user-b', '20000000-0000-0000-0000-000000000002', 'OWNER',
                     'owner-b@test.com', 'test-only-not-a-real-password-hash')
                """);
        execute(connection, """
                INSERT INTO worker (
                    worker_id, legal_name_enc, company_name, employee_no,
                    nationality, visa_type, stay_expiry,
                    contract_start, contract_end, arrival_date
                ) VALUES
                    ('worker-a', decode('01', 'hex'),
                     '10000000-0000-0000-0000-000000000001', 'EMP-A',
                     'TEST', 'E-9', DATE '2028-01-01',
                     DATE '2026-01-01', DATE '2027-01-01', DATE '2025-12-01'),
                    ('worker-b', decode('02', 'hex'),
                     '20000000-0000-0000-0000-000000000002', 'EMP-B',
                     'TEST', 'E-9', DATE '2028-02-01',
                     DATE '2026-02-01', DATE '2027-02-01', DATE '2025-12-02')
                """);
        execute(connection, """
                INSERT INTO task (
                    task_id, company_name, task_type, status, assignee_id
                ) VALUES
                    ('task-a', '10000000-0000-0000-0000-000000000001',
                     'TEST', 'DRAFT', 'user-a'),
                    ('task-b', '20000000-0000-0000-0000-000000000002',
                     'TEST', 'DRAFT', 'user-b'),
                    ('task-mixed', '10000000-0000-0000-0000-000000000001',
                     'TEST', 'DRAFT', 'user-b')
                """);
        execute(connection, """
                INSERT INTO ticket (
                    ticket_id, task_id, worker_id, assignee_id,
                    status, link_issued_by
                ) VALUES
                    ('10000000-0000-0000-0000-000000000010',
                     'task-a', 'worker-a', 'user-a', 'OPEN', 'user-a'),
                    ('20000000-0000-0000-0000-000000000020',
                     'task-b', 'worker-b', 'user-b', 'OPEN', 'user-b'),
                    ('90000000-0000-0000-0000-000000000097',
                     'task-a', 'worker-b', NULL, 'INCONSISTENT', NULL)
                """);
        execute(connection, """
                INSERT INTO document (
                    document_id, worker_id, task_id, ticket_id,
                    doc_type, status, verified_by
                ) VALUES
                    ('10000000-0000-0000-0000-000000000011',
                     'worker-a', 'task-a',
                     '10000000-0000-0000-0000-000000000010',
                     'TEST', 'SUBMITTED', 'user-a'),
                    ('20000000-0000-0000-0000-000000000021',
                     'worker-b', 'task-b',
                     '20000000-0000-0000-0000-000000000020',
                     'TEST', 'SUBMITTED', 'user-b'),
                    ('10000000-0000-0000-0000-000000000018',
                     NULL, NULL,
                     '10000000-0000-0000-0000-000000000010',
                     'TEST', 'TICKET_ONLY', NULL),
                    ('20000000-0000-0000-0000-000000000028',
                     NULL, NULL,
                     '20000000-0000-0000-0000-000000000020',
                     'TEST', 'TICKET_ONLY', NULL),
                    ('90000000-0000-0000-0000-000000000099',
                     'worker-a', 'task-b', NULL,
                     'TEST', 'INCONSISTENT', NULL)
                """);
    }

    private void cleanupTestData(Connection connection) throws SQLException {
        if (!connection.getAutoCommit()) {
            connection.rollback();
            connection.setAutoCommit(true);
        }
        execute(connection, "RESET ROLE");
        execute(connection, """
                DELETE FROM document
                WHERE document_id IN (
                    '10000000-0000-0000-0000-000000000011',
                    '10000000-0000-0000-0000-000000000018',
                    '20000000-0000-0000-0000-000000000021',
                    '20000000-0000-0000-0000-000000000028',
                    '90000000-0000-0000-0000-000000000099',
                    '30000000-0000-0000-0000-000000000032'
                )
                """);
        execute(connection, """
                DELETE FROM ticket
                WHERE ticket_id IN (
                    '10000000-0000-0000-0000-000000000010',
                    '20000000-0000-0000-0000-000000000020',
                    '90000000-0000-0000-0000-000000000097',
                    '30000000-0000-0000-0000-000000000030',
                    '30000000-0000-0000-0000-000000000031'
                )
                """);
        execute(connection, """
                DELETE FROM task
                WHERE task_id IN (
                    'task-a', 'task-b', 'task-mixed', 'task-runtime-a',
                    'constraint-task', 'cross-tenant-task', 'cross-completed-task'
                )
                """);
        execute(connection, """
                DELETE FROM worker
                WHERE worker_id IN ('worker-a', 'worker-b', 'invalid-date-worker')
                """);
        execute(connection, """
                DELETE FROM user_account
                WHERE user_id IN (
                    'user-a', 'user-b', 'constraint-user',
                    'invalid-role-user', 'duplicate-login-user'
                )
                """);
        execute(connection, """
                DELETE FROM company
                WHERE company_name IN (
                    '10000000-0000-0000-0000-000000000001',
                    '20000000-0000-0000-0000-000000000002',
                    '30000000-0000-0000-0000-000000000003',
                    '40000000-0000-0000-0000-000000000004'
                )
                """);
    }

    private void assertRowLevelSecurity(Connection connection) throws SQLException {
        String role = "fowoco_rls_test_" + UUID.randomUUID().toString().replace("-", "");
        String quotedRole = quoteIdentifier(role);

        execute(connection, """
                CREATE ROLE %s
                    NOLOGIN
                    NOSUPERUSER
                    NOCREATEDB
                    NOCREATEROLE
                    NOINHERIT
                    NOREPLICATION
                    NOBYPASSRLS
                """.formatted(quotedRole));
        try {
            execute(connection, "GRANT USAGE ON SCHEMA public TO " + quotedRole);
            execute(connection, """
                    GRANT SELECT, INSERT, UPDATE, DELETE
                    ON TABLE company, user_account, worker, task,
                             ticket, document
                    TO %s
                    """.formatted(quotedRole));

            assertRestrictedRole(connection, role);
            execute(connection, "SET ROLE " + quotedRole);
            connection.setAutoCommit(false);

            setTenantContext(connection, COMPANY_A);
            assertTenantVisibleRows(connection, COMPANY_A);
            assertThat(executeUpdate(connection, """
                    UPDATE worker
                    SET employee_no = 'EMP-A-UPDATED'
                    WHERE worker_id = 'worker-a'
                    """))
                    .isOne();
            assertThat(executeUpdate(connection, """
                    INSERT INTO task (
                        task_id, company_name, task_type, status, assignee_id
                    ) VALUES (
                        'task-runtime-a',
                        '10000000-0000-0000-0000-000000000001',
                        'TEST', 'DRAFT', 'user-a'
                    )
                    """))
                    .isOne();
            assertThat(executeUpdate(connection, """
                    UPDATE worker
                    SET employee_no = 'SHOULD-NOT-CHANGE'
                    WHERE worker_id = 'worker-b'
                    """))
                    .isZero();
            assertThat(executeUpdate(connection, """
                    DELETE FROM document
                    WHERE document_id = '20000000-0000-0000-0000-000000000021'
                    """))
                    .isZero();
            connection.commit();

            assertNoTenantContextLeaks(connection);

            setTenantContext(connection, COMPANY_B);
            assertTenantVisibleRows(connection, COMPANY_B);
            connection.commit();

            assertRlsRejected(connection, COMPANY_A, """
                    UPDATE worker
                    SET company_name = '20000000-0000-0000-0000-000000000002'
                    WHERE worker_id = 'worker-a'
                    """);
            assertRlsRejected(connection, COMPANY_A, """
                    UPDATE ticket
                    SET worker_id = 'worker-b'
                    WHERE ticket_id = '10000000-0000-0000-0000-000000000010'
                    """);
            assertRlsRejected(connection, COMPANY_A, """
                    INSERT INTO task (task_id, company_name, task_type, status, assignee_id)
                    VALUES (
                        'cross-tenant-task',
                        '10000000-0000-0000-0000-000000000001',
                        'TEST', 'DRAFT', 'user-b'
                    )
                    """);
            assertRlsRejected(connection, COMPANY_A, """
                    INSERT INTO task (
                        task_id, company_name, task_type, status, completed_by
                    ) VALUES (
                        'cross-completed-task',
                        '10000000-0000-0000-0000-000000000001',
                        'TEST', 'DRAFT', 'user-b'
                    )
                    """);
            assertRlsRejected(connection, COMPANY_A, """
                    INSERT INTO ticket (
                        ticket_id, task_id, worker_id, status
                    ) VALUES (
                        '10000000-0000-0000-0000-000000000013',
                        'task-a', 'worker-b', 'OPEN'
                    )
                    """);
            assertRlsRejected(connection, COMPANY_A, """
                    INSERT INTO ticket (
                        ticket_id, task_id, assignee_id, status
                    ) VALUES (
                        '10000000-0000-0000-0000-000000000023',
                        'task-a', 'user-b', 'OPEN'
                    )
                    """);
            assertRlsRejected(connection, COMPANY_A, """
                    INSERT INTO ticket (
                        ticket_id, task_id, status, link_issued_by
                    ) VALUES (
                        '10000000-0000-0000-0000-000000000024',
                        'task-a', 'OPEN', 'user-b'
                    )
                    """);
            assertRlsRejected(connection, COMPANY_A, """
                    INSERT INTO document (
                        document_id, worker_id, task_id, doc_type, status
                    ) VALUES (
                        '10000000-0000-0000-0000-000000000014',
                        'worker-a', 'task-b', 'TEST', 'MIXED'
                    )
                    """);
            assertRlsRejected(connection, COMPANY_A, """
                    INSERT INTO document (
                        document_id, worker_id, doc_type, status, verified_by
                    ) VALUES (
                        '10000000-0000-0000-0000-000000000025',
                        'worker-a', 'TEST', 'MIXED', 'user-b'
                    )
                    """);
            assertRlsRejected(connection, COMPANY_A, """
                    INSERT INTO document (
                        document_id, ticket_id, doc_type, status
                    ) VALUES (
                        '10000000-0000-0000-0000-000000000026',
                        '20000000-0000-0000-0000-000000000020',
                        'TEST', 'MIXED'
                    )
                    """);
        } finally {
            if (!connection.getAutoCommit()) {
                connection.rollback();
                connection.setAutoCommit(true);
            }
            execute(connection, "RESET ROLE");
            execute(connection, "DROP OWNED BY " + quotedRole);
            execute(connection, "DROP ROLE " + quotedRole);
        }
    }

    private void assertTenantVisibleRows(Connection connection, String companyName) throws SQLException {
        boolean tenantA = COMPANY_A.equals(companyName);
        String suffix = tenantA ? "a" : "b";
        String ticketId = tenantA
                ? "10000000-0000-0000-0000-000000000010"
                : "20000000-0000-0000-0000-000000000020";
        String documentId = tenantA
                ? "10000000-0000-0000-0000-000000000011"
                : "20000000-0000-0000-0000-000000000021";
        String ticketOnlyDocumentId = tenantA
                ? "10000000-0000-0000-0000-000000000018"
                : "20000000-0000-0000-0000-000000000028";

        assertThat(queryStrings(connection, "SELECT company_name::text FROM company"))
                .containsExactly(companyName);
        assertThat(queryStrings(connection, "SELECT user_id FROM user_account"))
                .containsExactly("user-" + suffix);
        assertThat(queryStrings(connection, "SELECT worker_id FROM worker"))
                .containsExactly("worker-" + suffix);
        assertThat(queryStrings(connection, "SELECT task_id FROM task"))
                .containsExactly("task-" + suffix);
        assertThat(queryStrings(connection, "SELECT ticket_id::text FROM ticket"))
                .containsExactly(ticketId);
        assertThat(queryStrings(connection, "SELECT document_id::text FROM document"))
                .containsExactlyInAnyOrder(documentId, ticketOnlyDocumentId);
    }

    private void assertNoTenantContextLeaks(Connection connection) throws SQLException {
        for (String table : TABLES) {
            assertThat(queryInt(connection, "SELECT count(*) FROM " + table))
                    .as("rows visible without transaction-local tenant context in " + table)
                    .isZero();
        }
        connection.commit();

        assertRlsRejected(connection, null, """
                INSERT INTO company (company_name, name)
                VALUES ('40000000-0000-0000-0000-000000000004', 'no-context')
                """);
    }

    private void assertRlsRejected(Connection connection, String companyName, String sql) throws SQLException {
        if (companyName != null) {
            setTenantContext(connection, companyName);
        }
        try {
            executeUpdate(connection, sql);
            throw new AssertionError("Expected PostgreSQL row-level security to reject the statement");
        } catch (SQLException exception) {
            assertThat(exception.getSQLState()).isEqualTo("42501");
        } finally {
            connection.rollback();
        }
    }

    private void assertRestrictedRole(Connection connection, String role) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT rolsuper, rolcreaterole, rolcreatedb, rolreplication, rolbypassrls
                FROM pg_roles
                WHERE rolname = ?
                """)) {
            statement.setString(1, role);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getBoolean("rolsuper")).isFalse();
                assertThat(resultSet.getBoolean("rolcreaterole")).isFalse();
                assertThat(resultSet.getBoolean("rolcreatedb")).isFalse();
                assertThat(resultSet.getBoolean("rolreplication")).isFalse();
                assertThat(resultSet.getBoolean("rolbypassrls")).isFalse();
            }
        }
    }

    private void assertTestAdministrator(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("""
                     SELECT rolsuper, rolcreaterole, rolbypassrls
                     FROM pg_roles
                     WHERE rolname = current_user
                     """)) {
            assertThat(resultSet.next()).isTrue();
            boolean superuser = resultSet.getBoolean("rolsuper");
            boolean createRole = resultSet.getBoolean("rolcreaterole");
            boolean bypassRls = resultSet.getBoolean("rolbypassrls");
            assertThat(superuser || bypassRls)
                    .as("PostgreSQL integration tests require a disposable test administrator that can bypass FORCE RLS")
                    .isTrue();
            assertThat(superuser || createRole)
                    .as("PostgreSQL integration tests create a temporary restricted role")
                    .isTrue();
        }
    }

    private void assertConstraintNames(Connection connection, String type, Set<String> expected)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT conname
                FROM pg_constraint
                JOIN pg_namespace ON pg_namespace.oid = pg_constraint.connamespace
                WHERE pg_namespace.nspname = 'public'
                  AND pg_constraint.contype = ?
                  AND pg_constraint.conrelid <> 0
                  AND pg_constraint.conrelid <> 'public.flyway_schema_history'::regclass
                """)) {
            statement.setString(1, type);
            try (ResultSet resultSet = statement.executeQuery()) {
                Set<String> actual = new LinkedHashSet<>();
                while (resultSet.next()) {
                    actual.add(resultSet.getString("conname"));
                }
                assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
            }
        }
    }

    private void assertIndexes(Connection connection) throws SQLException {
        Map<String, String> expected = Map.ofEntries(
                Map.entry("idx_user_account_company_name", "user_account(company_name)"),
                Map.entry("idx_worker_company_name", "worker(company_name)"),
                Map.entry("idx_task_company_name", "task(company_name)"),
                Map.entry("idx_task_assignee_id", "task(assignee_id)"),
                Map.entry("idx_task_completed_by", "task(completed_by)"),
                Map.entry("idx_ticket_task_id", "ticket(task_id)"),
                Map.entry("idx_ticket_worker_id", "ticket(worker_id)"),
                Map.entry("idx_ticket_assignee_id", "ticket(assignee_id)"),
                Map.entry("idx_ticket_link_issued_by", "ticket(link_issued_by)"),
                Map.entry("idx_document_worker_id", "document(worker_id)"),
                Map.entry("idx_document_task_id", "document(task_id)"),
                Map.entry("idx_document_ticket_id", "document(ticket_id)"),
                Map.entry("idx_document_verified_by", "document(verified_by)")
        );
        Map<String, String> actual = new LinkedHashMap<>();

        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("""
                     SELECT
                         index_table.relname AS index_name,
                         source_table.relname AS table_name,
                         string_agg(source_attribute.attname, ',' ORDER BY index_key.position) AS columns
                     FROM pg_index AS index_record
                     JOIN pg_class AS index_table
                       ON index_table.oid = index_record.indexrelid
                     JOIN pg_namespace AS index_namespace
                       ON index_namespace.oid = index_table.relnamespace
                     JOIN pg_class AS source_table
                       ON source_table.oid = index_record.indrelid
                     JOIN LATERAL unnest(index_record.indkey) WITH ORDINALITY
                       AS index_key(attnum, position) ON index_key.attnum > 0
                     JOIN pg_attribute AS source_attribute
                       ON source_attribute.attrelid = source_table.oid
                      AND source_attribute.attnum = index_key.attnum
                     WHERE index_namespace.nspname = 'public'
                       AND index_table.relname LIKE 'idx_%'
                     GROUP BY index_table.relname, source_table.relname
                     """)) {
            while (resultSet.next()) {
                actual.put(
                        resultSet.getString("index_name"),
                        resultSet.getString("table_name") + "("
                                + resultSet.getString("columns") + ")"
                );
            }
        }
        assertThat(actual).containsExactlyInAnyOrderEntriesOf(expected);
    }

    private void assertForeignKeys(Connection connection) throws SQLException {
        Map<String, String> expected = Map.ofEntries(
                Map.entry("fk_user_account_company", "user_account.company_name->company.company_name"),
                Map.entry("fk_worker_company", "worker.company_name->company.company_name"),
                Map.entry("fk_task_company", "task.company_name->company.company_name"),
                Map.entry("fk_task_assignee", "task.assignee_id->user_account.user_id"),
                Map.entry("fk_task_completed_by", "task.completed_by->user_account.user_id"),
                Map.entry("fk_ticket_task", "ticket.task_id->task.task_id"),
                Map.entry("fk_ticket_worker", "ticket.worker_id->worker.worker_id"),
                Map.entry("fk_ticket_assignee", "ticket.assignee_id->user_account.user_id"),
                Map.entry("fk_ticket_link_issued_by", "ticket.link_issued_by->user_account.user_id"),
                Map.entry("fk_document_worker", "document.worker_id->worker.worker_id"),
                Map.entry("fk_document_task", "document.task_id->task.task_id"),
                Map.entry("fk_document_ticket", "document.ticket_id->ticket.ticket_id"),
                Map.entry("fk_document_verified_by", "document.verified_by->user_account.user_id")
        );
        Map<String, String> actual = new LinkedHashMap<>();

        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("""
                     SELECT
                         constraint_record.conname,
                         source_table.relname AS source_table,
                         source_attribute.attname AS source_column,
                         target_table.relname AS target_table,
                         target_attribute.attname AS target_column,
                         source_attribute.atttypid AS source_type,
                         source_attribute.atttypmod AS source_type_modifier,
                         target_attribute.atttypid AS target_type,
                         target_attribute.atttypmod AS target_type_modifier
                     FROM pg_constraint AS constraint_record
                     JOIN pg_namespace AS constraint_namespace
                       ON constraint_namespace.oid = constraint_record.connamespace
                     JOIN pg_class AS source_table
                       ON source_table.oid = constraint_record.conrelid
                     JOIN pg_class AS target_table
                       ON target_table.oid = constraint_record.confrelid
                     JOIN LATERAL unnest(constraint_record.conkey) WITH ORDINALITY
                       AS source_key(attnum, position) ON TRUE
                     JOIN LATERAL unnest(constraint_record.confkey) WITH ORDINALITY
                       AS target_key(attnum, position)
                       ON target_key.position = source_key.position
                     JOIN pg_attribute AS source_attribute
                       ON source_attribute.attrelid = source_table.oid
                      AND source_attribute.attnum = source_key.attnum
                     JOIN pg_attribute AS target_attribute
                       ON target_attribute.attrelid = target_table.oid
                      AND target_attribute.attnum = target_key.attnum
                     WHERE constraint_namespace.nspname = 'public'
                       AND constraint_record.contype = 'f'
                     """)) {
            while (resultSet.next()) {
                actual.put(
                        resultSet.getString("conname"),
                        resultSet.getString("source_table") + "."
                                + resultSet.getString("source_column") + "->"
                                + resultSet.getString("target_table") + "."
                                + resultSet.getString("target_column")
                );
                assertThat(resultSet.getLong("source_type"))
                        .as(resultSet.getString("conname") + " type OID")
                        .isEqualTo(resultSet.getLong("target_type"));
                assertThat(resultSet.getInt("source_type_modifier"))
                        .as(resultSet.getString("conname") + " type modifier")
                        .isEqualTo(resultSet.getInt("target_type_modifier"));
            }
        }
        assertThat(actual).containsExactlyInAnyOrderEntriesOf(expected);
    }

    private Map<String, ColumnSpec> expectedColumns() {
        Map<String, ColumnSpec> columns = new LinkedHashMap<>();
        addColumns(columns, "company",
                "company_name:uuid:false", "name:text:false");
        addColumns(columns, "user_account",
                "user_id:text:false", "company_name:uuid:false", "role:text:false",
                "id:text:false", "password:text:false", "created_at:timestamptz:false");
        addColumns(columns, "worker",
                "worker_id:text:false", "legal_name_enc:bytea:false", "company_name:uuid:false",
                "employee_no:text:false", "nationality:text:false", "visa_type:text:false",
                "stay_expiry:date:false", "contract_start:date:false", "contract_end:date:false",
                "phone:bytea:true", "email:text:true", "arrival_date:date:false",
                "created_at:timestamptz:false");
        addColumns(columns, "task",
                "task_id:text:false", "company_name:uuid:false", "task_type:text:false",
                "due_date:date:true", "status:text:false", "source_rule:text:true",
                "assignee_id:text:true", "completed_by:text:true", "completed_at:timestamptz:true",
                "created_at:timestamptz:false");
        addColumns(columns, "ticket",
                "ticket_id:uuid:false", "task_id:text:false", "worker_id:text:true",
                "assignee_id:text:true", "status:text:false", "created_at:timestamptz:false",
                "messages_json:jsonb:false", "link_token_hash:bytea:true", "link_status:text:true",
                "link_issued_by:text:true", "link_delivery_channel:text:true",
                "link_expires_at:timestamptz:true", "link_replaces_hash:bytea:true");
        addColumns(columns, "document",
                "document_id:uuid:false", "worker_id:text:true", "task_id:text:true",
                "ticket_id:uuid:true", "doc_type:text:false", "status:text:false",
                "evidence_type:text:true", "due_date:date:true", "created_at:timestamptz:false",
                "file_uri_enc:bytea:true", "value_enc:bytea:true", "verified_by:text:true",
                "verified_at:timestamptz:true");
        return columns;
    }

    private void addColumns(Map<String, ColumnSpec> columns, String table, String... definitions) {
        for (String definition : definitions) {
            String[] parts = definition.split(":");
            columns.put(
                    table + "." + parts[0],
                    new ColumnSpec(parts[1], Boolean.parseBoolean(parts[2]))
            );
        }
    }

    private Set<String> queryStrings(Connection connection, String sql) throws SQLException {
        Set<String> values = new LinkedHashSet<>();
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                values.add(resultSet.getString(1));
            }
        }
        return values;
    }

    private String queryString(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getString(1);
        }
    }

    private int queryInt(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getInt(1);
        }
    }

    private String columnDefault(Connection connection, String table, String column) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT column_default
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = ?
                  AND column_name = ?
                """)) {
            statement.setString(1, table);
            statement.setString(2, column);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getString(1);
            }
        }
    }

    private String columnComment(Connection connection, String table, String column) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT col_description(table_record.oid, attribute_record.attnum)
                FROM pg_class AS table_record
                JOIN pg_namespace AS table_namespace
                  ON table_namespace.oid = table_record.relnamespace
                JOIN pg_attribute AS attribute_record
                  ON attribute_record.attrelid = table_record.oid
                WHERE table_namespace.nspname = 'public'
                  AND table_record.relname = ?
                  AND attribute_record.attname = ?
                """)) {
            statement.setString(1, table);
            statement.setString(2, column);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getString(1);
            }
        }
    }

    private void setTenantContext(Connection connection, String companyName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT set_config('app.company_name', ?, true)"
        )) {
            statement.setString(1, companyName);
            statement.executeQuery();
        }
    }

    private void assertSqlState(Connection connection, String sqlState, String sql) throws SQLException {
        try {
            execute(connection, sql);
            throw new AssertionError("Expected SQLSTATE " + sqlState);
        } catch (SQLException exception) {
            assertThat(exception.getSQLState()).isEqualTo(sqlState);
        }
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private int executeUpdate(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            return statement.executeUpdate(sql);
        }
    }

    private String quoteIdentifier(String identifier) {
        if (!identifier.matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException("Unsafe SQL identifier");
        }
        return "\"" + identifier + "\"";
    }

    private String requiredEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " environment variable is required.");
        }
        return value;
    }

    private record ColumnSpec(String udtName, boolean nullable) {
    }
}
