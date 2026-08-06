package com.fowoco.server.common.database;

import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Queue;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class PostgreSqlAccessDeniedClassifier {

    static final String INSUFFICIENT_PRIVILEGE_SQL_STATE = "42501";

    public PostgreSqlAccessDeniedClassification classify(Throwable failure) {
        if (failure == null) {
            return notAccessDenied();
        }

        Queue<Throwable> queue = new ArrayDeque<>();
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        queue.add(failure);

        while (!queue.isEmpty()) {
            Throwable candidate = queue.remove();
            if (!visited.add(candidate)) {
                continue;
            }

            if (candidate instanceof SQLException sqlException
                    && INSUFFICIENT_PRIVILEGE_SQL_STATE.equals(sqlException.getSQLState())) {
                return new PostgreSqlAccessDeniedClassification(
                        DatabaseAccessDeniedType.CONFIRMED_ACCESS_DENIED,
                        sqlException.getSQLState(),
                        sqlException.getClass().getName()
                );
            }

            if (candidate.getCause() != null) {
                queue.add(candidate.getCause());
            }
            if (candidate instanceof SQLException sqlException
                    && sqlException.getNextException() != null) {
                queue.add(sqlException.getNextException());
            }
        }

        return notAccessDenied();
    }

    private PostgreSqlAccessDeniedClassification notAccessDenied() {
        return new PostgreSqlAccessDeniedClassification(
                DatabaseAccessDeniedType.NOT_ACCESS_DENIED,
                null,
                null
        );
    }
}
