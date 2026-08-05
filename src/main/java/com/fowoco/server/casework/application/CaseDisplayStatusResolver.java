package com.fowoco.server.casework.application;

import com.fowoco.server.casework.domain.CaseDisplayStatus;
import java.util.Objects;

/**
 * 여러 모듈의 상태를 변경하지 않고 업무함 표시 상태만 계산합니다.
 */
public final class CaseDisplayStatusResolver {

    public CaseDisplayState resolve(CaseDisplayFacts facts) {
        Objects.requireNonNull(facts, "facts must not be null");

        if (facts.cancelled()) {
            return new CaseDisplayState(CaseDisplayStatus.CANCELLED, false);
        }
        if (facts.completed()) {
            return new CaseDisplayState(CaseDisplayStatus.COMPLETED, false);
        }
        if (facts.reviewRequired() || facts.unreadResponse()) {
            return new CaseDisplayState(
                    CaseDisplayStatus.REVIEW_REQUIRED,
                    facts.unreadResponse()
            );
        }
        if (facts.linkIssued()) {
            return new CaseDisplayState(CaseDisplayStatus.REQUEST_SENT, false);
        }
        return new CaseDisplayState(CaseDisplayStatus.DOCUMENT_PENDING, false);
    }
}
