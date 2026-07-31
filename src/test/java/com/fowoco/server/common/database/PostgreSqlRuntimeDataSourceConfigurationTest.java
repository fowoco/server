package com.fowoco.server.common.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.lang.reflect.Method;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.env.MockEnvironment;

class PostgreSqlRuntimeDataSourceConfigurationTest {

    private final PostgreSqlRuntimeDataSourceConfiguration configuration =
            new PostgreSqlRuntimeDataSourceConfiguration();

    @Test
    void createsPrimaryRuntimeHikariDataSourceWithValidatedInitSql() throws Exception {
        DataSourceProperties dataSourceProperties = postgresProperties();
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setInitializationFailTimeout(-1);
        hikariConfig.setMaximumPoolSize(7);
        RuntimeDatabaseTimeoutProperties timeoutProperties = timeoutProperties();

        try (HikariDataSource dataSource = configuration.dataSource(
                dataSourceProperties,
                hikariConfig,
                timeoutProperties,
                new MockEnvironment()
        )) {
            assertThat(dataSource.getJdbcUrl())
                    .isEqualTo("jdbc:postgresql://localhost:5432/fowoco_config_test");
            assertThat(dataSource.getUsername()).isEqualTo("runtime_user");
            assertThat(dataSource.getMaximumPoolSize()).isEqualTo(7);
            assertThat(dataSource.getConnectionInitSql()).isEqualTo(
                    "SELECT pg_catalog.set_config('statement_timeout', '30000ms', false), "
                            + "pg_catalog.set_config('lock_timeout', '3000ms', false)"
            );
        }

        Method dataSourceMethod = PostgreSqlRuntimeDataSourceConfiguration.class
                .getDeclaredMethod(
                        "dataSource",
                        DataSourceProperties.class,
                        HikariConfig.class,
                        RuntimeDatabaseTimeoutProperties.class,
                        org.springframework.core.env.Environment.class
                );
        assertThat(dataSourceMethod.isAnnotationPresent(Primary.class)).isTrue();
    }

    @Test
    void customConfigurationIsInactiveInTransactionOnlyMode() {
        new ApplicationContextRunner()
                .withUserConfiguration(PostgreSqlRuntimeDataSourceConfiguration.class)
                .withPropertyValues("app.database.tenant-context-mode=transaction-only")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(
                            PostgreSqlRuntimeDataSourceConfiguration.class
                    );
                    assertThat(context).doesNotHaveBean("dataSource");
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "spring.datasource.jndi-name",
            "spring.datasource.hikari.jdbc-url",
            "spring.datasource.hikari.username",
            "spring.datasource.hikari.password",
            "spring.datasource.hikari.driver-class-name",
            "spring.datasource.hikari.data-source-class-name",
            "spring.datasource.hikari.data-source-j-n-d-i",
            "spring.datasource.hikari.connection-init-sql"
    })
    void rejectsConflictingDataSourcePropertiesWithoutPrintingValues(String property) {
        String sensitiveValue = "credential-or-secret-value";
        MockEnvironment environment = new MockEnvironment()
                .withProperty(property, sensitiveValue);

        assertThatThrownBy(() -> configuration.dataSource(
                postgresProperties(),
                nonConnectingHikariConfig(),
                timeoutProperties(),
                environment
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(property)
                .hasMessageNotContaining(sensitiveValue)
                .hasMessageContaining("app.database.runtime-timeout.*");
    }

    @Test
    void rejectsLazyFetchNonHikariTypeAndWrappedUrl() {
        MockEnvironment lazy = new MockEnvironment()
                .withProperty("spring.datasource.connection-fetch", "lazy");
        assertThatThrownBy(() -> configuration.dataSource(
                postgresProperties(),
                nonConnectingHikariConfig(),
                timeoutProperties(),
                lazy
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("connection-fetch");

        DataSourceProperties nonHikari = postgresProperties();
        nonHikari.setType(DataSource.class);
        MockEnvironment explicitType = new MockEnvironment()
                .withProperty("spring.datasource.type", DataSource.class.getName());
        assertThatThrownBy(() -> configuration.dataSource(
                nonHikari,
                nonConnectingHikariConfig(),
                timeoutProperties(),
                explicitType
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.datasource.type");

        DataSourceProperties wrapped = postgresProperties();
        wrapped.setUrl("jdbc:p6spy:postgresql://localhost:5432/fowoco_config_test");
        assertThatThrownBy(() -> configuration.dataSource(
                wrapped,
                nonConnectingHikariConfig(),
                timeoutProperties(),
                new MockEnvironment()
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jdbc:postgresql:");
    }

    @Test
    void rejectsBoundHikariConnectionSourcesEvenWithRelaxedPropertyNames() {
        HikariConfig hikariConfig = nonConnectingHikariConfig();
        hikariConfig.setJdbcUrl("jdbc:postgresql://other-host/other-db");

        assertThatThrownBy(() -> configuration.dataSource(
                postgresProperties(),
                hikariConfig,
                timeoutProperties(),
                new MockEnvironment()
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.datasource.hikari.jdbc-url")
                .hasMessageNotContaining("other-host");
    }

    private DataSourceProperties postgresProperties() {
        DataSourceProperties properties = new DataSourceProperties();
        properties.setUrl("jdbc:postgresql://localhost:5432/fowoco_config_test");
        properties.setUsername("runtime_user");
        properties.setPassword("runtime_password");
        properties.setDriverClassName("org.postgresql.Driver");
        return properties;
    }

    private HikariConfig nonConnectingHikariConfig() {
        HikariConfig config = new HikariConfig();
        config.setInitializationFailTimeout(-1);
        return config;
    }

    private RuntimeDatabaseTimeoutProperties timeoutProperties() {
        RuntimeDatabaseTimeoutProperties properties =
                new RuntimeDatabaseTimeoutProperties();
        properties.afterPropertiesSet();
        return properties;
    }
}
