package com.fowoco.server.demo.infrastructure.seed;

import com.fowoco.server.audit.application.port.AuditEventRepository;
import com.fowoco.server.audit.domain.ActorType;
import com.fowoco.server.audit.domain.AuditAction;
import com.fowoco.server.audit.domain.AuditEvent;
import com.fowoco.server.audit.domain.AuditTargetType;
import com.fowoco.server.auth.domain.UserRole;
import com.fowoco.server.auth.infrastructure.seed.DemoAuthSeedProperties;
import com.fowoco.server.task.application.TaskContentCodec;
import com.fowoco.server.task.application.TaskContentCodec.EncodedTaskContent;
import com.fowoco.server.task.application.port.TaskRepository;
import com.fowoco.server.task.domain.Task;
import com.fowoco.server.task.domain.TaskSource;
import com.fowoco.server.task.domain.TaskStatus;
import com.fowoco.server.task.domain.TaskType;
import com.fowoco.server.worker.application.port.WorkerDocumentRepository;
import com.fowoco.server.worker.domain.DocumentType;
import com.fowoco.server.worker.domain.SubmissionStatus;
import com.fowoco.server.worker.domain.WorkerDocument;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.annotation.Transactional;

@Order(2)
class DemoOperationalSeedRunner implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(DemoOperationalSeedRunner.class);
    private static final String CATALOG_VERSION = "0.2.0";
    private static final List<DemoTask> TASKS = List.of(
            demoTask(
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
            demoTask(
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
            demoTask(
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
            demoTask(
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
            demoTask(
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
    private static final List<DemoDocument> DOCUMENTS = List.of(
            demoDocument(
                    "95000000-0000-0000-0000-000000000001",
                    "92000000-0000-0000-0000-000000000001",
                    DocumentType.PASSPORT_COPY,
                    SubmissionStatus.MISSING,
                    25,
                    "체류기간 연장",
                    "만료 임박 여권 사본을 다시 받아야 합니다."
            ),
            demoDocument(
                    "95000000-0000-0000-0000-000000000002",
                    "92000000-0000-0000-0000-000000000001",
                    DocumentType.ARC,
                    SubmissionStatus.VERIFIED,
                    180,
                    "체류기간 연장",
                    "외국인등록증 사본 확인 완료"
            ),
            demoDocument(
                    "95000000-0000-0000-0000-000000000003",
                    "92000000-0000-0000-0000-000000000002",
                    DocumentType.CONTRACT,
                    SubmissionStatus.SUBMITTED,
                    90,
                    "근로계약 갱신",
                    "서명본 검토 대기"
            ),
            demoDocument(
                    "95000000-0000-0000-0000-000000000004",
                    "92000000-0000-0000-0000-000000000002",
                    DocumentType.PERMIT,
                    SubmissionStatus.VERIFIED,
                    150,
                    "고용허가기간 연장",
                    "고용허가서 확인 완료"
            ),
            demoDocument(
                    "95000000-0000-0000-0000-000000000005",
                    "92000000-0000-0000-0000-000000000003",
                    DocumentType.PASSPORT_COPY,
                    SubmissionStatus.VERIFIED,
                    300,
                    "체류기간 연장",
                    "유효한 여권 사본"
            ),
            demoDocument(
                    "95000000-0000-0000-0000-000000000006",
                    "92000000-0000-0000-0000-000000000004",
                    DocumentType.ARC,
                    SubmissionStatus.MISSING,
                    45,
                    "고용허가기간 연장",
                    "외국인등록증 사본 요청 필요"
            ),
            demoDocument(
                    "95000000-0000-0000-0000-000000000007",
                    "92000000-0000-0000-0000-000000000005",
                    DocumentType.CONTRACT,
                    SubmissionStatus.SUBMITTED,
                    120,
                    "근로계약 갱신",
                    "갱신 계약서 제출 완료"
            )
    );
    private static final UUID TIMELINE_TASK_ID =
            UUID.fromString("94000000-0000-0000-0000-000000000002");
    private static final List<DemoAudit> AUDITS = List.of(
            demoAudit(
                    "96000000-0000-0000-0000-000000000001",
                    AuditAction.TASK_CREATED,
                    "demo-seed-task-created",
                    "업무가 생성되었습니다.",
                    48
            ),
            demoAudit(
                    "96000000-0000-0000-0000-000000000002",
                    AuditAction.TASK_UPDATED,
                    "demo-seed-task-updated",
                    "계약 갱신 정보와 마감일이 확인되었습니다.",
                    24
            ),
            demoAudit(
                    "96000000-0000-0000-0000-000000000003",
                    AuditAction.APPROVAL_REQUESTED,
                    "demo-seed-approval-requested",
                    "관리자 승인을 요청했습니다.",
                    6
            )
    );

    private final DemoAuthSeedProperties properties;
    private final TaskRepository taskRepository;
    private final TaskContentCodec taskContentCodec;
    private final WorkerDocumentRepository workerDocumentRepository;
    private final AuditEventRepository auditEventRepository;
    private final Clock clock;

    DemoOperationalSeedRunner(
            DemoAuthSeedProperties properties,
            TaskRepository taskRepository,
            TaskContentCodec taskContentCodec,
            WorkerDocumentRepository workerDocumentRepository,
            AuditEventRepository auditEventRepository,
            Clock clock
    ) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.taskRepository = Objects.requireNonNull(taskRepository, "taskRepository must not be null");
        this.taskContentCodec = Objects.requireNonNull(taskContentCodec, "taskContentCodec must not be null");
        this.workerDocumentRepository = Objects.requireNonNull(
                workerDocumentRepository,
                "workerDocumentRepository must not be null"
        );
        this.auditEventRepository = Objects.requireNonNull(
                auditEventRepository,
                "auditEventRepository must not be null"
        );
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        Instant now = clock.instant();
        LocalDate today = LocalDate.now(clock);
        TASKS.forEach(task -> seedTask(task, today, now));
        DOCUMENTS.forEach(document -> seedDocument(document, today, now));
        AUDITS.forEach(audit -> seedAudit(audit, now));
        LOGGER.info(
                "demo_operational_seed ready company_id={} task_count={} document_count={} audit_count={}",
                properties.companyId(),
                TASKS.size(),
                DOCUMENTS.size(),
                AUDITS.size()
        );
    }

    private void seedTask(DemoTask seed, LocalDate today, Instant now) {
        LocalDate dueDate = today.plusDays(seed.dueDays());
        EncodedTaskContent content = taskContentCodec.encode(
                seed.workerId(),
                seed.workflowId(),
                seed.taskType().name(),
                seed.title(),
                seed.description(),
                dueDate,
                Map.of("worker_id", seed.workerId().toString(), "due_at", dueDate.toString())
        );
        Optional<Task> existing = taskRepository.findByIdAndCompanyId(
                seed.taskId(),
                properties.companyId()
        );
        existing.ifPresent(task -> verifyExistingTask(task, seed));
        Instant createdAt = existing.map(Task::createdAt)
                .orElseGet(() -> now.minus(seed.createdDaysAgo(), ChronoUnit.DAYS));
        Instant desiredUpdatedAt = seed.status() == TaskStatus.COMPLETED
                ? now
                : now.minus(Math.max(seed.createdDaysAgo() - 1, 0), ChronoUnit.DAYS);
        Instant updatedAt = desiredUpdatedAt.isBefore(createdAt) ? createdAt : desiredUpdatedAt;
        long version = existing.map(Task::version).orElse(0L);
        taskRepository.save(new Task(
                seed.taskId(),
                properties.companyId(),
                seed.workerId(),
                seed.caseId(),
                seed.taskType(),
                seed.workflowId(),
                CATALOG_VERSION,
                seed.title(),
                seed.description(),
                content.businessDataJson(),
                content.criticalFingerprint(),
                0L,
                seed.source(),
                seed.status(),
                dueDate,
                properties.adminUserId(),
                properties.adminUserId(),
                createdAt,
                updatedAt,
                version
        ));
    }

    private void verifyExistingTask(Task task, DemoTask seed) {
        if (!seed.taskId().equals(task.taskId())
                || !properties.companyId().equals(task.companyId())
                || !seed.workerId().equals(task.workerId())
                || !seed.caseId().equals(task.caseId())
                || seed.taskType() != task.taskType()
                || !seed.workflowId().equals(task.workflowId())
                || !CATALOG_VERSION.equals(task.workflowCatalogVersion())
                || !seed.title().equals(task.title())
                || !seed.description().equals(task.description())
                || seed.source() != task.source()
                || seed.status() != task.status()
                || task.contentRevision() != 0L
                || !properties.adminUserId().equals(task.createdBy())
                || !properties.adminUserId().equals(task.updatedBy())) {
            throw new IllegalStateException(
                    "a reserved demo task id already belongs to different task data"
            );
        }
    }

    private void seedDocument(DemoDocument seed, LocalDate today, Instant now) {
        LocalDate expiryDate = today.plusDays(seed.expiryDays());
        Optional<WorkerDocument> existing =
                workerDocumentRepository.findByIdAndWorkerIdAndCompanyId(
                        seed.documentId(),
                        seed.workerId(),
                        properties.companyId()
                );
        existing.ifPresent(document -> verifyExistingDocument(document, seed));
        if (existing.isEmpty()) {
            workerDocumentRepository.insert(WorkerDocument.create(
                    seed.documentId(),
                    seed.workerId(),
                    properties.companyId(),
                    null,
                    seed.documentType(),
                    seed.submissionStatus(),
                    expiryDate,
                    seed.destination(),
                    seed.note(),
                    now
            ));
            return;
        }
        WorkerDocument document = existing.orElseThrow();
        workerDocumentRepository.update(new WorkerDocument(
                document.workerDocumentId(),
                document.workerId(),
                document.companyId(),
                document.taskId(),
                document.documentType(),
                document.submissionStatus(),
                expiryDate,
                document.destination(),
                document.note(),
                null,
                document.createdAt(),
                now,
                document.version()
        ));
    }

    private void verifyExistingDocument(WorkerDocument document, DemoDocument seed) {
        if (!seed.documentId().equals(document.workerDocumentId())
                || !seed.workerId().equals(document.workerId())
                || !properties.companyId().equals(document.companyId())
                || seed.documentType() != document.documentType()
                || seed.submissionStatus() != document.submissionStatus()
                || !Objects.equals(seed.destination(), document.destination())
                || !Objects.equals(seed.note(), document.note())
                || document.fileId() != null) {
            throw new IllegalStateException(
                    "a reserved demo worker document id already belongs to different document data"
            );
        }
    }

    private void seedAudit(DemoAudit seed, Instant now) {
        Optional<AuditEvent> existing = auditEventRepository
                .findTaskActivities(properties.companyId(), TIMELINE_TASK_ID)
                .stream()
                .filter(event -> seed.auditEventId().equals(event.auditEventId()))
                .findFirst();
        if (existing.isPresent()) {
            verifyExistingAudit(existing.orElseThrow(), seed);
            return;
        }
        auditEventRepository.append(new AuditEvent(
                seed.auditEventId(),
                properties.companyId(),
                ActorType.HR_USER,
                properties.adminUserId(),
                UserRole.ADMIN,
                seed.action(),
                AuditTargetType.TASK,
                TIMELINE_TASK_ID,
                seed.requestId(),
                "demo-seed-task-timeline",
                "1",
                seed.changeSummary(),
                now.minus(seed.hoursAgo(), ChronoUnit.HOURS)
        ));
    }

    private void verifyExistingAudit(AuditEvent event, DemoAudit seed) {
        if (!properties.companyId().equals(event.companyId())
                || event.actorType() != ActorType.HR_USER
                || !properties.adminUserId().equals(event.actorId())
                || event.userRole() != UserRole.ADMIN
                || event.action() != seed.action()
                || event.targetType() != AuditTargetType.TASK
                || !TIMELINE_TASK_ID.equals(event.targetId())
                || !seed.requestId().equals(event.requestId())
                || !seed.changeSummary().equals(event.changeSummary())) {
            throw new IllegalStateException(
                    "a reserved demo audit event id already belongs to different audit data"
            );
        }
    }

    private static DemoTask demoTask(
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
        return new DemoTask(
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

    private static DemoDocument demoDocument(
            String documentId,
            String workerId,
            DocumentType documentType,
            SubmissionStatus submissionStatus,
            int expiryDays,
            String destination,
            String note
    ) {
        return new DemoDocument(
                UUID.fromString(documentId),
                UUID.fromString(workerId),
                documentType,
                submissionStatus,
                expiryDays,
                destination,
                note
        );
    }

    private static DemoAudit demoAudit(
            String auditEventId,
            AuditAction action,
            String requestId,
            String changeSummary,
            int hoursAgo
    ) {
        return new DemoAudit(
                UUID.fromString(auditEventId),
                action,
                requestId,
                changeSummary,
                hoursAgo
        );
    }

    private record DemoTask(
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

    private record DemoDocument(
            UUID documentId,
            UUID workerId,
            DocumentType documentType,
            SubmissionStatus submissionStatus,
            int expiryDays,
            String destination,
            String note
    ) {
    }

    private record DemoAudit(
            UUID auditEventId,
            AuditAction action,
            String requestId,
            String changeSummary,
            int hoursAgo
    ) {
    }
}
