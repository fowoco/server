package com.fowoco.server.reliability.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventPublicationTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-07-24T00:00:00Z");

    @Test
    void anExpiredLeaseCanBeClaimedByAnotherWorker() {
        EventPublication publication = pendingPublication();
        publication.claim("server-blue", OCCURRED_AT, Duration.ofSeconds(30));

        assertThat(publication.isClaimableAt(OCCURRED_AT.plusSeconds(29))).isFalse();
        assertThat(publication.isClaimableAt(OCCURRED_AT.plusSeconds(30))).isTrue();

        publication.claim(
                "server-green",
                OCCURRED_AT.plusSeconds(30),
                Duration.ofSeconds(30)
        );

        assertThat(publication.status()).isEqualTo(EventPublicationStatus.PROCESSING);
        assertThat(publication.leaseOwner()).isEqualTo("server-green");
        assertThat(publication.attemptCount()).isEqualTo(2);
    }

    @Test
    void aWorkerCannotCompleteAnotherWorkersActiveLease() {
        EventPublication publication = pendingPublication();
        publication.claim("server-blue", OCCURRED_AT, Duration.ofSeconds(30));

        assertThatThrownBy(() -> publication.complete(
                "server-green",
                OCCURRED_AT.plusSeconds(1)
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("lease");
    }

    @Test
    void retryClearsLeaseAndSchedulesTheNextAttempt() {
        EventPublication publication = pendingPublication();
        publication.claim("server-blue", OCCURRED_AT, Duration.ofSeconds(30));
        Instant nextAttempt = OCCURRED_AT.plusSeconds(10);

        publication.retry(
                "server-blue",
                "DEPENDENCY_TEMPORARY_FAILURE",
                nextAttempt,
                OCCURRED_AT.plusSeconds(1)
        );

        assertThat(publication.status()).isEqualTo(EventPublicationStatus.RETRY_WAIT);
        assertThat(publication.leaseOwner()).isNull();
        assertThat(publication.nextAttemptAt()).isEqualTo(nextAttempt);
        assertThat(publication.lastErrorCode()).isEqualTo("DEPENDENCY_TEMPORARY_FAILURE");
    }

    private EventPublication pendingPublication() {
        UUID companyId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        DomainEventEnvelope event = new DomainEventEnvelope(
                eventId,
                "TaskCreated",
                "1",
                "Task",
                UUID.randomUUID(),
                companyId,
                EventActorType.SYSTEM_RULE,
                null,
                "request-1",
                "0123456789abcdef0123456789abcdef",
                OCCURRED_AT,
                SafeEventPayload.empty()
        );
        return EventPublication.pending(event, "{}", OCCURRED_AT);
    }
}
