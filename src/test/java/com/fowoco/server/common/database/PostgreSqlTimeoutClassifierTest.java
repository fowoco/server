package com.fowoco.server.common.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;

class PostgreSqlTimeoutClassifierTest {

    private final PostgreSqlTimeoutClassifier classifier =
            new PostgreSqlTimeoutClassifier();

    @Test
    void confirmsOnlyCanonicalStatementAndLockTimeoutDiagnostics() {
        assertClassification(
                sql("ERROR: canceling statement due to statement timeout", "57014"),
                DatabaseTimeoutType.CONFIRMED_STATEMENT_TIMEOUT,
                "57014"
        );
        assertClassification(
                sql("ERROR: canceling statement due to lock timeout", "55P03"),
                DatabaseTimeoutType.CONFIRMED_LOCK_TIMEOUT,
                "55P03"
        );
    }

    @Test
    void leavesGenericCancellationAndNowaitFailureAmbiguous() {
        assertClassification(
                sql("canceling statement due to user request", "57014"),
                DatabaseTimeoutType.AMBIGUOUS_QUERY_CANCELED,
                "57014"
        );
        assertClassification(
                sql("could not obtain lock on row in relation", "55P03"),
                DatabaseTimeoutType.AMBIGUOUS_LOCK_NOT_AVAILABLE,
                "55P03"
        );
        assertClassification(
                sql("현지화된 취소 메시지", "57014"),
                DatabaseTimeoutType.AMBIGUOUS_QUERY_CANCELED,
                "57014"
        );
    }

    @Test
    void doesNotMisclassifyPermissionBootstrapOrGeneralSqlFailures() {
        assertClassification(
                sql("permission denied", "42501"),
                DatabaseTimeoutType.OTHER_DATABASE_FAILURE,
                "42501"
        );
        assertClassification(
                sql("invalid bootstrap argument", "22023"),
                DatabaseTimeoutType.OTHER_DATABASE_FAILURE,
                "22023"
        );
        assertClassification(
                sql("syntax error", "42601"),
                DatabaseTimeoutType.OTHER_DATABASE_FAILURE,
                "42601"
        );
        assertClassification(
                new IllegalStateException("not a database failure"),
                DatabaseTimeoutType.NOT_DATABASE_FAILURE,
                null
        );
    }

    @Test
    void searchesCauseBeforeNextExceptionAndReturnsFirstConfirmedTimeout() {
        SQLException root = sql("root", "42601");
        SQLException next = sql(
                "canceling statement due to lock timeout",
                "55P03"
        );
        SQLException cause = sql(
                "canceling statement due to statement timeout",
                "57014"
        );
        root.initCause(cause);
        root.setNextException(next);

        PostgreSqlTimeoutClassification result = classifier.classify(root);

        assertThat(result.type())
                .isEqualTo(DatabaseTimeoutType.CONFIRMED_STATEMENT_TIMEOUT);
        assertThat(result.sqlState()).isEqualTo("57014");
        assertThat(result.exceptionType()).isEqualTo(SQLException.class.getName());
    }

    @Test
    void searchesWrappedAndNextExceptionsAndIgnoresSuppressedExceptions() {
        SQLException root = sql("root", "42601");
        SQLException next = sql(
                "canceling statement due to lock timeout",
                "55P03"
        );
        root.setNextException(next);
        RuntimeException wrapper = new RuntimeException(new RuntimeException(root));

        assertClassification(
                wrapper,
                DatabaseTimeoutType.CONFIRMED_LOCK_TIMEOUT,
                "55P03"
        );

        RuntimeException onlySuppressed = new RuntimeException("outer");
        onlySuppressed.addSuppressed(sql(
                "canceling statement due to statement timeout",
                "57014"
        ));
        assertClassification(
                onlySuppressed,
                DatabaseTimeoutType.NOT_DATABASE_FAILURE,
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
                DatabaseTimeoutType.OTHER_DATABASE_FAILURE,
                "42601"
        );
    }

    private void assertClassification(
            Throwable failure,
            DatabaseTimeoutType expectedType,
            String expectedSqlState
    ) {
        PostgreSqlTimeoutClassification result = classifier.classify(failure);

        assertThat(result.type()).isEqualTo(expectedType);
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
