package com.fowoco.server.common.database;

import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Queue;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class PostgreSqlTimeoutClassifier {

    static final String QUERY_CANCELED_SQL_STATE = "57014";
    static final String LOCK_NOT_AVAILABLE_SQL_STATE = "55P03";
    static final String STATEMENT_TIMEOUT_DIAGNOSTIC =
            "canceling statement due to statement timeout";
    static final String LOCK_TIMEOUT_DIAGNOSTIC =
            "canceling statement due to lock timeout";

    public PostgreSqlTimeoutClassification classify(Throwable failure) {
        if (failure == null) {
            return notDatabaseFailure();
        }

        Queue<Throwable> queue = new ArrayDeque<>();
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        PostgreSqlTimeoutClassification ambiguousQueryCanceled = null;
        PostgreSqlTimeoutClassification ambiguousLockNotAvailable = null;
        PostgreSqlTimeoutClassification otherDatabaseFailure = null;
        queue.add(failure);

        while (!queue.isEmpty()) {
            Throwable candidate = queue.remove();
            if (!visited.add(candidate)) {
                continue;
            }

            if (candidate instanceof SQLException sqlException) {
                PostgreSqlTimeoutClassification classification = classify(sqlException);
                if (isConfirmed(classification.type())) {
                    return classification;
                }
                if (classification.type()
                        == DatabaseTimeoutType.AMBIGUOUS_QUERY_CANCELED
                        && ambiguousQueryCanceled == null) {
                    ambiguousQueryCanceled = classification;
                } else if (classification.type()
                        == DatabaseTimeoutType.AMBIGUOUS_LOCK_NOT_AVAILABLE
                        && ambiguousLockNotAvailable == null) {
                    ambiguousLockNotAvailable = classification;
                } else if (classification.type()
                        == DatabaseTimeoutType.OTHER_DATABASE_FAILURE
                        && otherDatabaseFailure == null) {
                    otherDatabaseFailure = classification;
                }
            }

            if (candidate.getCause() != null) {
                queue.add(candidate.getCause());
            }
            if (candidate instanceof SQLException sqlException
                    && sqlException.getNextException() != null) {
                queue.add(sqlException.getNextException());
            }
        }

        if (ambiguousQueryCanceled != null) {
            return ambiguousQueryCanceled;
        }
        if (ambiguousLockNotAvailable != null) {
            return ambiguousLockNotAvailable;
        }
        if (otherDatabaseFailure != null) {
            return otherDatabaseFailure;
        }
        return notDatabaseFailure();
    }

    private PostgreSqlTimeoutClassification classify(SQLException exception) {
        String sqlState = exception.getSQLState();
        String message = exception.getMessage();
        if (QUERY_CANCELED_SQL_STATE.equals(sqlState)) {
            return classification(
                    message != null && message.contains(STATEMENT_TIMEOUT_DIAGNOSTIC)
                            ? DatabaseTimeoutType.CONFIRMED_STATEMENT_TIMEOUT
                            : DatabaseTimeoutType.AMBIGUOUS_QUERY_CANCELED,
                    exception
            );
        }
        if (LOCK_NOT_AVAILABLE_SQL_STATE.equals(sqlState)) {
            return classification(
                    message != null && message.contains(LOCK_TIMEOUT_DIAGNOSTIC)
                            ? DatabaseTimeoutType.CONFIRMED_LOCK_TIMEOUT
                            : DatabaseTimeoutType.AMBIGUOUS_LOCK_NOT_AVAILABLE,
                    exception
            );
        }
        return classification(DatabaseTimeoutType.OTHER_DATABASE_FAILURE, exception);
    }

    private PostgreSqlTimeoutClassification classification(
            DatabaseTimeoutType type,
            SQLException exception
    ) {
        return new PostgreSqlTimeoutClassification(
                type,
                exception.getSQLState(),
                exception.getClass().getName()
        );
    }

    private boolean isConfirmed(DatabaseTimeoutType type) {
        return type == DatabaseTimeoutType.CONFIRMED_STATEMENT_TIMEOUT
                || type == DatabaseTimeoutType.CONFIRMED_LOCK_TIMEOUT;
    }

    private PostgreSqlTimeoutClassification notDatabaseFailure() {
        return new PostgreSqlTimeoutClassification(
                DatabaseTimeoutType.NOT_DATABASE_FAILURE,
                null,
                null
        );
    }
}
