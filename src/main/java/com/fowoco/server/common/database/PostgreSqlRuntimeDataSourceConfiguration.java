package com.fowoco.server.common.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        name = "app.database.tenant-context-mode",
        havingValue = "postgresql"
)
@EnableConfigurationProperties(RuntimeDatabaseTimeoutProperties.class)
public class PostgreSqlRuntimeDataSourceConfiguration {

    private static final String RUNTIME_TIMEOUT_SOURCE =
            "app.database.runtime-timeout.*";
    private static final List<String> UNSUPPORTED_PROPERTIES = List.of(
            "spring.datasource.jndi-name",
            "spring.datasource.hikari.jdbc-url",
            "spring.datasource.hikari.username",
            "spring.datasource.hikari.password",
            "spring.datasource.hikari.driver-class-name",
            "spring.datasource.hikari.data-source-class-name",
            "spring.datasource.hikari.data-source-j-n-d-i",
            "spring.datasource.hikari.connection-init-sql"
    );

    @Bean
    @ConfigurationProperties("spring.datasource.hikari")
    public HikariConfig postgreSqlRuntimeHikariConfig() {
        return new HikariConfig();
    }

    @Bean(name = "dataSource")
    @Primary
    public HikariDataSource dataSource(
            DataSourceProperties dataSourceProperties,
            HikariConfig postgreSqlRuntimeHikariConfig,
            RuntimeDatabaseTimeoutProperties timeoutProperties,
            Environment environment
    ) {
        validateSupportedConfiguration(
                dataSourceProperties,
                postgreSqlRuntimeHikariConfig,
                environment
        );

        String jdbcUrl = dataSourceProperties.determineUrl();
        if (!jdbcUrl.startsWith("jdbc:postgresql:")) {
            throw unsupported(
                    "spring.datasource.url",
                    "a direct jdbc:postgresql: URL"
            );
        }

        postgreSqlRuntimeHikariConfig.setJdbcUrl(jdbcUrl);
        postgreSqlRuntimeHikariConfig.setUsername(
                dataSourceProperties.determineUsername()
        );
        postgreSqlRuntimeHikariConfig.setPassword(
                dataSourceProperties.determinePassword()
        );
        String driverClassName = dataSourceProperties.determineDriverClassName();
        if (StringUtils.hasText(driverClassName)) {
            postgreSqlRuntimeHikariConfig.setDriverClassName(driverClassName);
        }
        postgreSqlRuntimeHikariConfig.setConnectionInitSql(
                connectionInitSql(timeoutProperties)
        );
        postgreSqlRuntimeHikariConfig.validate();
        return new HikariDataSource(postgreSqlRuntimeHikariConfig);
    }

    static String connectionInitSql(RuntimeDatabaseTimeoutProperties properties) {
        return "SELECT "
                + "pg_catalog.set_config('statement_timeout', '"
                + properties.statementTimeoutMillis()
                + "ms', false), "
                + "pg_catalog.set_config('lock_timeout', '"
                + properties.lockTimeoutMillis()
                + "ms', false)";
    }

    private static void validateSupportedConfiguration(
            DataSourceProperties dataSourceProperties,
            HikariConfig hikariConfig,
            Environment environment
    ) {
        for (String property : UNSUPPORTED_PROPERTIES) {
            if (environment.containsProperty(property)) {
                throw unsupported(property, "spring.datasource.* URL-based configuration");
            }
        }
        if (dataSourceProperties.getJndiName() != null) {
            throw unsupported(
                    "spring.datasource.jndi-name",
                    "spring.datasource.* URL-based configuration"
            );
        }

        rejectBoundValue(hikariConfig.getJdbcUrl(), "spring.datasource.hikari.jdbc-url");
        rejectBoundValue(hikariConfig.getUsername(), "spring.datasource.hikari.username");
        rejectBoundValue(hikariConfig.getPassword(), "spring.datasource.hikari.password");
        rejectBoundValue(
                hikariConfig.getDriverClassName(),
                "spring.datasource.hikari.driver-class-name"
        );
        rejectBoundValue(
                hikariConfig.getDataSourceClassName(),
                "spring.datasource.hikari.data-source-class-name"
        );
        rejectBoundValue(
                hikariConfig.getDataSourceJNDI(),
                "spring.datasource.hikari.data-source-j-n-d-i"
        );
        rejectBoundValue(
                hikariConfig.getConnectionInitSql(),
                "spring.datasource.hikari.connection-init-sql"
        );

        Class<? extends DataSource> type = dataSourceProperties.getType();
        if (type != null && !HikariDataSource.class.isAssignableFrom(type)) {
            throw unsupported("spring.datasource.type", HikariDataSource.class.getName());
        }

        String connectionFetch = environment.getProperty(
                "spring.datasource.connection-fetch"
        );
        if ("lazy".equalsIgnoreCase(connectionFetch)) {
            throw unsupported(
                    "spring.datasource.connection-fetch",
                    "eager Runtime Hikari connection creation"
            );
        }
    }

    private static void rejectBoundValue(String value, String property) {
        if (value != null) {
            throw unsupported(property, "spring.datasource.* URL-based configuration");
        }
    }

    private static IllegalStateException unsupported(
            String property,
            String supportedPath
    ) {
        return new IllegalStateException(
                "Unsupported PostgreSQL Runtime DataSource property '"
                        + property
                        + "'. Use "
                        + supportedPath
                        + "; Runtime timeout values must come only from "
                        + RUNTIME_TIMEOUT_SOURCE
        );
    }
}
