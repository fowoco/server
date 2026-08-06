package com.fowoco.server.common.security;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** Captures and restores PostgreSQL RLS flags for public-schema test tables. */
public final class PostgreSqlRlsStateFixture implements AutoCloseable {

    private static final Pattern TABLE_NAME = Pattern.compile("[a-z_][a-z0-9_]*");

    private final Connection connection;
    private final Map<String, TableState> originalStates;
    private boolean restored;

    private PostgreSqlRlsStateFixture(
            Connection connection,
            Map<String, TableState> originalStates
    ) {
        this.connection = connection;
        this.originalStates = originalStates;
    }

    public static PostgreSqlRlsStateFixture capture(
            Connection connection,
            Collection<String> tableNames
    ) throws SQLException {
        Objects.requireNonNull(connection, "connection must not be null");
        Objects.requireNonNull(tableNames, "tableNames must not be null");
        if (tableNames.isEmpty()) {
            throw new IllegalArgumentException("tableNames must not be empty");
        }

        Map<String, TableState> states = new LinkedHashMap<>();
        for (String tableName : tableNames) {
            validateTableName(tableName);
            if (states.put(tableName, readState(connection, tableName)) != null) {
                throw new IllegalArgumentException("duplicate table name: " + tableName);
            }
        }
        return new PostgreSqlRlsStateFixture(
                connection,
                Collections.unmodifiableMap(new LinkedHashMap<>(states))
        );
    }

    public TableState originalState(String tableName) {
        TableState state = originalStates.get(tableName);
        if (state == null) {
            throw new IllegalArgumentException("table was not captured: " + tableName);
        }
        return state;
    }

    public void enableRowLevelSecurity() throws SQLException {
        for (String tableName : originalStates.keySet()) {
            execute("ALTER TABLE public." + quoteIdentifier(tableName)
                    + " ENABLE ROW LEVEL SECURITY");
        }
    }

    public void disableRowLevelSecurityForFixtureSetup() throws SQLException {
        for (String tableName : originalStates.keySet()) {
            String qualifiedTable = "public." + quoteIdentifier(tableName);
            execute("ALTER TABLE " + qualifiedTable + " DISABLE ROW LEVEL SECURITY");
            execute("ALTER TABLE " + qualifiedTable + " NO FORCE ROW LEVEL SECURITY");
        }
    }

    public static TableState readState(Connection connection, String tableName)
            throws SQLException {
        Objects.requireNonNull(connection, "connection must not be null");
        validateTableName(tableName);
        try (PreparedStatement statement = connection.prepareStatement(
                """
                SELECT relation.relrowsecurity, relation.relforcerowsecurity
                FROM pg_catalog.pg_class AS relation
                JOIN pg_catalog.pg_namespace AS namespace
                  ON namespace.oid = relation.relnamespace
                WHERE namespace.nspname = 'public'
                  AND relation.relname = ?
                  AND relation.relkind IN ('r', 'p')
                """
        )) {
            statement.setString(1, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException(
                            "PostgreSQL RLS test table does not exist: public." + tableName
                    );
                }
                return new TableState(resultSet.getBoolean(1), resultSet.getBoolean(2));
            }
        }
    }

    public void restore() throws SQLException {
        if (restored) {
            return;
        }

        SQLException failure = null;
        for (Map.Entry<String, TableState> entry : originalStates.entrySet()) {
            String qualifiedTable = "public." + quoteIdentifier(entry.getKey());
            TableState state = entry.getValue();
            failure = executeForCleanup(
                    qualifiedTable + (state.enabled()
                            ? " ENABLE ROW LEVEL SECURITY"
                            : " DISABLE ROW LEVEL SECURITY"),
                    failure
            );
            failure = executeForCleanup(
                    qualifiedTable + (state.forced()
                            ? " FORCE ROW LEVEL SECURITY"
                            : " NO FORCE ROW LEVEL SECURITY"),
                    failure
            );
        }

        if (failure != null) {
            throw failure;
        }
        restored = true;
    }

    private SQLException executeForCleanup(String alterClause, SQLException failure) {
        try {
            execute("ALTER TABLE " + alterClause);
        } catch (SQLException exception) {
            if (failure == null) {
                return exception;
            }
            failure.addSuppressed(exception);
        }
        return failure;
    }

    private void execute(String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static void validateTableName(String tableName) {
        Objects.requireNonNull(tableName, "tableName must not be null");
        if (!TABLE_NAME.matcher(tableName).matches()) {
            throw new IllegalArgumentException("invalid public table name: " + tableName);
        }
    }

    private static String quoteIdentifier(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    @Override
    public void close() throws SQLException {
        restore();
    }

    public record TableState(boolean enabled, boolean forced) {
    }
}
