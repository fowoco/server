package com.fowoco.server.casework.application;

/**
 * Case 표시 상태를 계산할 때 사용하는 조회 사실입니다.
 *
 * @param completed Case 업무가 모두 완료됐는지 여부
 * @param cancelled Case가 취소됐는지 여부
 * @param linkSent HR이 근로자용 요청 링크의 전달 완료를 기록했는지 여부
 * @param reviewRequired 도착한 답변이나 서류에 HR 검토가 남아 있는지 여부
 * @param unreadResponse 도착했지만 아직 읽지 않은 근로자 응답이 있는지 여부
 */
public record CaseDisplayFacts(
        boolean completed,
        boolean cancelled,
        boolean linkSent,
        boolean reviewRequired,
        boolean unreadResponse
) {
}
