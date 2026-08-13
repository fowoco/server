package com.fowoco.server.demo.infrastructure.seed;

import com.fowoco.server.approval.domain.ApprovalStatus;
import com.fowoco.server.demo.infrastructure.seed.DemoOperationalSeedCatalog.ApprovalSeed;
import com.fowoco.server.demo.infrastructure.seed.DemoOperationalSeedCatalog.ChecklistSeed;
import com.fowoco.server.demo.infrastructure.seed.DemoOperationalSeedCatalog.TaskSeed;
import com.fowoco.server.demo.infrastructure.seed.DemoOperationalSeedCatalog.TransitionSeed;
import com.fowoco.server.task.domain.TaskStatus;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class DemoTaskWorkflowSeedCatalog {

    private static final List<ChecklistTemplate> STAY_CHECKLIST = List.of(
            new ChecklistTemplate("PASSPORT_COPY_VERIFY_CURRENT", "여권 사본 보유·유효 상태 확인"),
            new ChecklistTemplate("ALIEN_REGISTRATION_CARD_VERIFY_CURRENT", "외국인등록증 사본 보유·유효 상태 확인"),
            new ChecklistTemplate("EMPLOYMENT_CONTRACT_VERIFY_PERIOD", "근로계약기간 확인"),
            new ChecklistTemplate("APPLICATION_FORM_SELECT_CURRENT", "최신 공식 통합신청서 양식 확인")
    );
    private static final List<ChecklistTemplate> CONTRACT_CHECKLIST = List.of(
            new ChecklistTemplate("EMPLOYMENT_CONTRACT_USE_CURRENT_STANDARD_FORM", "최신 표준근로계약서 양식 사용 확인"),
            new ChecklistTemplate("EMPLOYMENT_PERMIT_VERIFY_PERIOD", "고용허가기간과 연장 적용 여부 확인")
    );
    private static final Map<Integer, Integer> INCOMPLETE_ITEMS_BY_TASK = Map.of(
            7, 2,
            9, 1,
            12, 1,
            13, 1
    );
    private static final Set<Integer> APPROVAL_BACKFILL_TASKS = Set.of(3, 8, 10, 11);

    private DemoTaskWorkflowSeedCatalog() {
    }

    static List<ChecklistSeed> demoChecklists(List<TaskSeed> tasks) {
        List<ChecklistSeed> seeds = new ArrayList<>();
        for (int taskIndex = 0; taskIndex < tasks.size(); taskIndex++) {
            TaskSeed task = tasks.get(taskIndex);
            List<ChecklistTemplate> templates = "WF-STY-001".equals(task.workflowId())
                    ? STAY_CHECKLIST
                    : CONTRACT_CHECKLIST;
            int incompleteCount = INCOMPLETE_ITEMS_BY_TASK.getOrDefault(taskIndex + 1, 0);
            int completedHoursAgo = completedHoursAgo(taskIndex + 1, task);
            for (int itemIndex = 0; itemIndex < templates.size(); itemIndex++) {
                ChecklistTemplate template = templates.get(itemIndex);
                boolean completed = itemIndex < templates.size() - incompleteCount;
                int createdHoursAgo = Math.max(task.createdDaysAgo() * 24, 24);
                seeds.add(new ChecklistSeed(
                        demoUuid("94200000-0000-0000-0000-000000000", seeds.size() + 1),
                        task.taskId(),
                        template.itemCode(),
                        template.label(),
                        true,
                        completed,
                        createdHoursAgo,
                        completed ? completedHoursAgo : null
                ));
            }
        }
        return List.copyOf(seeds);
    }

    static List<ApprovalSeed> demoApprovals(List<TaskSeed> tasks) {
        return List.of(
                approval(1, tasks, 2, ApprovalStatus.PENDING, null),
                approval(2, tasks, 6, ApprovalStatus.PENDING, null),
                approval(3, tasks, 14, ApprovalStatus.PENDING, null),
                approval(4, tasks, 15, ApprovalStatus.PENDING, null),
                approval(5, tasks, 16, ApprovalStatus.APPROVED, "연장 조건과 제출 자료 확인"),
                approval(6, tasks, 17, ApprovalStatus.APPROVED, "체류 연장 신청 내용 승인"),
                approval(7, tasks, 5, ApprovalStatus.APPROVED, "서명 계약서와 갱신 조건 확인"),
                approval(8, tasks, 20, ApprovalStatus.APPROVED, "체류 연장 완료 건 승인"),
                approval(9, tasks, 21, ApprovalStatus.APPROVED, "재계약 완료 건 승인"),
                approval(10, tasks, 22, ApprovalStatus.APPROVED, "고용기간 연장 완료 건 승인"),
                approval(11, tasks, 23, ApprovalStatus.APPROVED, "신청 결과 확인 후 승인"),
                approval(12, tasks, 9, ApprovalStatus.REJECTED, "필수 고용 정보 보완 필요"),
                approval(13, tasks, 1, ApprovalStatus.INVALIDATED,
                        "마감일 변경으로 기존 승인 snapshot을 무효화"),
                approval(14, tasks, 3, ApprovalStatus.APPROVED, "여권 사본 요청 전 승인 확인"),
                approval(15, tasks, 10, ApprovalStatus.APPROVED, "체류서류 요청 전 승인 확인"),
                approval(16, tasks, 11, ApprovalStatus.APPROVED, "외국인등록증 요청 전 승인 확인")
        );
    }

    static List<TransitionSeed> demoTransitions(List<TaskSeed> tasks) {
        List<TransitionSeed> seeds = new ArrayList<>();
        TransitionSequence sequence = new TransitionSequence(legacyTransitionCount(tasks) + 1);
        for (int taskIndex = 0; taskIndex < tasks.size(); taskIndex++) {
            int taskNumber = taskIndex + 1;
            history(seeds, taskNumber, tasks.get(taskIndex), transitionPath(taskNumber), sequence);
        }
        return List.copyOf(seeds);
    }

    private static ApprovalSeed approval(
            int approvalNumber,
            List<TaskSeed> tasks,
            int taskNumber,
            ApprovalStatus status,
            String reason
    ) {
        TaskSeed task = tasks.get(taskNumber - 1);
        List<TransitionPoint> points = transitionPoints(taskNumber, task);
        int readyHoursAgo = hoursAgo(points, TaskStatus.READY_FOR_REVIEW);
        int requestedHoursAgo = readyHoursAgo - 1;
        Integer outcomeHoursAgo = switch (status) {
            case PENDING -> null;
            case APPROVED -> hoursAgo(points, TaskStatus.APPROVED) + 1;
            case REJECTED, INVALIDATED -> hoursAgoAfter(
                    points,
                    TaskStatus.DRAFT,
                    TaskStatus.READY_FOR_REVIEW
            ) + 1;
        };
        if (outcomeHoursAgo != null && requestedHoursAgo < outcomeHoursAgo) {
            throw new IllegalStateException("demo approval timeline is invalid");
        }
        ApprovalSnapshot snapshot = approvalSnapshot(taskNumber, task, status, reason);
        return new ApprovalSeed(
                demoUuid("94300000-0000-0000-0000-000000000", approvalNumber),
                task.taskId(),
                status,
                reason,
                requestedHoursAgo,
                outcomeHoursAgo,
                snapshot.ai(),
                snapshot.hr(),
                snapshot.changedFields(),
                snapshot.sourceVersions()
        );
    }

    private static ApprovalSnapshot approvalSnapshot(
            int taskNumber,
            TaskSeed task,
            ApprovalStatus status,
            String reason
    ) {
        Map<String, Object> ai = new LinkedHashMap<>();
        ai.put("summary", task.title());
        ai.put("task_type", task.taskType().name());
        ai.put("workflow_id", task.workflowId());
        ai.put("due_offset_days", task.dueDays());
        ai.put("proposed_status", TaskStatus.READY_FOR_REVIEW.name());

        Map<String, Object> hr = new LinkedHashMap<>();
        hr.put("summary", task.title());
        hr.put("review_result", status.name());
        hr.put("approval_required", true);
        if (reason != null) {
            hr.put("review_note", reason);
        }

        List<Map<String, Object>> changedFields = List.of();
        if (taskNumber == 6) {
            ai.put("case_display_name", "재계약·연장 준비");
            ai.put("input_summary", Map.of("required_count", 9, "available_count", 7));
            hr.put("confirmed_fields", List.of("continued_employment_intent"));
            hr.put("warning_fields", List.of("passport_expiry_date"));
            hr.put("validation_summary", Map.of(
                    "key_fields_valid", 7,
                    "key_fields_total", 7,
                    "error_count", 0,
                    "warning_count", 1
            ));
            changedFields = List.of(Map.of(
                    "field", "continued_employment_intent",
                    "before", "NEEDS_CONFIRMATION",
                    "after", "CONFIRMED",
                    "source", "HR_INPUT"
            ));
        } else if (taskNumber == 9) {
            hr.put("missing_fields", List.of("employment_period", "submission_destination"));
        } else if (taskNumber == 1) {
            hr.put("invalidation_trigger", "DUE_DATE_CHANGED");
            changedFields = List.of(Map.of(
                    "field", "due_at",
                    "before", "D+3",
                    "after", "D+7",
                    "source", "HR_INPUT"
            ));
        }

        return new ApprovalSnapshot(
                Map.copyOf(ai),
                Map.copyOf(hr),
                changedFields,
                Map.of(
                        "workflow_catalog", DemoOperationalSeedCatalog.WORKFLOW_CATALOG_VERSION,
                        "workflow_id", task.workflowId(),
                        "task_version", 0,
                        "content_revision", 0,
                        "worker_record", "demo-seed-v1",
                        "document_records", "demo-seed-v1"
                )
        );
    }

    private static void history(
            List<TransitionSeed> seeds,
            int taskNumber,
            TaskSeed task,
            List<TaskStatus> statuses,
            TransitionSequence sequence
    ) {
        TaskStatus fromStatus = TaskStatus.DRAFT;
        List<TransitionPoint> points = transitionPoints(task, statuses);
        for (TransitionPoint point : points) {
            TaskStatus toStatus = point.status();
            int transitionNumber = sequence.next(isApprovalBackfill(taskNumber, toStatus));
            seeds.add(new TransitionSeed(
                    demoUuid("94400000-0000-0000-0000-000000000", transitionNumber),
                    task.taskId(),
                    fromStatus,
                    toStatus,
                    transitionReason(toStatus),
                    "demo-seed-task-transition-%03d".formatted(transitionNumber),
                    point.hoursAgo()
            ));
            fromStatus = toStatus;
        }
    }

    private static int legacyTransitionCount(List<TaskSeed> tasks) {
        int currentCount = 0;
        for (int taskNumber = 1; taskNumber <= tasks.size(); taskNumber++) {
            currentCount += transitionPath(taskNumber).size();
            if (APPROVAL_BACKFILL_TASKS.contains(taskNumber)) {
                currentCount -= 2;
            }
        }
        return currentCount;
    }

    private static boolean isApprovalBackfill(int taskNumber, TaskStatus status) {
        return APPROVAL_BACKFILL_TASKS.contains(taskNumber)
                && (status == TaskStatus.READY_FOR_REVIEW || status == TaskStatus.APPROVED);
    }

    private static int completedHoursAgo(int taskNumber, TaskSeed task) {
        List<TransitionPoint> points = transitionPoints(taskNumber, task);
        return points.stream()
                .filter(point -> point.status() == TaskStatus.READY_FOR_REVIEW)
                .findFirst()
                .map(point -> Math.min(task.createdDaysAgo() * 24, point.hoursAgo() + 1))
                .orElseGet(() -> Math.max(task.createdDaysAgo() * 12, 1));
    }

    private static List<TransitionPoint> transitionPoints(int taskNumber, TaskSeed task) {
        return transitionPoints(task, transitionPath(taskNumber));
    }

    private static List<TransitionPoint> transitionPoints(TaskSeed task, List<TaskStatus> statuses) {
        int transitionCount = statuses.size();
        if (transitionCount == 0) {
            return List.of();
        }
        int createdHoursAgo = Math.max(task.createdDaysAgo() * 24, transitionCount);
        int finalHoursAgo = task.status() == TaskStatus.COMPLETED
                ? 0
                : Math.max(task.createdDaysAgo() - 1, 0) * 24;
        int transitionWindow = createdHoursAgo - finalHoursAgo;
        List<TransitionPoint> points = new ArrayList<>(transitionCount);
        for (int index = 0; index < transitionCount; index++) {
            points.add(new TransitionPoint(
                    statuses.get(index),
                    createdHoursAgo - ((index + 1) * transitionWindow / transitionCount)
            ));
        }
        return List.copyOf(points);
    }

    private static int hoursAgo(List<TransitionPoint> points, TaskStatus status) {
        return points.stream()
                .filter(point -> point.status() == status)
                .findFirst()
                .map(TransitionPoint::hoursAgo)
                .orElseThrow(() -> new IllegalStateException(
                        "demo approval requires a " + status + " transition"
                ));
    }

    private static int hoursAgoAfter(
            List<TransitionPoint> points,
            TaskStatus status,
            TaskStatus precedingStatus
    ) {
        boolean precedingFound = false;
        for (TransitionPoint point : points) {
            if (point.status() == precedingStatus) {
                precedingFound = true;
            } else if (precedingFound && point.status() == status) {
                return point.hoursAgo();
            }
        }
        throw new IllegalStateException("demo approval outcome transition is missing");
    }

    private static List<TaskStatus> transitionPath(int taskNumber) {
        return switch (taskNumber) {
            case 1 -> List.of(TaskStatus.READY_FOR_REVIEW, TaskStatus.DRAFT);
            case 2, 6, 14, 15 -> List.of(TaskStatus.READY_FOR_REVIEW);
            case 3, 8, 10, 11 -> List.of(
                    TaskStatus.NEEDS_INFO,
                    TaskStatus.DRAFT,
                    TaskStatus.READY_FOR_REVIEW,
                    TaskStatus.APPROVED,
                    TaskStatus.WAITING_WORKER
            );
            case 4, 18, 19 -> List.of(
                    TaskStatus.NEEDS_INFO,
                    TaskStatus.WAITING_WORKER,
                    TaskStatus.WAITING_EXTERNAL
            );
            case 5, 21, 22, 23 -> List.of(
                    TaskStatus.READY_FOR_REVIEW,
                    TaskStatus.APPROVED,
                    TaskStatus.COMPLETED
            );
            case 7, 12 -> List.of();
            case 9 -> List.of(
                    TaskStatus.READY_FOR_REVIEW,
                    TaskStatus.DRAFT,
                    TaskStatus.NEEDS_INFO
            );
            case 13 -> List.of(TaskStatus.NEEDS_INFO);
            case 16, 17 -> List.of(TaskStatus.READY_FOR_REVIEW, TaskStatus.APPROVED);
            case 20 -> List.of(
                    TaskStatus.READY_FOR_REVIEW,
                    TaskStatus.APPROVED,
                    TaskStatus.WAITING_EXTERNAL,
                    TaskStatus.COMPLETED
            );
            case 24 -> List.of(TaskStatus.CANCELLED);
            default -> throw new IllegalArgumentException("unknown demo task number: " + taskNumber);
        };
    }

    private static String transitionReason(TaskStatus status) {
        return switch (status) {
            case NEEDS_INFO -> "필수 정보 또는 체크리스트 보완 필요";
            case READY_FOR_REVIEW -> "필수 준비 항목 확인 완료";
            case APPROVED -> "관리자 검토 승인";
            case WAITING_WORKER -> "근로자 제출 자료 대기";
            case WAITING_EXTERNAL -> "외부 기관 처리 결과 대기";
            case COMPLETED -> "필수 승인과 완료 근거 확인";
            case CANCELLED -> "계약 일정 변경으로 업무 취소";
            case DRAFT -> "검토 결과를 반영하기 위해 초안으로 복귀";
        };
    }

    private static UUID demoUuid(String prefix, int number) {
        return UUID.fromString(prefix + "%03d".formatted(number));
    }

    private record ChecklistTemplate(String itemCode, String label) {
    }

    private static final class TransitionSequence {

        private int legacySequence = 1;
        private int extensionSequence;

        private TransitionSequence(int extensionSequence) {
            this.extensionSequence = extensionSequence;
        }

        private int next(boolean extension) {
            if (extension) {
                return extensionSequence++;
            }
            return legacySequence++;
        }
    }

    private record TransitionPoint(TaskStatus status, int hoursAgo) {
    }

    private record ApprovalSnapshot(
            Map<String, Object> ai,
            Map<String, Object> hr,
            List<Map<String, Object>> changedFields,
            Map<String, Object> sourceVersions
    ) {
    }
}
