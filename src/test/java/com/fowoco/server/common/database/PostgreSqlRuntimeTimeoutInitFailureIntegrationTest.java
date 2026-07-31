package com.fowoco.server.common.database;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "POSTGRES_TEST_ENABLED", matches = "true")
@Timeout(20)
class PostgreSqlRuntimeTimeoutInitFailureIntegrationTest {

    @Test
    void invalidInitSqlNeverProvidesAUsablePoolConnection() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(required("POSTGRES_TEST_URL"));
        config.setUsername(required("POSTGRES_TEST_USERNAME"));
        config.setPassword(required("POSTGRES_TEST_PASSWORD"));
        config.setDriverClassName("org.postgresql.Driver");
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(0);
        config.setInitializationFailTimeout(5_000L);
        config.setConnectionInitSql(
                "SELECT definitely_missing_runtime_timeout_function()"
        );

        assertThatThrownBy(() -> {
            try (HikariDataSource dataSource = new HikariDataSource(config)) {
                dataSource.getConnection().close();
            }
        }).isInstanceOf(Exception.class);
    }

    private String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " environment variable is required.");
        }
        return value;
    }
}
