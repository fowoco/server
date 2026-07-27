package com.fowoco.server.reliability.application;

import com.fowoco.server.reliability.application.port.OutboxClaimBootstrap;
import com.fowoco.server.reliability.application.port.OutboxClaimBootstrap.ClaimResult;
import com.fowoco.server.reliability.config.OutboxProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxClaimService {

    private final OutboxClaimBootstrap claimBootstrap;
    private final OutboxProperties properties;
    private final OutboxMetrics metrics;
    private final Clock clock;

    public OutboxClaimService(
            OutboxClaimBootstrap claimBootstrap,
            OutboxProperties properties,
            OutboxMetrics metrics,
            Clock clock
    ) {
        this.claimBootstrap = claimBootstrap;
        this.properties = properties;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Transactional
    public List<ClaimedEvent> claimBatch(String owner) {
        Instant now = clock.instant();
        List<ClaimResult> results = claimBootstrap.claim(
                owner,
                now,
                properties.getLeaseDuration(),
                properties.getBatchSize(),
                properties.getMaxAttempts()
        );
        List<ClaimedEvent> claimed = new ArrayList<>(results.size());
        for (ClaimResult result : results) {
            if (result.reviewRequired()) {
                metrics.recordReviewRequired();
            } else {
                claimed.add(new ClaimedEvent(result.eventId(), result.companyId()));
            }
        }
        return List.copyOf(claimed);
    }

    public record ClaimedEvent(UUID eventId, UUID companyId) {

        public ClaimedEvent {
            if (eventId == null || companyId == null) {
                throw new IllegalArgumentException("claimed event identifiers must not be null");
            }
        }
    }
}
