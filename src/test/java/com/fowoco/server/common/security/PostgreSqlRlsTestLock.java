package com.fowoco.server.common.security;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Objects;

/**
 * Serializes tests that temporarily change row-level-security state in a shared PostgreSQL
 * database.
 */
public final class PostgreSqlRlsTestLock implements AutoCloseable {

    private static final long LOCK_KEY = 0x466f776f636f524cL;
    private static final Duration DEFAULT_ACQUISITION_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration RETRY_INTERVAL = Duration.ofMillis(50);

    private Connection connection;

    private PostgreSqlRlsTestLock(Connection connection) {
        this.connection = connection;
    }

    public static PostgreSqlRlsTestLock acquire(
            String url,
            String username,
            String password
    ) throws SQLException {
        return acquire(url, username, password, DEFAULT_ACQUISITION_TIMEOUT);
    }

    public static PostgreSqlRlsTestLock acquire(
            String url,
            String username,
            String password,
            Duration acquisitionTimeout
    ) throws SQLException {
        Objects.requireNonNull(url, "url must not be null");
        Objects.requireNonNull(username, "username must not be null");
        Objects.requireNonNull(password, "password must not be null");
        Objects.requireNonNull(acquisitionTimeout, "acquisitionTimeout must not be null");
        if (acquisitionTimeout.isZero() || acquisitionTimeout.isNegative()) {
            throw new IllegalArgumentException("acquisitionTimeout must be positive");
        }

        Connection connection = DriverManager.getConnection(url, username, password);
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT pg_catalog.pg_try_advisory_lock(?)"
        )) {
            statement.setLong(1, LOCK_KEY);
            long timeoutNanos = acquisitionTimeout.toNanos();
            long startedAt = System.nanoTime();

            while (true) {
                if (tryAcquire(statement)) {
                    return new PostgreSqlRlsTestLock(connection);
                }

                long elapsedNanos = System.nanoTime() - startedAt;
                if (elapsedNanos >= timeoutNanos) {
                    throw new IllegalStateException(
                            "PostgreSQL RLS test lock was not acquired within "
                                    + acquisitionTimeout
                                    + "; another security test may be using the shared database"
                    );
                }
                sleep(Math.min(RETRY_INTERVAL.toNanos(), timeoutNanos - elapsedNanos));
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            IllegalStateException failure = new IllegalStateException(
                    "Interrupted while waiting for the PostgreSQL RLS test lock",
                    exception
            );
            closeAfterFailure(connection, failure);
            throw failure;
        } catch (SQLException | RuntimeException | Error failure) {
            closeAfterFailure(connection, failure);
            throw failure;
        }
    }

    private static boolean tryAcquire(PreparedStatement statement) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) {
                throw new SQLException("PostgreSQL RLS test lock query returned no result");
            }
            return resultSet.getBoolean(1);
        }
    }

    private static void sleep(long nanos) throws InterruptedException {
        long millis = nanos / 1_000_000L;
        int remainingNanos = (int) (nanos - millis * 1_000_000L);
        Thread.sleep(millis, remainingNanos);
    }

    private static void closeAfterFailure(Connection connection, Throwable failure) {
        try {
            connection.close();
        } catch (Throwable closeFailure) {
            failure.addSuppressed(closeFailure);
        }
    }

    @Override
    public synchronized void close() throws SQLException {
        Connection connectionToClose = connection;
        connection = null;
        if (connectionToClose != null) {
            connectionToClose.close();
        }
    }
}
