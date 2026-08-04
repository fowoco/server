package com.fowoco.server.common.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class RuntimeDatabaseTimeoutPropertiesTest {

    @Test
    void defaultsAreThirtySecondsAndThreeSeconds() {
        RuntimeDatabaseTimeoutProperties properties =
                new RuntimeDatabaseTimeoutProperties();

        properties.afterPropertiesSet();

        assertThat(properties.statementTimeoutMillis()).isEqualTo(30_000L);
        assertThat(properties.lockTimeoutMillis()).isEqualTo(3_000L);
    }

    @Test
    void bindsSpringDurationFormatsAndNormalizesToMilliseconds() {
        assertTimeouts("30000", "1000us", 30_000L, 1L);
        assertTimeouts("PT30S", "3000ms", 30_000L, 3_000L);
    }

    @Test
    void rejectsSubMillisecondZeroNegativeAndInvalidOrdering() {
        assertInvalid(
                Duration.ofSeconds(30),
                Duration.ofNanos(1_500_000),
                "whole milliseconds"
        );
        assertInvalid(Duration.ZERO, Duration.ofMillis(1), "positive");
        assertInvalid(Duration.ofSeconds(30), Duration.ofMillis(-1), "positive");
        assertInvalid(Duration.ofSeconds(3), Duration.ofSeconds(3), "shorter");
        assertInvalid(Duration.ofSeconds(3), Duration.ofSeconds(4), "shorter");
    }

    @Test
    void acceptsMaximumAndRejectsValuesAbovePostgreSqlIntegerBoundary() {
        RuntimeDatabaseTimeoutProperties maximum = properties(
                RuntimeDatabaseTimeoutProperties.MAX_POSTGRESQL_TIMEOUT,
                Duration.ofMillis(Integer.MAX_VALUE - 1L)
        );
        maximum.afterPropertiesSet();

        assertThat(maximum.statementTimeoutMillis()).isEqualTo(Integer.MAX_VALUE);
        assertThatThrownBy(() -> properties(
                RuntimeDatabaseTimeoutProperties.MAX_POSTGRESQL_TIMEOUT.plusMillis(1),
                Duration.ofMillis(1)
        ).afterPropertiesSet())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not exceed")
                .isNotInstanceOf(ArithmeticException.class);
    }

    @Test
    void invalidDurationTextFailsBinding() {
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(Map.of(
                "app.database.runtime-timeout.statement-timeout", "thirty parsecs",
                "app.database.runtime-timeout.lock-timeout", "3s"
        ));

        assertThatThrownBy(() -> new Binder(source).bind(
                "app.database.runtime-timeout",
                Bindable.of(RuntimeDatabaseTimeoutProperties.class)
        ).get())
                .isInstanceOf(RuntimeException.class);
    }

    private void assertTimeouts(
            String statement,
            String lock,
            long statementMillis,
            long lockMillis
    ) {
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(Map.of(
                "app.database.runtime-timeout.statement-timeout", statement,
                "app.database.runtime-timeout.lock-timeout", lock
        ));
        RuntimeDatabaseTimeoutProperties properties = new Binder(source).bind(
                "app.database.runtime-timeout",
                Bindable.of(RuntimeDatabaseTimeoutProperties.class)
        ).get();
        properties.afterPropertiesSet();

        assertThat(properties.statementTimeoutMillis()).isEqualTo(statementMillis);
        assertThat(properties.lockTimeoutMillis()).isEqualTo(lockMillis);
    }

    private void assertInvalid(
            Duration statement,
            Duration lock,
            String messageFragment
    ) {
        assertThatThrownBy(() -> properties(statement, lock).afterPropertiesSet())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(messageFragment);
    }

    private RuntimeDatabaseTimeoutProperties properties(
            Duration statement,
            Duration lock
    ) {
        RuntimeDatabaseTimeoutProperties properties =
                new RuntimeDatabaseTimeoutProperties();
        properties.setStatementTimeout(statement);
        properties.setLockTimeout(lock);
        return properties;
    }
}
