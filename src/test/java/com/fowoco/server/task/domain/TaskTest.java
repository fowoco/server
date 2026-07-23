package com.fowoco.server.task.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fowoco.server.common.error.ApiException;
import com.fowoco.server.task.application.error.TaskErrorCode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TaskTest {

    private static final UUID ACTOR_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-07-23T01:00:00Z");
    private static final String FINGERPRINT_A = "a".repeat(64);
    private static final String FINGERPRINT_B = "b".repeat(64);

    @Test
    void requestsReviewOnlyWhenRequirementsAreSatisfied() {
        Task task = task(TaskStatus.NEEDS_INFO);

        TaskStatus previous = task.requestReview(true, 0, ACTOR_ID, NOW.plusSeconds(1));

        assertThat(previous).isEqualTo(TaskStatus.NEEDS_INFO);
        assertThat(task.status()).isEqualTo(TaskStatus.READY_FOR_REVIEW);
    }

    @Test
    void rejectsReviewWhenRequirementsAreMissing() {
        Task task = task(TaskStatus.DRAFT);

        assertThatThrownBy(() -> task.requestReview(false, 0, ACTOR_ID, NOW))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(TaskErrorCode.TASK_REQUIREMENTS_MISSING));
    }

    @Test
    void followsApprovalSubmissionAndCompletionSequence() {
        Task task = task(TaskStatus.READY_FOR_REVIEW);

        task.approve(0, ACTOR_ID, NOW.plusSeconds(1));
        task.recordExternalSubmission(0, ACTOR_ID, NOW.plusSeconds(2));
        task.complete(true, true, 0, ACTOR_ID, NOW.plusSeconds(3));

        assertThat(task.status()).isEqualTo(TaskStatus.COMPLETED);
    }

    @Test
    void requiresEvidenceForCompletion() {
        Task task = task(TaskStatus.WAITING_EXTERNAL);

        assertThatThrownBy(() -> task.complete(true, false, 0, ACTOR_ID, NOW))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(TaskErrorCode.EVIDENCE_REQUIRED));
    }

    @Test
    void invalidatesApprovalWhenCriticalDataChanges() {
        Task task = task(TaskStatus.APPROVED);

        Task.UpdateOutcome outcome = task.updateContent(
                "수정된 재계약",
                null,
                "{\"wage\":3100000}",
                FINGERPRINT_B,
                LocalDate.of(2026, 8, 1),
                0,
                ACTOR_ID,
                NOW.plusSeconds(1)
        );

        assertThat(outcome.criticalChanged()).isTrue();
        assertThat(outcome.approvalInvalidated()).isTrue();
        assertThat(task.status()).isEqualTo(TaskStatus.READY_FOR_REVIEW);
    }

    @Test
    void rejectsAStaleExpectedVersion() {
        Task task = task(TaskStatus.READY_FOR_REVIEW);

        assertThatThrownBy(() -> task.approve(1, ACTOR_ID, NOW))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(TaskErrorCode.TASK_VERSION_CONFLICT));
    }

    @Test
    void terminalTaskCannotBeCancelledAgain() {
        Task task = task(TaskStatus.COMPLETED);

        assertThatThrownBy(() -> task.cancel(0, ACTOR_ID, NOW))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(TaskErrorCode.INVALID_TASK_TRANSITION));
    }

    private Task task(TaskStatus status) {
        return new Task(
                UUID.fromString("20000000-0000-0000-0000-000000000001"),
                UUID.fromString("30000000-0000-0000-0000-000000000001"),
                UUID.fromString("40000000-0000-0000-0000-000000000001"),
                UUID.fromString("50000000-0000-0000-0000-000000000001"),
                TaskType.RECONTRACT,
                "recontract.v1",
                "expiry-renewal-v1",
                "재계약 준비",
                null,
                "{\"wage\":3000000}",
                FINGERPRINT_A,
                0,
                TaskSource.MANUAL,
                status,
                LocalDate.of(2026, 7, 31),
                ACTOR_ID,
                ACTOR_ID,
                NOW,
                NOW,
                0
        );
    }
}
