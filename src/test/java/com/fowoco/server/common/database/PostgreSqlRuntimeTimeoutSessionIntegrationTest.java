package com.fowoco.server.common.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "POSTGRES_TEST_ENABLED", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(30)
class PostgreSqlRuntimeTimeoutSessionIntegrationTest
        extends PostgreSqlRuntimeTimeoutIntegrationSupport {

    @Override
    protected String statementTimeout() {
        return "1234ms";
    }

    @Override
    protected String lockTimeout() {
        return "234ms";
    }

    @Test
    void appliesSessionTimeoutsOnlyToRestrictedRuntimeConnections() throws Exception {
        try (Connection runtimeConnection = runtimeDataSource.getConnection();
             Connection migrationConnection = migrationConnection()) {
            assertThat(setting(runtimeConnection, "statement_timeout"))
                    .isEqualTo("1234ms");
            assertThat(setting(runtimeConnection, "lock_timeout"))
                    .isEqualTo("234ms");
            assertThat(setting(migrationConnection, "statement_timeout"))
                    .isNotEqualTo("1234ms");
            assertThat(setting(migrationConnection, "lock_timeout"))
                    .isNotEqualTo("234ms");
        }

        Integer roleSettings = migrationJdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM pg_catalog.pg_db_role_setting settings
                JOIN pg_catalog.pg_roles role ON role.oid = settings.setrole
                WHERE role.rolname = ?
                """,
                Integer.class,
                runtimeRole
        );
        assertThat(roleSettings).isZero();
    }

    @Test
    void appliesTimeoutsToNewPhysicalConnectionsAfterEviction() throws Exception {
        int firstPid;
        try (Connection connection = runtimeDataSource.getConnection()) {
            firstPid = backendPid(connection);
            assertRuntimeDefaults(connection);
        }

        runtimeDataSource.getHikariPoolMXBean().softEvictConnections();

        int secondPid = awaitDifferentBackend(firstPid);
        assertThat(secondPid).isNotEqualTo(firstPid);
    }

    @Test
    void setLocalChangesReturnToDefaultsAfterCommitAndRollback() throws Exception {
        transactionTemplate.executeWithoutResult(status -> {
            runtimeJdbc.execute("SET LOCAL statement_timeout = '900ms'");
            runtimeJdbc.execute("SET LOCAL lock_timeout = '90ms'");
            assertThat(runtimeJdbc.queryForObject(
                    "SELECT pg_catalog.current_setting('statement_timeout')",
                    String.class
            )).isEqualTo("900ms");
        });
        assertRuntimeDefaults();

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            runtimeJdbc.execute("SET LOCAL statement_timeout = '800ms'");
            runtimeJdbc.execute("SET LOCAL lock_timeout = '80ms'");
            throw new ExpectedRollback();
        })).isInstanceOf(ExpectedRollback.class);
        assertRuntimeDefaults();
    }

    private int awaitDifferentBackend(int previousPid) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        int observedPid = previousPid;
        while (System.nanoTime() < deadline) {
            try (Connection connection = runtimeDataSource.getConnection()) {
                observedPid = backendPid(connection);
                assertRuntimeDefaults(connection);
            }
            if (observedPid != previousPid) {
                return observedPid;
            }
            Thread.sleep(50L);
        }
        return observedPid;
    }

    private void assertRuntimeDefaults() {
        assertThat(runtimeJdbc.queryForObject(
                "SELECT pg_catalog.current_setting('statement_timeout')",
                String.class
        )).isEqualTo("1234ms");
        assertThat(runtimeJdbc.queryForObject(
                "SELECT pg_catalog.current_setting('lock_timeout')",
                String.class
        )).isEqualTo("234ms");
    }

    private void assertRuntimeDefaults(Connection connection) throws Exception {
        assertThat(setting(connection, "statement_timeout")).isEqualTo("1234ms");
        assertThat(setting(connection, "lock_timeout")).isEqualTo("234ms");
    }

    private static final class ExpectedRollback extends RuntimeException {
    }
}
