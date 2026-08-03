package com.fowoco.server.demo.infrastructure.seed;

import com.fowoco.server.demo.infrastructure.seed.DemoOperationalSeedCatalog.TaskSeed;
import com.fowoco.server.task.domain.TaskSource;
import com.fowoco.server.task.domain.TaskStatus;
import com.fowoco.server.task.domain.TaskType;
import java.util.List;
import java.util.UUID;

final class DemoTaskSeedCatalog {

    private DemoTaskSeedCatalog() {
    }

    static List<TaskSeed> demoTasks() {
        return List.of(
                task(1, 1, TaskType.STAY_PERIOD_EXTENSION, "AI 추천: 체류기간 연장 준비",
                        "체류기간 만료 전에 필요한 서류를 확인하고 연장 신청을 준비합니다.",
                        TaskSource.AI_CANDIDATE, TaskStatus.DRAFT, 3, 6),
                task(2, 2, TaskType.RECONTRACT, "근로계약 갱신 검토",
                        "갱신 계약 조건과 제출 서류를 검토하고 승인을 기다립니다.",
                        TaskSource.MANUAL, TaskStatus.READY_FOR_REVIEW, 20, 5),
                task(3, 3, TaskType.STAY_PERIOD_EXTENSION, "여권 사본 제출 대기",
                        "근로자에게 최신 여권 사본을 요청했으며 응답을 기다립니다.",
                        TaskSource.MANUAL, TaskStatus.WAITING_WORKER, 6, 4),
                task(4, 4, TaskType.EMPLOYMENT_PERIOD_EXTENSION, "고용허가기간 연장 결과 대기",
                        "관할 기관에 제출을 완료하고 처리 결과를 기다립니다.",
                        TaskSource.MANUAL, TaskStatus.WAITING_EXTERNAL, 90, 12),
                task(5, 5, TaskType.RECONTRACT, "근로계약 갱신 완료",
                        "서명된 근로계약서 확인과 갱신 처리를 완료했습니다.",
                        TaskSource.SYSTEM_DDAY, TaskStatus.COMPLETED, 30, 10),
                task(6, 6, TaskType.RECONTRACT, "응웬반A 재계약 검토",
                        "재계약 조건과 체류기간 연장 일정을 함께 검토합니다.",
                        TaskSource.MANUAL, TaskStatus.READY_FOR_REVIEW, 12, 3),
                taskWithCase(7, 6, 6, TaskType.STAY_PERIOD_EXTENSION, "체류연장 업무 초안",
                        "체류기간 연장 신청을 위한 AI 초안을 확인합니다.",
                        TaskSource.AI_CANDIDATE, TaskStatus.DRAFT, 0, 2),
                taskWithCase(8, 6, 6, TaskType.STAY_PERIOD_EXTENSION, "여권 만료일 확인 대기",
                        "최신 여권 사본과 만료일 확인을 기다립니다.",
                        TaskSource.MANUAL, TaskStatus.WAITING_WORKER, 7, 2),
                task(9, 8, TaskType.EMPLOYMENT_PERIOD_EXTENSION, "고용기간 연장 정보 보완",
                        "연장 신청에 필요한 고용 정보와 제출 서류를 보완합니다.",
                        TaskSource.SYSTEM_DDAY, TaskStatus.NEEDS_INFO, 4, 5),
                task(10, 9, TaskType.STAY_PERIOD_EXTENSION, "체류서류 응답 대기",
                        "근로자에게 요청한 체류 관련 서류의 응답을 기다립니다.",
                        TaskSource.MANUAL, TaskStatus.WAITING_WORKER, 7, 4),
                task(11, 11, TaskType.STAY_PERIOD_EXTENSION, "외국인등록증 사본 대기",
                        "체류연장 검토를 위해 외국인등록증 사본을 기다립니다.",
                        TaskSource.MANUAL, TaskStatus.WAITING_WORKER, 12, 6),
                task(12, 10, TaskType.RECONTRACT, "재계약 서류 준비",
                        "계약 만료 전에 재계약 서류와 조건을 준비합니다.",
                        TaskSource.AI_CANDIDATE, TaskStatus.DRAFT, 20, 7),
                task(13, 7, TaskType.EMPLOYMENT_PERIOD_EXTENSION, "고용기간 연장 자료 보완",
                        "지원 가능한 고용기간 연장 업무의 누락 정보를 확인합니다.",
                        TaskSource.MANUAL, TaskStatus.NEEDS_INFO, 21, 8),
                task(14, 12, TaskType.STAY_PERIOD_EXTENSION, "체류기간 연장 검토",
                        "제출 준비가 완료된 체류기간 연장 내용을 검토합니다.",
                        TaskSource.SYSTEM_DDAY, TaskStatus.READY_FOR_REVIEW, 7, 3),
                task(15, 13, TaskType.RECONTRACT, "재계약 조건 최종 검토",
                        "근로조건과 계약기간을 확인하고 최종 검토합니다.",
                        TaskSource.MANUAL, TaskStatus.READY_FOR_REVIEW, 21, 9),
                task(16, 14, TaskType.EMPLOYMENT_PERIOD_EXTENSION, "고용기간 연장 승인",
                        "고용기간 연장 신청 내용이 승인되어 제출을 준비합니다.",
                        TaskSource.MANUAL, TaskStatus.APPROVED, 35, 10),
                task(17, 15, TaskType.STAY_PERIOD_EXTENSION, "체류기간 연장 승인",
                        "체류기간 연장 신청 내용이 승인되었습니다.",
                        TaskSource.SYSTEM_DDAY, TaskStatus.APPROVED, 62, 11),
                task(18, 16, TaskType.EMPLOYMENT_PERIOD_EXTENSION, "고용기간 연장 심사 대기",
                        "외부기관에 제출한 고용기간 연장 결과를 기다립니다.",
                        TaskSource.MANUAL, TaskStatus.WAITING_EXTERNAL, 90, 14),
                task(19, 17, TaskType.RECONTRACT, "재계약 신고 결과 대기",
                        "재계약 관련 외부 신고 처리 결과를 기다립니다.",
                        TaskSource.MANUAL, TaskStatus.WAITING_EXTERNAL, 120, 16),
                task(20, 18, TaskType.STAY_PERIOD_EXTENSION, "체류기간 연장 완료",
                        "체류기간 연장 결과와 완료 자료를 확인했습니다.",
                        TaskSource.SYSTEM_DDAY, TaskStatus.COMPLETED, 0, 20),
                task(21, 19, TaskType.RECONTRACT, "재계약 처리 완료",
                        "서명 계약서와 재계약 처리 결과를 확인했습니다.",
                        TaskSource.MANUAL, TaskStatus.COMPLETED, 35, 25),
                task(22, 20, TaskType.EMPLOYMENT_PERIOD_EXTENSION, "고용기간 연장 완료",
                        "고용기간 연장 승인 결과를 확인하고 업무를 완료했습니다.",
                        TaskSource.SYSTEM_DDAY, TaskStatus.COMPLETED, 62, 30),
                task(23, 21, TaskType.STAY_PERIOD_EXTENSION, "체류기간 연장 처리 완료",
                        "체류기간 연장 신청과 결과 확인을 완료했습니다.",
                        TaskSource.MANUAL, TaskStatus.COMPLETED, 90, 35),
                task(24, 22, TaskType.RECONTRACT, "재계약 검토 취소",
                        "근로계약 종료 일정 변경으로 재계약 검토를 취소했습니다.",
                        TaskSource.MANUAL, TaskStatus.CANCELLED, 21, 12)
        );
    }

    static List<TaskSeed> testTasks() {
        return List.of(
                testTask(1, 1, TaskType.STAY_PERIOD_EXTENSION, "테스트 체류연장 초안",
                        TaskSource.AI_CANDIDATE, TaskStatus.DRAFT, 7, 2),
                testTask(2, 2, TaskType.RECONTRACT, "테스트 재계약 검토",
                        TaskSource.MANUAL, TaskStatus.READY_FOR_REVIEW, 30, 5),
                testTask(3, 3, TaskType.EMPLOYMENT_PERIOD_EXTENSION, "테스트 고용기간 연장 완료",
                        TaskSource.SYSTEM_DDAY, TaskStatus.COMPLETED, 60, 10)
        );
    }

    private static TaskSeed task(
            int taskNumber,
            int workerNumber,
            TaskType taskType,
            String title,
            String description,
            TaskSource source,
            TaskStatus status,
            int dueDays,
            int createdDaysAgo
    ) {
        return taskWithCase(
                taskNumber,
                taskNumber,
                workerNumber,
                taskType,
                title,
                description,
                source,
                status,
                dueDays,
                createdDaysAgo
        );
    }

    private static TaskSeed taskWithCase(
            int taskNumber,
            int caseNumber,
            int workerNumber,
            TaskType taskType,
            String title,
            String description,
            TaskSource source,
            TaskStatus status,
            int dueDays,
            int createdDaysAgo
    ) {
        return new TaskSeed(
                demoUuid("94000000-0000-0000-0000-000000000", taskNumber),
                demoUuid("94100000-0000-0000-0000-000000000", caseNumber),
                demoUuid("92000000-0000-0000-0000-000000000", workerNumber),
                taskType,
                workflowId(taskType),
                title,
                description,
                source,
                status,
                dueDays,
                createdDaysAgo
        );
    }

    private static TaskSeed testTask(
            int taskNumber,
            int workerNumber,
            TaskType taskType,
            String title,
            TaskSource source,
            TaskStatus status,
            int dueDays,
            int createdDaysAgo
    ) {
        return new TaskSeed(
                demoUuid("97000000-0000-0000-0000-000000000", taskNumber),
                demoUuid("97100000-0000-0000-0000-000000000", taskNumber),
                demoUuid("93000000-0000-0000-0000-000000000", workerNumber),
                taskType,
                workflowId(taskType),
                title,
                "Tenant 격리 확인을 위한 소규모 테스트 업무입니다.",
                source,
                status,
                dueDays,
                createdDaysAgo
        );
    }

    private static String workflowId(TaskType taskType) {
        return taskType == TaskType.STAY_PERIOD_EXTENSION ? "WF-STY-001" : "WF-CON-001";
    }

    private static UUID demoUuid(String prefix, int number) {
        return UUID.fromString(prefix + "%03d".formatted(number));
    }
}
