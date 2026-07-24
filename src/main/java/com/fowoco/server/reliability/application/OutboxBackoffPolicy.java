package com.fowoco.server.reliability.application;

import com.fowoco.server.reliability.config.OutboxProperties;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class OutboxBackoffPolicy {

    private final Duration initialBackoff;
    private final Duration maxBackoff;

    public OutboxBackoffPolicy(OutboxProperties properties) {
        initialBackoff = properties.getInitialBackoff();
        maxBackoff = properties.getMaxBackoff();
        if (maxBackoff.compareTo(initialBackoff) < 0) {
            throw new IllegalArgumentException(
                    "Outbox maxBackoff cannot be shorter than initialBackoff."
            );
        }
    }

    public Duration delayForAttempt(int attemptCount) {
        if (attemptCount < 1) {
            throw new IllegalArgumentException("attemptCount must be positive");
        }
        long multiplier = 1L << Math.min(attemptCount - 1, 30);
        try {
            Duration calculated = initialBackoff.multipliedBy(multiplier);
            return calculated.compareTo(maxBackoff) > 0 ? maxBackoff : calculated;
        } catch (ArithmeticException exception) {
            return maxBackoff;
        }
    }
}
