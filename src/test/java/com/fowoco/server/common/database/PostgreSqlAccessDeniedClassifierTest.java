package com.fowoco.server.common.database;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.PersistenceException;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

class PostgreSqlAccessDeniedClassifierTest {

    private final PostgreSqlAccessDeniedClassifier classifier =
            new PostgreSqlAccessDeniedClassifier();

    @Test
    void confirmsDirectInsufficientPrivilegeSqlState() {
        assertClassification(
                sql("permission denied", "42501"),
                DatabaseAccessDeniedType.CONFIRMED_ACCESS_DENIED,
                "42501"
        );
    }

    @Test
    void searchesSpringAndJpaWrapperCauseChains() {
        Throwable wrapped = new DataAccessResourceFailureException(
                "spring data failure",
                new PersistenceException(
                        "jpa failure",
                        new RuntimeException(sql("row-level security violation", "42501"))
                )
        );

        assertClassification(
                wrapped,
                DatabaseAccessDeniedType.CONFIRMED_ACCESS_DENIED,
                "42501"
        );
    }

    @Test
    void searchesSqlNextExceptionChain() {
        SQLException root = sql("batch failed", "42601");
        root.setNextException(sql("permission denied", "42501"));

        assertClassification(
                root,
                DatabaseAccessDeniedType.CONFIRMED_ACCESS_DENIED,
                "42501"
        );
    }

    @Test
    void doesNotClassifyOtherSqlStatesOrNonDatabaseFailures() {
        assertClassification(
                sql("syntax error", "42601"),
                DatabaseAccessDeniedType.NOT_ACCESS_DENIED,
                null
        );
        assertClassification(
                new IllegalStateException("not a database failure"),
                DatabaseAccessDeniedType.NOT_ACCESS_DENIED,
                null
        );
        assertClassification(
                null,
                DatabaseAccessDeniedType.NOT_ACCESS_DENIED,
                null
        );
    }

    @Test
    void doesNotClassifyOptimisticLockWithoutInsufficientPrivilegeSqlState() {
        ObjectOptimisticLockingFailureException failure =
                new ObjectOptimisticLockingFailureException(Object.class, 1L);

        assertClassification(
                failure,
                DatabaseAccessDeniedType.NOT_ACCESS_DENIED,
                null
        );
    }

    @Test
    void identityVisitedSetStopsCauseAndNextExceptionCycles() {
        SQLException first = sql("first", "42601");
        SQLException second = sql("second", "22023");
        first.setNextException(second);
        second.setNextException(first);

        assertClassification(
                first,
                DatabaseAccessDeniedType.NOT_ACCESS_DENIED,
                null
        );
    }

    @Test
    void ignoresSuppressedSqlExceptions() {
        RuntimeException failure = new RuntimeException("outer");
        failure.addSuppressed(sql("permission denied", "42501"));

        assertClassification(
                failure,
                DatabaseAccessDeniedType.NOT_ACCESS_DENIED,
                null
        );
    }

    private void assertClassification(
            Throwable failure,
            DatabaseAccessDeniedType expectedType,
            String expectedSqlState
    ) {
        PostgreSqlAccessDeniedClassification result = classifier.classify(failure);

        assertThat(result.type()).isEqualTo(expectedType);
        assertThat(result.confirmed())
                .isEqualTo(expectedType == DatabaseAccessDeniedType.CONFIRMED_ACCESS_DENIED);
        assertThat(result.sqlState()).isEqualTo(expectedSqlState);
        if (expectedSqlState == null) {
            assertThat(result.exceptionType()).isNull();
        } else {
            assertThat(result.exceptionType()).isEqualTo(SQLException.class.getName());
        }
    }

    private SQLException sql(String message, String sqlState) {
        return new SQLException(message, sqlState);
    }
}
