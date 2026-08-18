package com.fowoco.server.common.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "POSTGRES_TEST_ENABLED", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(45)
class PostgreSqlRuntimeTimeoutBehaviorIntegrationTest
        extends PostgreSqlRuntimeTimeoutIntegrationSupport {

    private final PostgreSqlTimeoutClassifier classifier =
            new PostgreSqlTimeoutClassifier();

    @Override
    protected String statementTimeout() {
        return "300ms";
    }

    @Override
    protected String lockTimeout() {
        return "100ms";
    }

    @AfterEach
    void resetFixture() {
        restoreFixtureName();
    }

    @Test
    void statementTimeoutRollsBackTransactionAndPoolServesNextQuery() {
        Throwable failure = catchThrowable(() -> transactionTemplate.executeWithoutResult(
                status -> {
                    tenantDatabaseContext.setCompanyIdForCurrentTransaction(
                            FIXTURE_COMPANY_ID
                    );
                    assertThat(runtimeJdbc.update(
                            "UPDATE company SET name = ? WHERE company_id = ?",
                            "must roll back",
                            FIXTURE_COMPANY_ID
                    )).isEqualTo(1);
                    runtimeJdbc.execute("SELECT pg_catalog.pg_sleep(1.0)");
                }
        ));

        PostgreSqlTimeoutClassification classification = classifier.classify(failure);
        assertThat(classification.type())
                .isEqualTo(DatabaseTimeoutType.CONFIRMED_STATEMENT_TIMEOUT);
        assertThat(classification.sqlState()).isEqualTo("57014");
        assertThat(migrationJdbc.queryForObject(
                "SELECT name FROM company WHERE company_id = ?",
                String.class,
                FIXTURE_COMPANY_ID
        )).isEqualTo(ORIGINAL_COMPANY_NAME);
        assertNormalRuntimeQuery();
    }

    @Test
    void lockTimeoutDoesNotAffectLockOwnerAndPoolRecovers() throws Exception {
        try (Connection lockOwner = migrationConnection()) {
            lockOwner.setAutoCommit(false);
            try (PreparedStatement statement = lockOwner.prepareStatement(
                    "SELECT company_id FROM company WHERE company_id = ? FOR UPDATE"
            )) {
                statement.setObject(1, FIXTURE_COMPANY_ID);
                statement.executeQuery().close();

                for (int attempt = 0; attempt < 3; attempt++) {
                    Throwable failure = catchThrowable(() ->
                            transactionTemplate.executeWithoutResult(status -> {
                                tenantDatabaseContext.setCompanyIdForCurrentTransaction(
                                        FIXTURE_COMPANY_ID
                                );
                                runtimeJdbc.update(
                                        "UPDATE company SET name = ? "
                                                + "WHERE company_id = ?",
                                        "blocked update",
                                        FIXTURE_COMPANY_ID
                                );
                            })
                    );
                    PostgreSqlTimeoutClassification classification =
                            classifier.classify(failure);
                    assertThat(classification.type())
                            .isEqualTo(DatabaseTimeoutType.CONFIRMED_LOCK_TIMEOUT);
                    assertThat(classification.sqlState()).isEqualTo("55P03");
                    assertThat(runtimeJdbc.queryForObject("SELECT 1", Integer.class))
                            .isEqualTo(1);
                }
                assertThat(lockOwner.isClosed()).isFalse();
            } finally {
                lockOwner.rollback();
            }
        }

        transactionTemplate.executeWithoutResult(status -> {
            tenantDatabaseContext.setCompanyIdForCurrentTransaction(FIXTURE_COMPANY_ID);
            assertThat(runtimeJdbc.update(
                    "UPDATE company SET name = ? WHERE company_id = ?",
                    "after lock release",
                    FIXTURE_COMPANY_ID
            )).isEqualTo(1);
        });
        assertThat(migrationJdbc.queryForObject(
                "SELECT name FROM company WHERE company_id = ?",
                String.class,
                FIXTURE_COMPANY_ID
        )).isEqualTo("after lock release");
        assertNormalRuntimeQuery();
    }

    @Test
    void repeatedTimeoutsDoNotPreventLaterConnectionCheckout() {
        for (int attempt = 0; attempt < 3; attempt++) {
            Throwable failure = catchThrowable(() ->
                    transactionTemplate.executeWithoutResult(status ->
                            runtimeJdbc.execute("SELECT pg_catalog.pg_sleep(1.0)")
                    )
            );
            assertThat(classifier.classify(failure).type())
                    .isEqualTo(DatabaseTimeoutType.CONFIRMED_STATEMENT_TIMEOUT);
            assertNormalRuntimeQuery();
        }
    }

    private void assertNormalRuntimeQuery() {
        assertThat(runtimeJdbc.queryForObject("SELECT 1", Integer.class)).isEqualTo(1);
        assertThat(runtimeJdbc.queryForObject(
                "SELECT pg_catalog.current_setting('statement_timeout')",
                String.class
        )).isEqualTo("300ms");
        assertThat(runtimeJdbc.queryForObject(
                "SELECT pg_catalog.current_setting('lock_timeout')",
                String.class
        )).isEqualTo("100ms");
    }
}
