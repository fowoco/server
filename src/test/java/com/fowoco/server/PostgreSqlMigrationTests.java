package com.fowoco.server;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "POSTGRES_TEST_ENABLED", matches = "true")
class PostgreSqlMigrationTests {

    @Test
    void migrationsApplyToPostgreSql() {
        Flyway flyway = Flyway.configure()
                .dataSource(
                        requiredEnvironmentVariable("POSTGRES_TEST_URL"),
                        requiredEnvironmentVariable("POSTGRES_TEST_USERNAME"),
                        requiredEnvironmentVariable("POSTGRES_TEST_PASSWORD")
                )
                .locations("classpath:db/migration")
                .load();

        flyway.migrate();

        assertThat(flyway.info().current()).isNotNull();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("1");
    }

    private String requiredEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " environment variable is required.");
        }
        return value;
    }
}
