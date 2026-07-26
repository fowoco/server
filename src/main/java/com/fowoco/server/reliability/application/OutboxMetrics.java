package com.fowoco.server.reliability.application;

import com.fowoco.server.reliability.application.port.EventPublicationRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class OutboxMetrics {

    private final Counter completed;
    private final Counter retried;
    private final Counter reviewRequired;

    public OutboxMetrics(
            MeterRegistry meterRegistry,
            EventPublicationRepository repository,
            Clock clock
    ) {
        completed = counter(meterRegistry, "completed");
        retried = counter(meterRegistry, "retry");
        reviewRequired = counter(meterRegistry, "review_required");
        Gauge.builder(
                        "fowoco.outbox.publications.backlog",
                        repository,
                        EventPublicationRepository::countOutstanding
                )
                .description("Outstanding durable event publications")
                .register(meterRegistry);
        Gauge.builder(
                        "fowoco.outbox.publications.oldest.delay.seconds",
                        repository,
                        candidate -> candidate.findOldestOutstandingOccurredAt()
                                .map(occurredAt -> Math.max(
                                        0.0,
                                        Duration.between(occurredAt, clock.instant()).toMillis()
                                                / 1000.0
                                ))
                                .orElse(0.0)
                )
                .description("Age in seconds of the oldest outstanding publication")
                .register(meterRegistry);
    }

    public void recordCompleted() {
        completed.increment();
    }

    public void recordRetry() {
        retried.increment();
    }

    public void recordReviewRequired() {
        reviewRequired.increment();
    }

    private Counter counter(MeterRegistry registry, String result) {
        return Counter.builder("fowoco.outbox.publications.processed")
                .description("Durable event publication processing outcomes")
                .tag("result", result)
                .register(registry);
    }
}
