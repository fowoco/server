package com.fowoco.server.aiintegration.infrastructure.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fowoco.server.aiintegration.application.error.AiRuntimeCallException;
import com.fowoco.server.aiintegration.application.error.AiRuntimeFailureCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class AiRuntimeCircuitBreakerTest {

    @Test
    void allowsOneHalfOpenProbeAndClosesAfterSuccess() {
        MutableClock clock = new MutableClock();
        AiRuntimeCircuitBreaker breaker = new AiRuntimeCircuitBreaker(
                1,
                Duration.ofSeconds(30),
                clock
        );
        breaker.beforeCall();
        breaker.recordFailure();

        assertCircuitOpen(breaker);
        clock.advance(Duration.ofSeconds(30));

        breaker.beforeCall();
        assertCircuitOpen(breaker);
        breaker.recordSuccess();
        breaker.beforeCall();
        breaker.recordSuccess();
    }

    private void assertCircuitOpen(AiRuntimeCircuitBreaker breaker) {
        assertThatThrownBy(breaker::beforeCall)
                .isInstanceOfSatisfying(
                        AiRuntimeCallException.class,
                        exception -> assertThat(exception.failureCode())
                                .isEqualTo(AiRuntimeFailureCode.CIRCUIT_OPEN)
                );
    }

    private static final class MutableClock extends Clock {

        private Instant instant = Instant.parse("2026-07-26T00:00:00Z");

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
