package com.fowoco.server.common.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class DatabaseTimestampTest {

    @Test
    void normalizesJavaNanosecondsToDatabaseMicroseconds() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-07-25T00:00:00.123456789Z"),
                ZoneOffset.UTC
        );

        assertThat(DatabaseTimestamp.now(clock))
                .isEqualTo(Instant.parse("2026-07-25T00:00:00.123456Z"));
    }

    @Test
    void updateTimestampNeverMovesBeforeCreationTimestamp() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-07-25T00:00:00.123456900Z"),
                ZoneOffset.UTC
        );
        Instant databaseCreationTime =
                Instant.parse("2026-07-25T00:00:00.123457Z");

        assertThat(DatabaseTimestamp.nowNotBefore(clock, databaseCreationTime))
                .isEqualTo(databaseCreationTime);
    }
}
