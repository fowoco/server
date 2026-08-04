package com.fowoco.server.casework.application;

import static com.fowoco.server.casework.domain.CaseDisplayStatus.COMPLETED;
import static com.fowoco.server.casework.domain.CaseDisplayStatus.DOCUMENT_PENDING;
import static com.fowoco.server.casework.domain.CaseDisplayStatus.REQUEST_SENT;
import static com.fowoco.server.casework.domain.CaseDisplayStatus.REVIEW_REQUIRED;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CaseDisplayStatusResolverTest {

    private final CaseDisplayStatusResolver resolver = new CaseDisplayStatusResolver();

    @Test
    void showsDocumentPendingBeforeWorkerLinkIsIssued() {
        CaseDisplayState result = resolver.resolve(facts(false, false, false, false));

        assertThat(result.status()).isEqualTo(DOCUMENT_PENDING);
        assertThat(result.hasUnreadResponse()).isFalse();
    }

    @Test
    void showsRequestSentAfterWorkerLinkIsIssuedWithoutResponse() {
        CaseDisplayState result = resolver.resolve(facts(false, true, false, false));

        assertThat(result.status()).isEqualTo(REQUEST_SENT);
        assertThat(result.hasUnreadResponse()).isFalse();
    }

    @Test
    void keepsReviewRequiredAfterResponseIsReadUntilHrDecision() {
        CaseDisplayState result = resolver.resolve(facts(false, true, true, false));

        assertThat(result.status()).isEqualTo(REVIEW_REQUIRED);
        assertThat(result.hasUnreadResponse()).isFalse();
    }

    @Test
    void showsReviewRequiredAndUnreadBadgeForNewWorkerResponse() {
        CaseDisplayState result = resolver.resolve(facts(false, true, false, true));

        assertThat(result.status()).isEqualTo(REVIEW_REQUIRED);
        assertThat(result.hasUnreadResponse()).isTrue();
    }

    @Test
    void completedTakesPriorityAndClearsUnreadBadge() {
        CaseDisplayState result = resolver.resolve(facts(true, true, true, true));

        assertThat(result.status()).isEqualTo(COMPLETED);
        assertThat(result.hasUnreadResponse()).isFalse();
    }

    private CaseDisplayFacts facts(
            boolean completed,
            boolean linkIssued,
            boolean reviewRequired,
            boolean unreadResponse
    ) {
        return new CaseDisplayFacts(
                completed,
                linkIssued,
                reviewRequired,
                unreadResponse
        );
    }
}
