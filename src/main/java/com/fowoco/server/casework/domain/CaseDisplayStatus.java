package com.fowoco.server.casework.domain;

/**
 * 업무함에서 사용자에게 보여 주는 Case 요약 상태입니다.
 *
 * <p>Task의 업무 처리 상태와 Worker Link의 전달 상태를 바꾸지 않고,
 * 조회 시점의 사실을 조합해 화면에 표시할 값만 나타냅니다.</p>
 */
public enum CaseDisplayStatus {
    DOCUMENT_PENDING,
    REQUEST_SENT,
    REVIEW_REQUIRED,
    COMPLETED
}
