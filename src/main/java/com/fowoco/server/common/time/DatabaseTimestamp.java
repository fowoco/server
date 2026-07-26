package com.fowoco.server.common.time;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * PostgreSQL TIMESTAMP(6)과 같은 마이크로초 정밀도의 시각을 만듭니다.
 */
public final class DatabaseTimestamp {

    private DatabaseTimestamp() {
    }

    public static Instant now(Clock clock) {
        Objects.requireNonNull(clock, "clock must not be null");
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    public static Instant nowNotBefore(Clock clock, Instant minimum) {
        Objects.requireNonNull(minimum, "minimum must not be null");
        Instant current = now(clock);
        return current.isBefore(minimum) ? minimum : current;
    }
}
