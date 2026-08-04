package com.fowoco.server.casework.application;

import com.fowoco.server.casework.domain.CaseDisplayStatus;
import java.util.Objects;

/**
 * 업무함 API에 반환할 Case 표시 상태와 새 응답 표시입니다.
 */
public record CaseDisplayState(
        CaseDisplayStatus status,
        boolean hasUnreadResponse
) {
    public CaseDisplayState {
        Objects.requireNonNull(status, "status must not be null");
    }
}
