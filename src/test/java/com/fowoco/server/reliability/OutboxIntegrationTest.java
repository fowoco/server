package com.fowoco.server.reliability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fowoco.server.reliability.application.NonRetryableEventHandlingException;
import com.fowoco.server.reliability.application.OutboxClaimService;
import com.fowoco.server.reliability.application.OutboxProcessor;
import com.fowoco.server.reliability.application.RetryableEventHandlingException;
import com.fowoco.server.reliability.application.port.DomainEventHandler;
import com.fowoco.server.reliability.application.port.DomainEventPublisher;
import com.fowoco.server.reliability.domain.DomainEventEnvelope;
import com.fowoco.server.reliability.domain.EventActorType;
import com.fowoco.server.reliability.domain.SafeEventPayload;
import java.time.Clock;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@ActiveProfiles("test")
@SpringBootTest(properties = {
        "app.reliability.outbox.enabled=false",
        "app.reliability.outbox.lease-duration=30s",
        "app.reliability.outbox.max-attempts=3",
        "app.reliability.outbox.initial-backoff=1s",
        "app.reliability.outbox.max-backoff=4s"
})
@Import(OutboxIntegrationTest.ReliabilityTestConfiguration.class)
class OutboxIntegrationTest {

    private static final UUID COMPANY_ID =
            UUID.fromString("71000000-0000-0000-0000-000000000001");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private DomainEventPublisher eventPublisher;

    @Autowired
    private OutboxProcessor processor;

    @Autowired
    private OutboxClaimService claimService;

    @Autowired
    private TestEventHandler handler;

    @Autowired
    private Clock clock;

    @BeforeEach
    void resetState() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS reliability_test_effect (
                    event_id UUID NOT NULL PRIMARY KEY,
                    handled_at TIMESTAMP(6) WITH TIME ZONE NOT NULL
                )
                """);
        jdbcTemplate.update("DELETE FROM reliability_test_effect");
        jdbcTemplate.update("DELETE FROM event_consumption");
        jdbcTemplate.update("DELETE FROM event_publication");
        jdbcTemplate.update("DELETE FROM company WHERE company_id = ?", COMPANY_ID);
        jdbcTemplate.update(
                """
                INSERT INTO company (
                    company_id, name, status, created_at, updated_at, version
                ) VALUES (?, 'Outbox 원자성 테스트', 'ACTIVE',
                          CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """,
                COMPANY_ID
        );
        handler.reset();
    }

    @Test
    void businessChangeAndEventPublicationCommitOrRollbackTogether() {
        DomainEventEnvelope rolledBackEvent = event();

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.update(
                    "UPDATE company SET name = '롤백 대상' WHERE company_id = ?",
                    COMPANY_ID
            );
            eventPublisher.publish(rolledBackEvent);
            throw new IllegalStateException("force rollback");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(companyName()).isEqualTo("Outbox 원자성 테스트");
        assertThat(publicationCount(rolledBackEvent.eventId())).isZero();

        DomainEventEnvelope committedEvent = event();
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.update(
                    "UPDATE company SET name = '커밋 완료' WHERE company_id = ?",
                    COMPANY_ID
            );
            eventPublisher.publish(committedEvent);
        });

        assertThat(companyName()).isEqualTo("커밋 완료");
        assertThat(publicationStatus(committedEvent.eventId())).isEqualTo("PENDING");
    }

    @Test
    void transientFailureRollsBackSideEffectAndRetriesSafely() {
        handler.failRetryablyOnce();
        DomainEventEnvelope event = event();
        publish(event);

        assertThat(processor.processAvailable()).isEqualTo(1);

        assertThat(publicationStatus(event.eventId())).isEqualTo("RETRY_WAIT");
        assertThat(publicationAttemptCount(event.eventId())).isEqualTo(1);
        assertThat(lastErrorCode(event.eventId())).isEqualTo("TEST_DEPENDENCY_UNAVAILABLE");
        assertThat(effectCount(event.eventId())).isZero();
        assertThat(consumptionCount(event.eventId())).isZero();

        makeImmediatelyClaimable(event.eventId());

        assertThat(processor.processAvailable()).isEqualTo(1);
        assertThat(publicationStatus(event.eventId())).isEqualTo("COMPLETED");
        assertThat(publicationAttemptCount(event.eventId())).isEqualTo(2);
        assertThat(effectCount(event.eventId())).isEqualTo(1);
        assertThat(consumptionCount(event.eventId())).isEqualTo(1);
        assertThat(handler.invocationCount()).isEqualTo(2);
    }

    @Test
    void completedHandlerIsNotExecutedAgainAfterDuplicateDelivery() {
        DomainEventEnvelope event = event();
        publish(event);
        processor.processAvailable();

        jdbcTemplate.update(
                """
                UPDATE event_publication
                SET status = 'RETRY_WAIT',
                    next_attempt_at = CURRENT_TIMESTAMP,
                    completed_at = NULL,
                    last_error_code = 'DUPLICATE_DELIVERY_SIMULATION',
                    updated_at = CURRENT_TIMESTAMP,
                    version = version + 1
                WHERE event_id = ?
                """,
                event.eventId()
        );

        assertThat(processor.processAvailable()).isEqualTo(1);

        assertThat(publicationStatus(event.eventId())).isEqualTo("COMPLETED");
        assertThat(publicationAttemptCount(event.eventId())).isEqualTo(2);
        assertThat(effectCount(event.eventId())).isEqualTo(1);
        assertThat(consumptionCount(event.eventId())).isEqualTo(1);
        assertThat(handler.invocationCount()).isEqualTo(1);
    }

    @Test
    void expiredLeaseIsRecoveredAfterServerRestart() {
        DomainEventEnvelope event = event();
        publish(event);

        assertThat(claimService.claimBatch("stopped-server"))
                .containsExactly(event.eventId());
        assertThat(publicationStatus(event.eventId())).isEqualTo("PROCESSING");

        jdbcTemplate.update(
                """
                UPDATE event_publication
                SET lease_expires_at = DATEADD('SECOND', -1, CURRENT_TIMESTAMP),
                    updated_at = CURRENT_TIMESTAMP,
                    version = version + 1
                WHERE event_id = ?
                """,
                event.eventId()
        );

        assertThat(processor.processAvailable()).isEqualTo(1);

        assertThat(publicationStatus(event.eventId())).isEqualTo("COMPLETED");
        assertThat(publicationAttemptCount(event.eventId())).isEqualTo(2);
        assertThat(effectCount(event.eventId())).isEqualTo(1);
    }

    @Test
    void nonRetryableFailureMovesEventToManualReview() {
        handler.failPermanently();
        DomainEventEnvelope event = event();
        publish(event);

        assertThat(processor.processAvailable()).isEqualTo(1);

        assertThat(publicationStatus(event.eventId())).isEqualTo("REVIEW_REQUIRED");
        assertThat(lastErrorCode(event.eventId())).isEqualTo("TEST_PAYLOAD_REJECTED");
        assertThat(effectCount(event.eventId())).isZero();
        assertThat(consumptionCount(event.eventId())).isZero();
    }

    @Test
    void publishingOutsideBusinessTransactionIsRejected() {
        assertThatThrownBy(() -> eventPublisher.publish(event()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("active transaction");
    }

    private void publish(DomainEventEnvelope event) {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publish(event));
    }

    private DomainEventEnvelope event() {
        return new DomainEventEnvelope(
                UUID.randomUUID(),
                TestEventHandler.EVENT_TYPE,
                "1",
                "ReliabilityProbe",
                UUID.randomUUID(),
                COMPANY_ID,
                EventActorType.SYSTEM_RULE,
                null,
                "reliability-test-" + UUID.randomUUID(),
                null,
                clock.instant(),
                SafeEventPayload.of(
                        Set.of("result"),
                        Map.of("result", "READY")
                )
        );
    }

    private String companyName() {
        return jdbcTemplate.queryForObject(
                "SELECT name FROM company WHERE company_id = ?",
                String.class,
                COMPANY_ID
        );
    }

    private int publicationCount(UUID eventId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM event_publication WHERE event_id = ?",
                Integer.class,
                eventId
        );
    }

    private String publicationStatus(UUID eventId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM event_publication WHERE event_id = ?",
                String.class,
                eventId
        );
    }

    private int publicationAttemptCount(UUID eventId) {
        return jdbcTemplate.queryForObject(
                "SELECT attempt_count FROM event_publication WHERE event_id = ?",
                Integer.class,
                eventId
        );
    }

    private String lastErrorCode(UUID eventId) {
        return jdbcTemplate.queryForObject(
                "SELECT last_error_code FROM event_publication WHERE event_id = ?",
                String.class,
                eventId
        );
    }

    private int effectCount(UUID eventId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reliability_test_effect WHERE event_id = ?",
                Integer.class,
                eventId
        );
    }

    private int consumptionCount(UUID eventId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM event_consumption WHERE event_id = ?",
                Integer.class,
                eventId
        );
    }

    private void makeImmediatelyClaimable(UUID eventId) {
        jdbcTemplate.update(
                """
                UPDATE event_publication
                SET next_attempt_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP,
                    version = version + 1
                WHERE event_id = ?
                """,
                eventId
        );
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ReliabilityTestConfiguration {

        @Bean
        TestEventHandler testEventHandler(JdbcTemplate jdbcTemplate) {
            return new TestEventHandler(jdbcTemplate);
        }
    }

    static final class TestEventHandler implements DomainEventHandler {

        private static final String EVENT_TYPE = "ReliabilityTestRequested";

        private final JdbcTemplate jdbcTemplate;
        private final AtomicInteger invocationCount = new AtomicInteger();
        private final AtomicInteger retryableFailuresRemaining = new AtomicInteger();
        private volatile boolean permanentFailure;

        private TestEventHandler(JdbcTemplate jdbcTemplate) {
            this.jdbcTemplate = jdbcTemplate;
        }

        @Override
        public String handlerName() {
            return "reliability-test-handler-v1";
        }

        @Override
        public boolean supports(String eventType) {
            return EVENT_TYPE.equals(eventType);
        }

        @Override
        public void handle(DomainEventEnvelope event) {
            invocationCount.incrementAndGet();
            jdbcTemplate.update(
                    """
                    INSERT INTO reliability_test_effect (event_id, handled_at)
                    VALUES (?, CURRENT_TIMESTAMP)
                    """,
                    event.eventId()
            );
            if (permanentFailure) {
                throw new NonRetryableEventHandlingException("TEST_PAYLOAD_REJECTED");
            }
            if (retryableFailuresRemaining.getAndUpdate(value -> Math.max(0, value - 1)) > 0) {
                throw new RetryableEventHandlingException(
                        "TEST_DEPENDENCY_UNAVAILABLE"
                );
            }
        }

        void failRetryablyOnce() {
            retryableFailuresRemaining.set(1);
        }

        void failPermanently() {
            permanentFailure = true;
        }

        int invocationCount() {
            return invocationCount.get();
        }

        void reset() {
            invocationCount.set(0);
            retryableFailuresRemaining.set(0);
            permanentFailure = false;
        }
    }
}
