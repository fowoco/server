package com.fowoco.server.workerlink.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkerLinkTest {

    private static final UUID LINK_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID TASK_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID COMPANY_ID = UUID.fromString("30000000-0000-0000-0000-000000000003");
    private static final UUID HR_ID = UUID.fromString("40000000-0000-0000-0000-000000000004");
    private static final Instant ISSUED_AT = Instant.parse("2026-08-08T00:00:00Z");

    @Test
    void issuedLinkStartsNotSent() {
        WorkerLink link = issueLink();

        assertThat(link.deliveryStatus()).isEqualTo(WorkerLinkDeliveryStatus.NOT_SENT);
        assertThat(link.sentAt()).isNull();
        assertThat(link.sentBy()).isNull();
    }

    @Test
    void markSentRecordsActorAndServerTimeOnlyOnce() {
        WorkerLink link = issueLink();
        Instant sentAt = ISSUED_AT.plusSeconds(60);

        WorkerLink sent = link.markSent(HR_ID, sentAt);
        WorkerLink repeated = sent.markSent(UUID.randomUUID(), sentAt.plusSeconds(60));

        assertThat(sent.deliveryStatus()).isEqualTo(WorkerLinkDeliveryStatus.SENT);
        assertThat(sent.sentAt()).isEqualTo(sentAt);
        assertThat(sent.sentBy()).isEqualTo(HR_ID);
        assertThat(repeated).isSameAs(sent);
    }

    @Test
    void deliveryStateSeparatesRejectedAndUncertainProviderResults() {
        Instant startedAt = ISSUED_AT.plusSeconds(10);
        WorkerLink sending = issueLink().markSending(startedAt);

        WorkerLink rejected = sending.markNotSentAfterRejectedDelivery(startedAt.plusSeconds(1));
        WorkerLink uncertain = sending.markDeliveryReviewRequired(startedAt.plusSeconds(1));

        assertThat(sending.deliveryStatus()).isEqualTo(WorkerLinkDeliveryStatus.SENDING);
        assertThat(rejected.deliveryStatus()).isEqualTo(WorkerLinkDeliveryStatus.NOT_SENT);
        assertThat(uncertain.deliveryStatus()).isEqualTo(WorkerLinkDeliveryStatus.REVIEW_REQUIRED);
        assertThat(rejected.sentAt()).isNull();
        assertThat(uncertain.sentAt()).isNull();
    }

    private WorkerLink issueLink() {
        return WorkerLink.issue(
                LINK_ID,
                TASK_ID,
                COMPANY_ID,
                "a".repeat(64),
                ISSUED_AT.plusSeconds(3600),
                HR_ID,
                null,
                "worker-link-test-key",
                ISSUED_AT
        );
    }
}
