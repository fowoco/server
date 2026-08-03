package com.fowoco.server.demo.infrastructure.seed;

import com.fowoco.server.audit.domain.AuditAction;
import com.fowoco.server.approval.domain.ApprovalStatus;
import com.fowoco.server.approval.domain.EvidenceType;
import com.fowoco.server.task.domain.TaskSource;
import com.fowoco.server.task.domain.TaskStatus;
import com.fowoco.server.task.domain.TaskType;
import com.fowoco.server.worker.domain.DocumentType;
import com.fowoco.server.worker.domain.SubmissionStatus;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class DemoOperationalSeedCatalog {

    static final String WORKFLOW_CATALOG_VERSION = "0.2.0";
    static final UUID TIMELINE_TASK_ID =
            UUID.fromString("94000000-0000-0000-0000-000000000002");
    static final UUID TEST_ADMIN_USER_ID =
            UUID.fromString("91000000-0000-0000-0000-000000000002");

    private final List<TaskSeed> demoTasks = DemoTaskSeedCatalog.demoTasks();
    private final List<TaskSeed> testTasks = DemoTaskSeedCatalog.testTasks();
    private final List<DocumentSeed> demoDocuments = DemoDocumentSeedCatalog.demoDocuments();
    private final List<DocumentSeed> testDocuments = DemoDocumentSeedCatalog.testDocuments();
    private final List<ChecklistSeed> demoChecklists =
            DemoTaskWorkflowSeedCatalog.demoChecklists(demoTasks);
    private final List<ApprovalSeed> demoApprovals =
            DemoTaskWorkflowSeedCatalog.demoApprovals(demoTasks);
    private final List<TransitionSeed> demoTransitions =
            DemoTaskWorkflowSeedCatalog.demoTransitions(demoTasks);
    private final List<ExternalSubmissionSeed> demoExternalSubmissions =
            DemoTaskArtifactSeedCatalog.demoExternalSubmissions(demoTasks);
    private final List<EvidenceSeed> demoEvidence =
            DemoTaskArtifactSeedCatalog.demoEvidence(demoTasks);
    private final List<DocumentRequestDraftSeed> demoDocumentRequestDrafts =
            DemoTaskArtifactSeedCatalog.demoDocumentRequestDrafts(demoTasks);
    private final List<AuditSeed> demoAudits = List.of(
            audit(
                    "96000000-0000-0000-0000-000000000001",
                    AuditAction.TASK_CREATED,
                    "demo-seed-task-created",
                    "업무가 생성되었습니다.",
                    48
            ),
            audit(
                    "96000000-0000-0000-0000-000000000002",
                    AuditAction.TASK_UPDATED,
                    "demo-seed-task-updated",
                    "계약 갱신 정보와 마감일이 확인되었습니다.",
                    24
            ),
            audit(
                    "96000000-0000-0000-0000-000000000003",
                    AuditAction.APPROVAL_REQUESTED,
                    "demo-seed-approval-requested",
                    "관리자 승인을 요청했습니다.",
                    6
            )
    );

    DemoOperationalSeedCatalog() {
        verifyDefinitions();
    }

    List<TaskSeed> demoTasks() {
        return demoTasks;
    }

    List<TaskSeed> testTasks() {
        return testTasks;
    }

    List<DocumentSeed> demoDocuments() {
        return demoDocuments;
    }

    List<DocumentSeed> testDocuments() {
        return testDocuments;
    }

    List<ChecklistSeed> demoChecklists() {
        return demoChecklists;
    }

    List<ApprovalSeed> demoApprovals() {
        return demoApprovals;
    }

    List<TransitionSeed> demoTransitions() {
        return demoTransitions;
    }

    List<ExternalSubmissionSeed> demoExternalSubmissions() {
        return demoExternalSubmissions;
    }

    List<EvidenceSeed> demoEvidence() {
        return demoEvidence;
    }

    List<DocumentRequestDraftSeed> demoDocumentRequestDrafts() {
        return demoDocumentRequestDrafts;
    }

    List<AuditSeed> demoAudits() {
        return demoAudits;
    }

    private void verifyDefinitions() {
        requireSize(demoTasks, 24, "Demo Company task");
        requireSize(testTasks, 3, "Test Company task");
        requireSize(demoDocuments, 84, "Demo Company document");
        requireSize(testDocuments, 8, "Test Company document");
        requireSize(demoChecklists, 68, "Demo Company checklist item");
        requireSize(demoApprovals, 13, "Demo Company approval request");
        requireSize(demoTransitions, 52, "Demo Company task transition");
        requireSize(demoExternalSubmissions, 6, "Demo Company external submission");
        requireSize(demoEvidence, 10, "Demo Company completion evidence");
        requireSize(demoDocumentRequestDrafts, 5, "Demo Company document request draft");
        requireDistribution(
                demoTasks.stream().map(TaskSeed::taskType).toList(),
                Map.of(
                        TaskType.STAY_PERIOD_EXTENSION, 10L,
                        TaskType.RECONTRACT, 8L,
                        TaskType.EMPLOYMENT_PERIOD_EXTENSION, 6L
                ),
                "Demo Company task type"
        );
        requireDistribution(
                demoTasks.stream().map(TaskSeed::status).toList(),
                Map.of(
                        TaskStatus.DRAFT, 3L,
                        TaskStatus.NEEDS_INFO, 2L,
                        TaskStatus.READY_FOR_REVIEW, 4L,
                        TaskStatus.APPROVED, 2L,
                        TaskStatus.WAITING_WORKER, 4L,
                        TaskStatus.WAITING_EXTERNAL, 3L,
                        TaskStatus.COMPLETED, 5L,
                        TaskStatus.CANCELLED, 1L
                ),
                "Demo Company task status"
        );
        requireDistribution(
                demoDocuments.stream().map(DocumentSeed::submissionStatus).toList(),
                Map.of(
                        SubmissionStatus.VERIFIED, 48L,
                        SubmissionStatus.SUBMITTED, 20L,
                        SubmissionStatus.MISSING, 16L
                ),
                "Demo Company document status"
        );
        requireDistribution(
                demoApprovals.stream().map(ApprovalSeed::status).toList(),
                Map.of(
                        ApprovalStatus.PENDING, 4L,
                        ApprovalStatus.APPROVED, 7L,
                        ApprovalStatus.REJECTED, 1L,
                        ApprovalStatus.INVALIDATED, 1L
                ),
                "Demo Company approval status"
        );
        requireDistribution(
                demoEvidence.stream().map(EvidenceSeed::evidenceType).toList(),
                Map.of(
                        EvidenceType.DOCUMENT, 2L,
                        EvidenceType.RECEIPT, 3L,
                        EvidenceType.OFFICIAL_RESULT, 3L,
                        EvidenceType.HR_CONFIRMATION, 2L
                ),
                "Demo Company evidence type"
        );
        requireUniqueIds(
                Stream.concat(demoTasks.stream(), testTasks.stream()).map(TaskSeed::taskId).toList(),
                "task"
        );
        requireUniqueIds(
                Stream.concat(demoDocuments.stream(), testDocuments.stream())
                        .map(DocumentSeed::documentId)
                        .toList(),
                "document"
        );
        requireUniqueIds(demoChecklists.stream().map(ChecklistSeed::checklistItemId).toList(),
                "checklist item");
        requireUniqueIds(demoApprovals.stream().map(ApprovalSeed::approvalRequestId).toList(),
                "approval request");
        requireUniqueIds(demoTransitions.stream().map(TransitionSeed::transitionId).toList(),
                "task transition");
        requireUniqueIds(demoExternalSubmissions.stream()
                .map(ExternalSubmissionSeed::externalSubmissionId).toList(), "external submission");
        requireUniqueIds(demoEvidence.stream().map(EvidenceSeed::evidenceId).toList(),
                "completion evidence");
        requireUniqueIds(demoDocumentRequestDrafts.stream()
                .map(DocumentRequestDraftSeed::draftId).toList(), "document request draft");
        requireTaskReferences(demoChecklists.stream().map(ChecklistSeed::taskId).toList(),
                "checklist item");
        requireTaskReferences(demoApprovals.stream().map(ApprovalSeed::taskId).toList(),
                "approval request");
        requireTaskReferences(demoTransitions.stream().map(TransitionSeed::taskId).toList(),
                "task transition");
        requireTaskReferences(demoExternalSubmissions.stream()
                .map(ExternalSubmissionSeed::taskId).toList(), "external submission");
        requireTaskReferences(demoEvidence.stream().map(EvidenceSeed::taskId).toList(),
                "completion evidence");
        requireTaskReferences(demoDocumentRequestDrafts.stream()
                .map(DocumentRequestDraftSeed::taskId).toList(), "document request draft");
        verifyArtifactScenarios();
    }

    private static void requireSize(List<?> seeds, int expected, String name) {
        if (seeds.size() != expected) {
            throw new IllegalStateException(name + " seed count must be " + expected);
        }
    }

    private static <T> void requireDistribution(
            List<T> values,
            Map<T, Long> expected,
            String name
    ) {
        Map<T, Long> actual = values.stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        if (!expected.equals(actual)) {
            throw new IllegalStateException(name + " seed distribution is invalid: " + actual);
        }
    }

    private static void requireUniqueIds(List<UUID> ids, String name) {
        if (new HashSet<>(ids).size() != ids.size()) {
            throw new IllegalStateException("duplicate reserved demo " + name + " id");
        }
    }

    private void requireTaskReferences(List<UUID> taskIds, String name) {
        var reservedTaskIds = demoTasks.stream().map(TaskSeed::taskId).collect(Collectors.toSet());
        if (!reservedTaskIds.containsAll(taskIds)) {
            throw new IllegalStateException(name + " references an unknown demo task");
        }
    }

    private void verifyArtifactScenarios() {
        Map<UUID, TaskSeed> tasksById = demoTasks.stream()
                .collect(Collectors.toMap(TaskSeed::taskId, Function.identity()));
        boolean invalidSubmission = demoExternalSubmissions.stream()
                .map(ExternalSubmissionSeed::taskId)
                .map(tasksById::get)
                .anyMatch(task -> task.status() != TaskStatus.WAITING_EXTERNAL
                        && task.status() != TaskStatus.COMPLETED);
        if (invalidSubmission) {
            throw new IllegalStateException("external submission task status is invalid");
        }
        Map<UUID, Long> evidenceCounts = demoEvidence.stream()
                .collect(Collectors.groupingBy(EvidenceSeed::taskId, Collectors.counting()));
        boolean incompleteCompletionEvidence = demoTasks.stream()
                .filter(task -> task.status() == TaskStatus.COMPLETED)
                .anyMatch(task -> evidenceCounts.getOrDefault(task.taskId(), 0L) != 2L);
        if (incompleteCompletionEvidence || evidenceCounts.size() != 5) {
            throw new IllegalStateException("every completed demo task needs two evidence records");
        }
        var requestDraftTaskIds = demoDocumentRequestDrafts.stream()
                .map(DocumentRequestDraftSeed::taskId)
                .collect(Collectors.toSet());
        boolean missingWaitingWorkerDraft = demoTasks.stream()
                .filter(task -> task.status() == TaskStatus.WAITING_WORKER)
                .anyMatch(task -> !requestDraftTaskIds.contains(task.taskId()));
        boolean invalidDraftStatus = requestDraftTaskIds.stream()
                .map(tasksById::get)
                .anyMatch(task -> task.status() != TaskStatus.WAITING_WORKER
                        && task.status() != TaskStatus.DRAFT);
        if (missingWaitingWorkerDraft || invalidDraftStatus
                || requestDraftTaskIds.size() != demoDocumentRequestDrafts.size()) {
            throw new IllegalStateException("document request draft scenario is invalid");
        }
    }

    private static AuditSeed audit(
            String auditEventId,
            AuditAction action,
            String requestId,
            String changeSummary,
            int hoursAgo
    ) {
        return new AuditSeed(
                UUID.fromString(auditEventId),
                action,
                requestId,
                changeSummary,
                hoursAgo
        );
    }

    record TaskSeed(
            UUID taskId,
            UUID caseId,
            UUID workerId,
            TaskType taskType,
            String workflowId,
            String title,
            String description,
            TaskSource source,
            TaskStatus status,
            int dueDays,
            int createdDaysAgo
    ) {
    }

    record DocumentSeed(
            UUID documentId,
            UUID workerId,
            DocumentType documentType,
            SubmissionStatus submissionStatus,
            Integer expiryDays,
            String destination,
            String note
    ) {
    }

    record ChecklistSeed(
            UUID checklistItemId,
            UUID taskId,
            String itemCode,
            String label,
            boolean required,
            boolean completed,
            int createdHoursAgo,
            Integer completedHoursAgo
    ) {
    }

    record ApprovalSeed(
            UUID approvalRequestId,
            UUID taskId,
            ApprovalStatus status,
            String reason,
            int requestedHoursAgo,
            Integer outcomeHoursAgo
    ) {
    }

    record TransitionSeed(
            UUID transitionId,
            UUID taskId,
            TaskStatus fromStatus,
            TaskStatus toStatus,
            String reason,
            String requestId,
            int hoursAgo
    ) {
    }

    record ExternalSubmissionSeed(
            UUID externalSubmissionId,
            UUID taskId,
            String destination,
            String safeReference,
            int hoursAgo
    ) {
    }

    record EvidenceSeed(
            UUID evidenceId,
            UUID taskId,
            EvidenceType evidenceType,
            String note,
            int hoursAgo
    ) {
    }

    record DocumentRequestDraftSeed(
            UUID draftId,
            UUID taskId,
            String language,
            List<DocumentType> documentTypes,
            String message,
            int hoursAgo
    ) {
    }

    record AuditSeed(
            UUID auditEventId,
            AuditAction action,
            String requestId,
            String changeSummary,
            int hoursAgo
    ) {
    }
}
