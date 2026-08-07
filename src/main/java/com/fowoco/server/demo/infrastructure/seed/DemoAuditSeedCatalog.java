package com.fowoco.server.demo.infrastructure.seed;

import com.fowoco.server.approval.domain.ApprovalStatus;
import com.fowoco.server.audit.domain.ActorType;
import com.fowoco.server.audit.domain.AuditAction;
import com.fowoco.server.audit.domain.AuditTargetType;
import com.fowoco.server.auth.domain.UserRole;
import com.fowoco.server.demo.infrastructure.seed.DemoOperationalSeedCatalog.ApprovalSeed;
import com.fowoco.server.demo.infrastructure.seed.DemoOperationalSeedCatalog.AuditSeed;
import com.fowoco.server.demo.infrastructure.seed.DemoOperationalSeedCatalog.DocumentRequestDraftSeed;
import com.fowoco.server.demo.infrastructure.seed.DemoOperationalSeedCatalog.EvidenceSeed;
import com.fowoco.server.demo.infrastructure.seed.DemoOperationalSeedCatalog.ExternalSubmissionSeed;
import com.fowoco.server.demo.infrastructure.seed.DemoOperationalSeedCatalog.TaskSeed;
import com.fowoco.server.task.domain.TaskSource;
import com.fowoco.server.task.domain.TaskStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class DemoAuditSeedCatalog {

    private static final List<Integer> CHECKLIST_EVENT_TASKS = List.of(
            3, 4, 5, 9, 10, 11, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22
    );

    private DemoAuditSeedCatalog() {
    }

    static List<AuditSeed> additionalDemoAudits(
            List<TaskSeed> tasks,
            List<ApprovalSeed> approvals,
            List<ExternalSubmissionSeed> submissions,
            List<EvidenceSeed> evidence,
            List<DocumentRequestDraftSeed> requestDrafts
    ) {
        List<AuditSeed> seeds = new ArrayList<>();
        for (int taskIndex = 0; taskIndex < tasks.size(); taskIndex++) {
            int taskNumber = taskIndex + 1;
            if (taskNumber != 2 && taskNumber != 6 && taskNumber != 7 && taskNumber != 8) {
                TaskSeed task = tasks.get(taskIndex);
                add(seeds, actorType(task.source()), AuditAction.TASK_CREATED,
                        AuditTargetType.TASK, task.taskId(),
                        "업무 시나리오가 생성됨", task.createdDaysAgo() * 24);
            }
        }
        addCompoundDraftTrace(seeds, tasks);
        CHECKLIST_EVENT_TASKS.forEach(taskNumber -> {
            TaskSeed task = tasks.get(taskNumber - 1);
            add(seeds, ActorType.HR_USER, AuditAction.CHECKLIST_ITEM_UPDATED,
                    AuditTargetType.TASK, task.taskId(),
                    "필수 체크리스트 준비 상태를 확인함",
                    Math.max((task.createdDaysAgo() - 1) * 24 + 2, 2));
        });
        for (int index = 0; index < approvals.size(); index++) {
            ApprovalSeed approval = approvals.get(index);
            if (index != 0) {
                add(seeds, ActorType.HR_USER, AuditAction.APPROVAL_REQUESTED,
                        AuditTargetType.TASK, approval.taskId(),
                        "현재 업무 내용에 대한 승인 검토를 요청함",
                        approval.requestedHoursAgo());
            }
            if (approval.status() != ApprovalStatus.PENDING) {
                add(seeds, ActorType.HR_USER, approvalAction(approval.status()),
                        AuditTargetType.TASK, approval.taskId(), approvalSummary(approval.status()),
                        approval.outcomeHoursAgo());
            }
        }
        submissions.forEach(submission -> add(
                seeds,
                ActorType.HR_USER,
                AuditAction.EXTERNAL_SUBMISSION_RECORDED,
                AuditTargetType.TASK,
                submission.taskId(),
                "외부 기관 제출과 안전한 접수 참조값을 기록함",
                submission.hoursAgo()
        ));
        evidence.forEach(item -> add(
                seeds,
                ActorType.HR_USER,
                AuditAction.EVIDENCE_RECORDED,
                AuditTargetType.TASK,
                item.taskId(),
                "업무 완료 판단에 필요한 안전한 증빙 메모를 기록함",
                item.hoursAgo()
        ));
        requestDrafts.forEach(draft -> add(
                seeds,
                ActorType.HR_USER,
                AuditAction.DOCUMENT_REQUEST_DRAFT_SAVED,
                AuditTargetType.DOCUMENT_REQUEST_DRAFT,
                draft.draftId(),
                "근로자 안내용 문서 요청 초안을 저장함",
                draft.hoursAgo()
        ));
        tasks.stream()
                .filter(task -> task.status() == TaskStatus.COMPLETED)
                .forEach(task -> add(seeds, ActorType.HR_USER, AuditAction.TASK_COMPLETED,
                        AuditTargetType.TASK, task.taskId(),
                        "승인과 완료 증빙 확인 후 업무를 완료함", 0));
        tasks.stream()
                .filter(task -> task.status() == TaskStatus.CANCELLED)
                .forEach(task -> add(seeds, ActorType.HR_USER, AuditAction.TASK_CANCELLED,
                        AuditTargetType.TASK, task.taskId(),
                        "계약 일정 변경 사유를 확인하고 업무를 취소함",
                        Math.max((task.createdDaysAgo() - 1) * 24, 1)));
        for (DocumentRequestDraftSeed draft : requestDrafts.stream().limit(4).toList()) {
            add(seeds, ActorType.WORKER_LINK, AuditAction.TASK_UPDATED,
                    AuditTargetType.TASK, draft.taskId(),
                    "근로자 제출 문서 정보가 업무 준비 자료에 반영됨",
                    Math.max(draft.hoursAgo() - 12, 1));
        }
        return List.copyOf(seeds);
    }

    private static void addCompoundDraftTrace(List<AuditSeed> seeds, List<TaskSeed> tasks) {
        String traceId = "demo-compound-draft-flow";
        TaskSeed recontract = tasks.get(5);
        TaskSeed employmentExtension = tasks.get(6);
        TaskSeed passportRequest = tasks.get(7);
        addWithTrace(seeds, AuditAction.TASK_CREATED, recontract.taskId(),
                traceId, "복합 요청의 대상 근로자와 Case 맥락을 확인함", 72);
        addWithTrace(seeds, AuditAction.TASK_UPDATED, recontract.taskId(),
                traceId, "재계약 Workflow와 필수 정보를 확인함", 60);
        addWithTrace(seeds, AuditAction.TASK_UPDATED, passportRequest.taskId(),
                traceId, "보유 문서를 비교하고 여권 사본 누락을 확인함", 48);
        addWithTrace(seeds, AuditAction.TASK_UPDATED, passportRequest.taskId(),
                traceId, "베트남어 문서 요청 초안을 준비함", 40);
        addWithTrace(seeds, AuditAction.TASK_CREATED, employmentExtension.taskId(),
                traceId, "선행 재계약 결과를 기다리는 연장 후보를 준비함", 36);
    }

    static List<AuditSeed> testAudits(List<TaskSeed> tasks) {
        List<AuditSeed> seeds = new ArrayList<>();
        tasks.forEach(task -> addTest(seeds, actorType(task.source()), AuditAction.TASK_CREATED,
                task.taskId(), "격리 확인용 테스트 업무가 생성됨", task.createdDaysAgo() * 24));
        tasks.forEach(task -> addTest(seeds, ActorType.HR_USER, AuditAction.CHECKLIST_ITEM_UPDATED,
                task.taskId(), "테스트 업무의 준비 상태를 확인함",
                Math.max((task.createdDaysAgo() - 1) * 24, 1)));
        addTest(seeds, ActorType.HR_USER, AuditAction.APPROVAL_REQUESTED,
                tasks.get(1).taskId(), "테스트 재계약 업무 승인을 요청함", 24);
        addTest(seeds, ActorType.HR_USER, AuditAction.TASK_COMPLETED,
                tasks.get(2).taskId(), "테스트 고용기간 연장 업무를 완료함", 0);
        return List.copyOf(seeds);
    }

    private static void add(
            List<AuditSeed> seeds,
            ActorType actorType,
            AuditAction action,
            AuditTargetType targetType,
            UUID targetId,
            String summary,
            int hoursAgo
    ) {
        int sequence = seeds.size() + 4;
        seeds.add(audit(
                demoUuid("96000000-0000-0000-0000-000000000", sequence),
                actorType,
                action,
                targetType,
                targetId,
                "demo-seed-audit-%03d".formatted(sequence),
                "demo-task-%s".formatted(targetId.toString().substring(24)),
                summary,
                hoursAgo
        ));
    }

    private static void addTest(
            List<AuditSeed> seeds,
            ActorType actorType,
            AuditAction action,
            UUID targetId,
            String summary,
            int hoursAgo
    ) {
        int sequence = seeds.size() + 1;
        seeds.add(audit(
                demoUuid("99000000-0000-0000-0000-000000000", sequence),
                actorType,
                action,
                AuditTargetType.TASK,
                targetId,
                "test-seed-audit-%03d".formatted(sequence),
                "test-task-%s".formatted(targetId.toString().substring(24)),
                summary,
                hoursAgo
        ));
    }

    private static void addWithTrace(
            List<AuditSeed> seeds,
            AuditAction action,
            UUID targetId,
            String traceId,
            String summary,
            int hoursAgo
    ) {
        int sequence = seeds.size() + 4;
        seeds.add(audit(
                demoUuid("96000000-0000-0000-0000-000000000", sequence),
                ActorType.AI_AGENT,
                action,
                AuditTargetType.TASK,
                targetId,
                "demo-seed-audit-%03d".formatted(sequence),
                traceId,
                summary,
                hoursAgo
        ));
    }

    private static AuditSeed audit(
            UUID auditEventId,
            ActorType actorType,
            AuditAction action,
            AuditTargetType targetType,
            UUID targetId,
            String requestId,
            String traceId,
            String summary,
            int hoursAgo
    ) {
        return new AuditSeed(
                auditEventId,
                actorType,
                actorType == ActorType.HR_USER ? UserRole.ADMIN : null,
                action,
                targetType,
                targetId,
                requestId,
                traceId,
                summary,
                hoursAgo
        );
    }

    private static ActorType actorType(TaskSource source) {
        return switch (source) {
            case AI_CANDIDATE -> ActorType.AI_AGENT;
            case SYSTEM_DDAY -> ActorType.SYSTEM_RULE;
            case MANUAL, FILE_IMPORT -> ActorType.HR_USER;
            case WORKER_RESPONSE -> ActorType.WORKER_LINK;
        };
    }

    private static AuditAction approvalAction(ApprovalStatus status) {
        return switch (status) {
            case APPROVED -> AuditAction.TASK_APPROVED;
            case REJECTED -> AuditAction.TASK_REJECTED;
            case INVALIDATED -> AuditAction.APPROVAL_INVALIDATED;
            case PENDING -> throw new IllegalArgumentException("pending approval has no outcome action");
        };
    }

    private static String approvalSummary(ApprovalStatus status) {
        return switch (status) {
            case APPROVED -> "관리자가 현재 업무 내용을 승인함";
            case REJECTED -> "필수 정보 보완을 위해 승인 요청을 반려함";
            case INVALIDATED -> "검토 중인 승인 요청을 무효화함";
            case PENDING -> throw new IllegalArgumentException("pending approval has no outcome summary");
        };
    }

    private static UUID demoUuid(String prefix, int number) {
        return UUID.fromString(prefix + "%03d".formatted(number));
    }
}
