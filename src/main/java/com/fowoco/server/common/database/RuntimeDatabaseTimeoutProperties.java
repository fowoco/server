package com.fowoco.server.common.database;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;

@ConfigurationProperties(prefix = "app.database.runtime-timeout")
public class RuntimeDatabaseTimeoutProperties implements InitializingBean {

    static final Duration MAX_POSTGRESQL_TIMEOUT = Duration.ofMillis(Integer.MAX_VALUE);

    @DurationUnit(ChronoUnit.MILLIS)
    private Duration statementTimeout = Duration.ofSeconds(30);
    @DurationUnit(ChronoUnit.MILLIS)
    private Duration lockTimeout = Duration.ofSeconds(3);
    private long statementTimeoutMillis = 30_000L;
    private long lockTimeoutMillis = 3_000L;

    public Duration getStatementTimeout() {
        return statementTimeout;
    }

    public void setStatementTimeout(
            @DurationUnit(ChronoUnit.MILLIS) Duration statementTimeout
    ) {
        this.statementTimeout = statementTimeout;
    }

    public Duration getLockTimeout() {
        return lockTimeout;
    }

    public void setLockTimeout(@DurationUnit(ChronoUnit.MILLIS) Duration lockTimeout) {
        this.lockTimeout = lockTimeout;
    }

    public long statementTimeoutMillis() {
        return statementTimeoutMillis;
    }

    public long lockTimeoutMillis() {
        return lockTimeoutMillis;
    }

    @Override
    public void afterPropertiesSet() {
        statementTimeoutMillis = validateAndConvert(statementTimeout, "statementTimeout");
        lockTimeoutMillis = validateAndConvert(lockTimeout, "lockTimeout");
        if (lockTimeout.compareTo(statementTimeout) >= 0) {
            throw new IllegalArgumentException(
                    "lockTimeout must be shorter than statementTimeout"
            );
        }
    }

    private static long validateAndConvert(Duration duration, String field) {
        if (duration == null) {
            throw new IllegalArgumentException(field + " must not be null");
        }
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        if (duration.getNano() % 1_000_000 != 0) {
            throw new IllegalArgumentException(
                    field + " must be aligned to whole milliseconds"
            );
        }
        if (duration.compareTo(MAX_POSTGRESQL_TIMEOUT) > 0) {
            throw new IllegalArgumentException(
                    field + " must not exceed " + Integer.MAX_VALUE + " milliseconds"
            );
        }
        return duration.toMillis();
    }
}
