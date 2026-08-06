package com.fowoco.server.common.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "POSTGRES_TEST_ENABLED", matches = "true")
class PostgreSqlRlsTestLockTest {

    @Test
    void boundsContentionAndReleasesTheSessionLockWhenClosed() throws Exception {
        String url = requiredEnvironmentVariable("POSTGRES_TEST_URL");
        String username = requiredEnvironmentVariable("POSTGRES_TEST_USERNAME");
        String password = requiredEnvironmentVariable("POSTGRES_TEST_PASSWORD");

        try (PostgreSqlRlsTestLock first = PostgreSqlRlsTestLock.acquire(
                url,
                username,
                password
        )) {
            assertThatThrownBy(() -> {
                try (PostgreSqlRlsTestLock ignored = PostgreSqlRlsTestLock.acquire(
                        url,
                        username,
                        password,
                        Duration.ofMillis(150)
                )) {
                    // Close an unexpectedly acquired lock before failing the assertion.
                }
            })
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("PostgreSQL RLS test lock")
                    .hasMessageContaining("shared database")
                    .hasMessageNotContaining(url)
                    .hasMessageNotContaining(username)
                    .hasMessageNotContaining(password);

            first.close();
            first.close();
        }

        try (PostgreSqlRlsTestLock ignored = PostgreSqlRlsTestLock.acquire(
                url,
                username,
                password,
                Duration.ofSeconds(2)
        )) {
            // Acquiring again proves that closing the owning session released the lock.
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
