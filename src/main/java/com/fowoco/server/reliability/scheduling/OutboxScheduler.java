package com.fowoco.server.reliability.scheduling;

import com.fowoco.server.reliability.application.OutboxProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "app.reliability.outbox.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class OutboxScheduler {

    private final OutboxProcessor processor;

    public OutboxScheduler(OutboxProcessor processor) {
        this.processor = processor;
    }

    @Scheduled(fixedDelayString = "${app.reliability.outbox.poll-interval:1s}")
    public void processAvailableEvents() {
        processor.processAvailable();
    }
}
