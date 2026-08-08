package com.fowoco.server.casework.application;

import static com.fowoco.server.casework.domain.CaseDisplayStatus.COMPLETED;
import static com.fowoco.server.casework.domain.CaseDisplayStatus.CANCELLED;
import static com.fowoco.server.casework.domain.CaseDisplayStatus.DOCUMENT_PENDING;
import static com.fowoco.server.casework.domain.CaseDisplayStatus.REQUEST_SENT;
import static com.fowoco.server.casework.domain.CaseDisplayStatus.REVIEW_REQUIRED;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CaseDisplayStatusResolverTest {

    private final CaseDisplayStatusResolver resolver = new CaseDisplayStatusResolver();

    @Test
    void showsDocumentPendingBeforeWorkerLinkIsSent() {
        CaseDisplayState result = resolver.resolve(facts(false, false, false, false));

        assertThat(result.status()).isEqualTo(DOCUMENT_PENDING);
        assertThat(result.hasUnreadResponse()).isFalse();
    }

    @Test
    void showsRequestSentAfterWorkerLinkIsSentWithoutResponse() {
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
        CaseDisplayState result = resolver.resolve(facts(true, false, true, true, true));

        assertThat(result.status()).isEqualTo(COMPLETED);
        assertThat(result.hasUnreadResponse()).isFalse();
    }

    @Test
    void cancelledTakesPriorityAndClearsUnreadBadge() {
        CaseDisplayState result = resolver.resolve(facts(false, true, true, true, true));

        assertThat(result.status()).isEqualTo(CANCELLED);
        assertThat(result.hasUnreadResponse()).isFalse();
    }

    private CaseDisplayFacts facts(
            boolean completed,
            boolean linkSent,
            boolean reviewRequired,
            boolean unreadResponse
    ) {
        return facts(completed, false, linkSent, reviewRequired, unreadResponse);
    }

    private CaseDisplayFacts facts(
            boolean completed,
            boolean cancelled,
            boolean linkSent,
            boolean reviewRequired,
            boolean unreadResponse
    ) {
        return new CaseDisplayFacts(
                completed,
                cancelled,
                linkSent,
                reviewRequired,
                unreadResponse
        );
    }
}
