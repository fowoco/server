package com.fowoco.server.demo.infrastructure.seed;

import com.fowoco.server.audit.domain.AuditAction;
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

    List<AuditSeed> demoAudits() {
        return demoAudits;
    }

    private void verifyDefinitions() {
        requireSize(demoTasks, 24, "Demo Company task");
        requireSize(testTasks, 3, "Test Company task");
        requireSize(demoDocuments, 84, "Demo Company document");
        requireSize(testDocuments, 8, "Test Company document");
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

    record AuditSeed(
            UUID auditEventId,
            AuditAction action,
            String requestId,
            String changeSummary,
            int hoursAgo
    ) {
    }
}
