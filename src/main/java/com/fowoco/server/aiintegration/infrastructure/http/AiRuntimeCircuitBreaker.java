package com.fowoco.server.aiintegration.infrastructure.http;

import com.fowoco.server.aiintegration.application.error.AiRuntimeCallException;
import com.fowoco.server.aiintegration.application.error.AiRuntimeFailureCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Small consecutive-failure circuit breaker for one AI Runtime endpoint.
 */
final class AiRuntimeCircuitBreaker {

    private final int failureThreshold;
    private final Duration openDuration;
    private final Clock clock;

    private State state = State.CLOSED;
    private int consecutiveFailures;
    private Instant reopenAt = Instant.EPOCH;
    private boolean halfOpenProbeInFlight;

    AiRuntimeCircuitBreaker(int failureThreshold, Duration openDuration, Clock clock) {
        if (failureThreshold < 1) {
            throw new IllegalArgumentException("failureThreshold must be positive");
        }
        if (openDuration == null || openDuration.isZero() || openDuration.isNegative()) {
            throw new IllegalArgumentException("openDuration must be positive");
        }
        this.failureThreshold = failureThreshold;
        this.openDuration = openDuration;
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    synchronized void beforeCall() {
        Instant now = clock.instant();
        if (state == State.OPEN && !now.isBefore(reopenAt)) {
            state = State.HALF_OPEN;
            halfOpenProbeInFlight = false;
        }
        if (state == State.OPEN || (state == State.HALF_OPEN && halfOpenProbeInFlight)) {
            throw new AiRuntimeCallException(
                    AiRuntimeFailureCode.CIRCUIT_OPEN,
                    "AI Runtime circuit is open."
            );
        }
        if (state == State.HALF_OPEN) {
            halfOpenProbeInFlight = true;
        }
    }

    synchronized void recordSuccess() {
        state = State.CLOSED;
        consecutiveFailures = 0;
        halfOpenProbeInFlight = false;
        reopenAt = Instant.EPOCH;
    }

    synchronized void recordFailure() {
        if (state == State.HALF_OPEN) {
            open();
            return;
        }
        consecutiveFailures++;
        if (consecutiveFailures >= failureThreshold) {
            open();
        }
    }

    private void open() {
        state = State.OPEN;
        halfOpenProbeInFlight = false;
        reopenAt = clock.instant().plus(openDuration);
    }

    private enum State {
        CLOSED,
        OPEN,
        HALF_OPEN
    }
}
