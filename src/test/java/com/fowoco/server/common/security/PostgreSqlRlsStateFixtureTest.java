package com.fowoco.server.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "POSTGRES_TEST_ENABLED", matches = "true")
class PostgreSqlRlsStateFixtureTest {

    private static final String TABLE = "worker";

    @Test
    void restoresEnabledAndForcedFlagsToTheirExactInitialValues() throws Exception {
        String url = requiredEnvironmentVariable("POSTGRES_TEST_URL");
        String username = requiredEnvironmentVariable("POSTGRES_TEST_USERNAME");
        String password = requiredEnvironmentVariable("POSTGRES_TEST_PASSWORD");

        try (PostgreSqlRlsTestLock ignored = PostgreSqlRlsTestLock.acquire(
                url,
                username,
                password
        )) {
            Flyway.configure()
                    .dataSource(url, username, password)
                    .locations(
                            "classpath:db/migration",
                            "classpath:db/migration-postgresql"
                    )
                    .load()
                    .migrate();

            try (Connection connection = DriverManager.getConnection(url, username, password)) {
                PostgreSqlRlsStateFixture fixture = PostgreSqlRlsStateFixture.capture(
                        connection,
                        List.of(TABLE)
                );
                PostgreSqlRlsStateFixture.TableState initial = fixture.originalState(TABLE);

                try (fixture) {
                    setEnabled(connection, !initial.enabled());
                    setForced(connection, !initial.forced());

                    assertThat(PostgreSqlRlsStateFixture.readState(connection, TABLE))
                            .isEqualTo(new PostgreSqlRlsStateFixture.TableState(
                                    !initial.enabled(),
                                    !initial.forced()
                            ));
                }

                assertThat(PostgreSqlRlsStateFixture.readState(connection, TABLE))
                        .isEqualTo(initial);
                fixture.close();
            }
        }
    }

    private void setEnabled(Connection connection, boolean enabled) throws Exception {
        execute(connection, "ALTER TABLE public.worker "
                + (enabled ? "ENABLE" : "DISABLE")
                + " ROW LEVEL SECURITY");
    }

    private void setForced(Connection connection, boolean forced) throws Exception {
        execute(connection, "ALTER TABLE public.worker "
                + (forced ? "FORCE" : "NO FORCE")
                + " ROW LEVEL SECURITY");
    }

    private void execute(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static String requiredEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " environment variable is required.");
        }
        return value;
    }
}
