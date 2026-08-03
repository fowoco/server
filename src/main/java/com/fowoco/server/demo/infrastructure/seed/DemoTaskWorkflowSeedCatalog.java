package com.fowoco.server.demo.infrastructure.seed;

import com.fowoco.server.approval.domain.ApprovalStatus;
import com.fowoco.server.demo.infrastructure.seed.DemoOperationalSeedCatalog.ApprovalSeed;
import com.fowoco.server.demo.infrastructure.seed.DemoOperationalSeedCatalog.ChecklistSeed;
import com.fowoco.server.demo.infrastructure.seed.DemoOperationalSeedCatalog.TaskSeed;
import com.fowoco.server.demo.infrastructure.seed.DemoOperationalSeedCatalog.TransitionSeed;
import com.fowoco.server.task.domain.TaskStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
                        completed ? Math.max(createdHoursAgo / 2, 1) : null
                ));
            }
        }
        return List.copyOf(seeds);
    }

    static List<ApprovalSeed> demoApprovals(List<TaskSeed> tasks) {
        return List.of(
                approval(1, tasks, 2, ApprovalStatus.PENDING, null, 72, null),
                approval(2, tasks, 6, ApprovalStatus.PENDING, null, 48, null),
                approval(3, tasks, 14, ApprovalStatus.PENDING, null, 36, null),
                approval(4, tasks, 15, ApprovalStatus.PENDING, null, 24, null),
                approval(5, tasks, 16, ApprovalStatus.APPROVED, "연장 조건과 제출 자료 확인", 120, 96),
                approval(6, tasks, 17, ApprovalStatus.APPROVED, "체류 연장 신청 내용 승인", 144, 120),
                approval(7, tasks, 5, ApprovalStatus.APPROVED, "서명 계약서와 갱신 조건 확인", 216, 192),
                approval(8, tasks, 20, ApprovalStatus.APPROVED, "체류 연장 완료 건 승인", 360, 336),
                approval(9, tasks, 21, ApprovalStatus.APPROVED, "재계약 완료 건 승인", 480, 456),
                approval(10, tasks, 22, ApprovalStatus.APPROVED, "고용기간 연장 완료 건 승인", 600, 576),
                approval(11, tasks, 23, ApprovalStatus.APPROVED, "신청 결과 확인 후 승인", 720, 696),
                approval(12, tasks, 9, ApprovalStatus.REJECTED, "필수 고용 정보 보완 필요", 96, 72),
                approval(13, tasks, 1, ApprovalStatus.INVALIDATED, "중요 신청 정보 변경으로 기존 검토 무효화", 120, 96)
        );
    }

    static List<TransitionSeed> demoTransitions(List<TaskSeed> tasks) {
        List<TransitionSeed> seeds = new ArrayList<>();
        history(seeds, tasks.get(0), TaskStatus.DRAFT, TaskStatus.READY_FOR_REVIEW, TaskStatus.DRAFT);
        history(seeds, tasks.get(1), TaskStatus.DRAFT, TaskStatus.READY_FOR_REVIEW);
        history(seeds, tasks.get(2), TaskStatus.DRAFT, TaskStatus.READY_FOR_REVIEW,
                TaskStatus.APPROVED, TaskStatus.WAITING_WORKER);
        history(seeds, tasks.get(3), TaskStatus.DRAFT, TaskStatus.READY_FOR_REVIEW,
                TaskStatus.APPROVED, TaskStatus.WAITING_EXTERNAL);
        history(seeds, tasks.get(4), TaskStatus.DRAFT, TaskStatus.READY_FOR_REVIEW,
                TaskStatus.APPROVED, TaskStatus.COMPLETED);
        history(seeds, tasks.get(5), TaskStatus.DRAFT, TaskStatus.READY_FOR_REVIEW);
        history(seeds, tasks.get(7), TaskStatus.DRAFT, TaskStatus.READY_FOR_REVIEW,
                TaskStatus.APPROVED, TaskStatus.WAITING_WORKER);
        history(seeds, tasks.get(8), TaskStatus.DRAFT, TaskStatus.READY_FOR_REVIEW,
                TaskStatus.DRAFT, TaskStatus.NEEDS_INFO);
        history(seeds, tasks.get(9), TaskStatus.DRAFT, TaskStatus.READY_FOR_REVIEW,
                TaskStatus.APPROVED, TaskStatus.WAITING_WORKER);
        history(seeds, tasks.get(10), TaskStatus.DRAFT, TaskStatus.READY_FOR_REVIEW,
                TaskStatus.APPROVED, TaskStatus.WAITING_WORKER);
        history(seeds, tasks.get(12), TaskStatus.DRAFT, TaskStatus.NEEDS_INFO);
        history(seeds, tasks.get(13), TaskStatus.DRAFT, TaskStatus.READY_FOR_REVIEW);
        history(seeds, tasks.get(14), TaskStatus.DRAFT, TaskStatus.READY_FOR_REVIEW);
        history(seeds, tasks.get(15), TaskStatus.DRAFT, TaskStatus.READY_FOR_REVIEW, TaskStatus.APPROVED);
        history(seeds, tasks.get(16), TaskStatus.DRAFT, TaskStatus.READY_FOR_REVIEW, TaskStatus.APPROVED);
        history(seeds, tasks.get(17), TaskStatus.DRAFT, TaskStatus.READY_FOR_REVIEW,
                TaskStatus.APPROVED, TaskStatus.WAITING_EXTERNAL);
        history(seeds, tasks.get(18), TaskStatus.DRAFT, TaskStatus.READY_FOR_REVIEW,
                TaskStatus.APPROVED, TaskStatus.WAITING_EXTERNAL);
        history(seeds, tasks.get(19), TaskStatus.DRAFT, TaskStatus.READY_FOR_REVIEW,
                TaskStatus.APPROVED, TaskStatus.WAITING_EXTERNAL, TaskStatus.COMPLETED);
        history(seeds, tasks.get(20), TaskStatus.DRAFT, TaskStatus.READY_FOR_REVIEW,
                TaskStatus.APPROVED, TaskStatus.COMPLETED);
        history(seeds, tasks.get(21), TaskStatus.DRAFT, TaskStatus.READY_FOR_REVIEW,
                TaskStatus.APPROVED, TaskStatus.COMPLETED);
        history(seeds, tasks.get(22), TaskStatus.DRAFT, TaskStatus.READY_FOR_REVIEW,
                TaskStatus.APPROVED, TaskStatus.COMPLETED);
        history(seeds, tasks.get(23), TaskStatus.DRAFT, TaskStatus.CANCELLED);
        return List.copyOf(seeds);
    }

    private static ApprovalSeed approval(
            int approvalNumber,
            List<TaskSeed> tasks,
            int taskNumber,
            ApprovalStatus status,
            String reason,
            int requestedHoursAgo,
            Integer outcomeHoursAgo
    ) {
        return new ApprovalSeed(
                demoUuid("94300000-0000-0000-0000-000000000", approvalNumber),
                tasks.get(taskNumber - 1).taskId(),
                status,
                reason,
                requestedHoursAgo,
                outcomeHoursAgo
        );
    }

    private static void history(
            List<TransitionSeed> seeds,
            TaskSeed task,
            TaskStatus initialStatus,
            TaskStatus... statuses
    ) {
        TaskStatus fromStatus = initialStatus;
        int transitionCount = statuses.length;
        int createdHoursAgo = Math.max(task.createdDaysAgo() * 24, transitionCount);
        int finalHoursAgo = task.status() == TaskStatus.COMPLETED
                ? 0
                : Math.max(task.createdDaysAgo() - 1, 0) * 24;
        int transitionWindow = createdHoursAgo - finalHoursAgo;
        for (int index = 0; index < transitionCount; index++) {
            TaskStatus toStatus = statuses[index];
            int sequence = seeds.size() + 1;
            int hoursAgo = createdHoursAgo
                    - ((index + 1) * transitionWindow / transitionCount);
            seeds.add(new TransitionSeed(
                    demoUuid("94400000-0000-0000-0000-000000000", sequence),
                    task.taskId(),
                    fromStatus,
                    toStatus,
                    transitionReason(toStatus),
                    "demo-seed-task-transition-%03d".formatted(sequence),
                    hoursAgo
            ));
            fromStatus = toStatus;
        }
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
}
