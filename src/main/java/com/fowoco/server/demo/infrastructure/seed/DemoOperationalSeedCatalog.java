package com.fowoco.server.demo.infrastructure.seed;

import com.fowoco.server.audit.domain.AuditAction;
import com.fowoco.server.task.domain.TaskSource;
import com.fowoco.server.task.domain.TaskStatus;
import com.fowoco.server.task.domain.TaskType;
import com.fowoco.server.worker.domain.DocumentType;
import com.fowoco.server.worker.domain.SubmissionStatus;
import java.util.List;
import java.util.UUID;

final class DemoOperationalSeedCatalog {

    static final String WORKFLOW_CATALOG_VERSION = "0.2.0";
    static final UUID TIMELINE_TASK_ID =
            UUID.fromString("94000000-0000-0000-0000-000000000002");

    private final List<TaskSeed> tasks = List.of(
            task(
                    "94000000-0000-0000-0000-000000000001",
                    "94100000-0000-0000-0000-000000000001",
                    "92000000-0000-0000-0000-000000000001",
                    TaskType.STAY_PERIOD_EXTENSION,
                    "WF-STY-001",
                    "AI 추천: 체류기간 연장 준비",
                    "체류기간 만료 전에 필요한 서류를 확인하고 연장 신청을 준비합니다.",
                    TaskSource.AI_CANDIDATE,
                    TaskStatus.DRAFT,
                    3,
                    6
            ),
            task(
                    "94000000-0000-0000-0000-000000000002",
                    "94100000-0000-0000-0000-000000000002",
                    "92000000-0000-0000-0000-000000000002",
                    TaskType.RECONTRACT,
                    "WF-CON-001",
                    "근로계약 갱신 검토",
                    "갱신 계약 조건과 제출 서류를 검토하고 승인을 기다립니다.",
                    TaskSource.MANUAL,
                    TaskStatus.READY_FOR_REVIEW,
                    20,
                    5
            ),
            task(
                    "94000000-0000-0000-0000-000000000003",
                    "94100000-0000-0000-0000-000000000003",
                    "92000000-0000-0000-0000-000000000003",
                    TaskType.STAY_PERIOD_EXTENSION,
                    "WF-STY-001",
                    "여권 사본 제출 대기",
                    "근로자에게 최신 여권 사본을 요청했으며 응답을 기다립니다.",
                    TaskSource.MANUAL,
                    TaskStatus.WAITING_WORKER,
                    6,
                    4
            ),
            task(
                    "94000000-0000-0000-0000-000000000004",
                    "94100000-0000-0000-0000-000000000004",
                    "92000000-0000-0000-0000-000000000004",
                    TaskType.EMPLOYMENT_PERIOD_EXTENSION,
                    "WF-CON-001",
                    "고용허가기간 연장 결과 대기",
                    "관할 기관에 제출을 완료하고 처리 결과를 기다립니다.",
                    TaskSource.MANUAL,
                    TaskStatus.WAITING_EXTERNAL,
                    90,
                    12
            ),
            task(
                    "94000000-0000-0000-0000-000000000005",
                    "94100000-0000-0000-0000-000000000005",
                    "92000000-0000-0000-0000-000000000005",
                    TaskType.RECONTRACT,
                    "WF-CON-001",
                    "근로계약 갱신 완료",
                    "서명된 근로계약서 확인과 갱신 처리를 완료했습니다.",
                    TaskSource.SYSTEM_DDAY,
                    TaskStatus.COMPLETED,
                    30,
                    10
            )
    );

    private final List<DocumentSeed> documents = List.of(
            document(
                    "95000000-0000-0000-0000-000000000001",
                    "92000000-0000-0000-0000-000000000001",
                    DocumentType.PASSPORT_COPY,
                    SubmissionStatus.MISSING,
                    25,
                    "체류기간 연장",
                    "만료 임박 여권 사본을 다시 받아야 합니다."
            ),
            document(
                    "95000000-0000-0000-0000-000000000002",
                    "92000000-0000-0000-0000-000000000001",
                    DocumentType.ARC,
                    SubmissionStatus.VERIFIED,
                    180,
                    "체류기간 연장",
                    "외국인등록증 사본 확인 완료"
            ),
            document(
                    "95000000-0000-0000-0000-000000000003",
                    "92000000-0000-0000-0000-000000000002",
                    DocumentType.CONTRACT,
                    SubmissionStatus.SUBMITTED,
                    90,
                    "근로계약 갱신",
                    "서명본 검토 대기"
            ),
            document(
                    "95000000-0000-0000-0000-000000000004",
                    "92000000-0000-0000-0000-000000000002",
                    DocumentType.PERMIT,
                    SubmissionStatus.VERIFIED,
                    150,
                    "고용허가기간 연장",
                    "고용허가서 확인 완료"
            ),
            document(
                    "95000000-0000-0000-0000-000000000005",
                    "92000000-0000-0000-0000-000000000003",
                    DocumentType.PASSPORT_COPY,
                    SubmissionStatus.VERIFIED,
                    300,
                    "체류기간 연장",
                    "유효한 여권 사본"
            ),
            document(
                    "95000000-0000-0000-0000-000000000006",
                    "92000000-0000-0000-0000-000000000004",
                    DocumentType.ARC,
                    SubmissionStatus.MISSING,
                    45,
                    "고용허가기간 연장",
                    "외국인등록증 사본 요청 필요"
            ),
            document(
                    "95000000-0000-0000-0000-000000000007",
                    "92000000-0000-0000-0000-000000000005",
                    DocumentType.CONTRACT,
                    SubmissionStatus.SUBMITTED,
                    120,
                    "근로계약 갱신",
                    "갱신 계약서 제출 완료"
            )
    );

    private final List<AuditSeed> audits = List.of(
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

    List<TaskSeed> tasks() {
        return tasks;
    }

    List<DocumentSeed> documents() {
        return documents;
    }

    List<AuditSeed> audits() {
        return audits;
    }

    private static TaskSeed task(
            String taskId,
            String caseId,
            String workerId,
            TaskType taskType,
            String workflowId,
            String title,
            String description,
            TaskSource source,
            TaskStatus status,
            int dueDays,
            int createdDaysAgo
    ) {
        return new TaskSeed(
                UUID.fromString(taskId),
                UUID.fromString(caseId),
                UUID.fromString(workerId),
                taskType,
                workflowId,
                title,
                description,
                source,
                status,
                dueDays,
                createdDaysAgo
        );
    }

    private static DocumentSeed document(
            String documentId,
            String workerId,
            DocumentType documentType,
            SubmissionStatus submissionStatus,
            int expiryDays,
            String destination,
            String note
    ) {
        return new DocumentSeed(
                UUID.fromString(documentId),
                UUID.fromString(workerId),
                documentType,
                submissionStatus,
                expiryDays,
                destination,
                note
        );
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
            int expiryDays,
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
