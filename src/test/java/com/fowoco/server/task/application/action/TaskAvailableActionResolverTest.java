package com.fowoco.server.task.application.action;

import static org.assertj.core.api.Assertions.assertThat;

import com.fowoco.server.task.application.TaskAssigneeView;
import com.fowoco.server.task.application.TaskResult;
import com.fowoco.server.task.domain.Task;
import com.fowoco.server.task.domain.TaskChecklistItem;
import com.fowoco.server.task.domain.TaskSource;
import com.fowoco.server.task.domain.TaskStatus;
import com.fowoco.server.task.domain.TaskType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TaskAvailableActionResolverTest {

    private static final UUID TASK_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID COMPANY_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID WORKER_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID CASE_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final UUID ACTOR_ID = UUID.fromString("60000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-08-20T00:00:00Z");

    private final TaskAvailableActionResolver resolver = new TaskAvailableActionResolver();

    @Test
    void freshSupportedRenewalCanRunAgent() {
        TaskActionDecision decision = resolver.resolve(result(
                TaskStatus.DRAFT,
                Map.of(),
                List.of(completedChecklist()),
                List.of()
        ));

        assertThat(decision.nextAction()).isEqualTo(TaskAvailableAction.RUN_RENEWAL);
        assertThat(decision.availableActions()).containsExactly(TaskAvailableAction.RUN_RENEWAL);
    }

    @Test
    void ocrMissingFieldMustBeReviewedBeforeManualRenewal() {
        TaskActionDecision decision = resolver.resolve(result(
                TaskStatus.NEEDS_INFO,
                renewalExecution(
                        List.of("passport_number"),
                        List.of(Map.of("key", "passport_number", "source_hint", "DOCUMENT_OCR")),
                        List.of()
                ),
                List.of(completedChecklist()),
                List.of()
        ));

        assertThat(decision.nextAction()).isEqualTo(TaskAvailableAction.REVIEW_OCR);
        assertThat(decision.availableActions()).doesNotContain(TaskAvailableAction.RUN_RENEWAL);
        assertThat(decision.blockedReason()).isEqualTo("OCR_REVIEW_REQUIRED");
    }

    @Test
    void completedRenewalPreparationRequiresApprovalInsteadOfAnotherRun() {
        TaskActionDecision decision = resolver.resolve(result(
                TaskStatus.DRAFT,
                renewalExecution(
                        List.of(),
                        List.of(),
                        List.of(Map.of("stored_file_id", UUID.randomUUID().toString()))
                ),
                List.of(completedChecklist()),
                List.of()
        ));

        assertThat(decision.nextAction()).isEqualTo(TaskAvailableAction.REQUEST_APPROVAL);
        assertThat(decision.availableActions()).containsExactly(
                TaskAvailableAction.REVIEW_GENERATED_DOCUMENT,
                TaskAvailableAction.REQUEST_APPROVAL
        );
        assertThat(decision.availableActions()).doesNotContain(TaskAvailableAction.RUN_RENEWAL);
    }

    @Test
    void incompleteChecklistBlocksApproval() {
        TaskActionDecision decision = resolver.resolve(result(
                TaskStatus.NEEDS_INFO,
                renewalExecution(List.of(), List.of(), List.of()),
                List.of(incompleteChecklist()),
                List.of()
        ));

        assertThat(decision.nextAction()).isEqualTo(TaskAvailableAction.COMPLETE_CHECKLIST);
        assertThat(decision.blockedReason()).isEqualTo("CHECKLIST_INCOMPLETE");
    }

    @Test
    void terminalTaskHasNoAvailableAction() {
        TaskActionDecision decision = resolver.resolve(result(
                TaskStatus.COMPLETED,
                renewalExecution(List.of(), List.of(), List.of()),
                List.of(completedChecklist()),
                List.of()
        ));

        assertThat(decision.nextAction()).isNull();
        assertThat(decision.availableActions()).isEmpty();
        assertThat(decision.blockedReason()).isEqualTo("TASK_TERMINAL");
    }

    private TaskResult result(
            TaskStatus status,
            Map<String, Object> businessData,
            List<TaskChecklistItem> checklist,
            List<String> missingRequiredSlots
    ) {
        return new TaskResult(
                task(status),
                new TaskAssigneeView(ACTOR_ID, "테스트 담당자"),
                businessData,
                checklist,
                missingRequiredSlots
        );
    }

    private Task task(TaskStatus status) {
        return new Task(
                TASK_ID,
                COMPANY_ID,
                WORKER_ID,
                CASE_ID,
                TaskType.RECONTRACT,
                "WF-CON-001",
                "0.3.0",
                "재계약 준비",
                null,
                "{}",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                0,
                TaskSource.AI_CANDIDATE,
                status,
                LocalDate.of(2026, 8, 31),
                ACTOR_ID,
                ACTOR_ID,
                NOW,
                NOW,
                0
        );
    }

    private TaskChecklistItem incompleteChecklist() {
        return TaskChecklistItem.create(
                UUID.fromString("70000000-0000-0000-0000-000000000001"),
                TASK_ID,
                COMPANY_ID,
                "CHECK_CONTRACT",
                "계약 조건 확인",
                true,
                NOW
        );
    }

    private TaskChecklistItem completedChecklist() {
        TaskChecklistItem item = incompleteChecklist();
        item.updateCompletion(true, 0, ACTOR_ID, NOW.plusSeconds(1));
        return item;
    }

    private Map<String, Object> renewalExecution(
            List<String> missingSlots,
            List<Map<String, String>> requestedFields,
            List<Map<String, String>> generatedDocuments
    ) {
        return Map.of("renewal_execution", Map.of(
                "missing_slots", missingSlots,
                "requested_fields", requestedFields,
                "guide_review_required", false,
                "generated_documents", generatedDocuments
        ));
    }
}
