package com.fowoco.server.demo.infrastructure.seed;

import com.fowoco.server.audit.domain.AuditEvent;
import com.fowoco.server.approval.application.port.ApprovalRequestRepository;
import com.fowoco.server.approval.domain.ApprovalRequest;
import com.fowoco.server.demo.infrastructure.seed.DemoOperationalSeedCatalog.AuditSeed;
import com.fowoco.server.demo.infrastructure.seed.DemoOperationalSeedCatalog.ApprovalSeed;
import com.fowoco.server.demo.infrastructure.seed.DemoOperationalSeedCatalog.ChecklistSeed;
import com.fowoco.server.demo.infrastructure.seed.DemoOperationalSeedCatalog.DocumentSeed;
import com.fowoco.server.demo.infrastructure.seed.DemoOperationalSeedCatalog.DocumentRequestDraftSeed;
import com.fowoco.server.demo.infrastructure.seed.DemoOperationalSeedCatalog.EvidenceSeed;
import com.fowoco.server.demo.infrastructure.seed.DemoOperationalSeedCatalog.ExternalSubmissionSeed;
import com.fowoco.server.demo.infrastructure.seed.DemoOperationalSeedCatalog.TaskSeed;
import com.fowoco.server.demo.infrastructure.seed.DemoOperationalSeedCatalog.TransitionSeed;
import com.fowoco.server.task.application.port.TaskChecklistRepository;
import com.fowoco.server.task.application.port.TaskRepository;
import com.fowoco.server.task.domain.Task;
import com.fowoco.server.task.domain.TaskChecklistItem;
import com.fowoco.server.worker.application.port.WorkerDocumentRepository;
import com.fowoco.server.worker.application.port.WorkerRepository;
import com.fowoco.server.worker.domain.WorkerDocument;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

final class DemoOperationalSeedVerifier {

    private final TaskRepository taskRepository;
    private final WorkerDocumentRepository workerDocumentRepository;
    private final WorkerRepository workerRepository;
    private final TaskChecklistRepository checklistRepository;
    private final ApprovalRequestRepository approvalRepository;
    private final DemoTaskSeeder taskSeeder;
    private final DemoWorkerDocumentSeeder documentSeeder;
    private final DemoTaskChecklistSeeder checklistSeeder;
    private final DemoApprovalRequestSeeder approvalSeeder;
    private final DemoTaskTransitionSeeder transitionSeeder;
    private final DemoExternalSubmissionSeeder externalSubmissionSeeder;
    private final DemoEvidenceSeeder evidenceSeeder;
    private final DemoDocumentRequestDraftSeeder requestDraftSeeder;
    private final DemoAuditEventSeeder auditSeeder;

    DemoOperationalSeedVerifier(
            TaskRepository taskRepository,
            WorkerDocumentRepository workerDocumentRepository,
            WorkerRepository workerRepository,
            TaskChecklistRepository checklistRepository,
            ApprovalRequestRepository approvalRepository,
            DemoTaskSeeder taskSeeder,
            DemoWorkerDocumentSeeder documentSeeder,
            DemoTaskChecklistSeeder checklistSeeder,
            DemoApprovalRequestSeeder approvalSeeder,
            DemoTaskTransitionSeeder transitionSeeder,
            DemoExternalSubmissionSeeder externalSubmissionSeeder,
            DemoEvidenceSeeder evidenceSeeder,
            DemoDocumentRequestDraftSeeder requestDraftSeeder,
            DemoAuditEventSeeder auditSeeder
    ) {
        this.taskRepository = Objects.requireNonNull(taskRepository, "taskRepository must not be null");
        this.workerDocumentRepository = Objects.requireNonNull(
                workerDocumentRepository,
                "workerDocumentRepository must not be null"
        );
        this.workerRepository = Objects.requireNonNull(workerRepository, "workerRepository must not be null");
        this.checklistRepository = Objects.requireNonNull(
                checklistRepository,
                "checklistRepository must not be null"
        );
        this.approvalRepository = Objects.requireNonNull(
                approvalRepository,
                "approvalRepository must not be null"
        );
        this.taskSeeder = Objects.requireNonNull(taskSeeder, "taskSeeder must not be null");
        this.documentSeeder = Objects.requireNonNull(documentSeeder, "documentSeeder must not be null");
        this.checklistSeeder = Objects.requireNonNull(checklistSeeder, "checklistSeeder must not be null");
        this.approvalSeeder = Objects.requireNonNull(approvalSeeder, "approvalSeeder must not be null");
        this.transitionSeeder = Objects.requireNonNull(
                transitionSeeder,
                "transitionSeeder must not be null"
        );
        this.externalSubmissionSeeder = Objects.requireNonNull(
                externalSubmissionSeeder,
                "externalSubmissionSeeder must not be null"
        );
        this.evidenceSeeder = Objects.requireNonNull(evidenceSeeder, "evidenceSeeder must not be null");
        this.requestDraftSeeder = Objects.requireNonNull(
                requestDraftSeeder,
                "requestDraftSeeder must not be null"
        );
        this.auditSeeder = Objects.requireNonNull(auditSeeder, "auditSeeder must not be null");
    }

    void verify(
            List<TaskSeed> tasks,
            List<DocumentSeed> documents,
            List<ChecklistSeed> checklists,
            List<ApprovalSeed> approvals,
            List<TransitionSeed> transitions,
            List<ExternalSubmissionSeed> externalSubmissions,
            List<EvidenceSeed> evidence,
            List<DocumentRequestDraftSeed> requestDrafts,
            List<AuditSeed> audits,
            DemoOperationalSeedContext context
    ) {
        verifyUniqueIds(tasks.stream().map(TaskSeed::taskId).toList(), "task");
        verifyUniqueIds(documents.stream().map(DocumentSeed::documentId).toList(), "document");
        verifyUniqueIds(checklists.stream().map(ChecklistSeed::checklistItemId).toList(), "checklist item");
        verifyUniqueIds(approvals.stream().map(ApprovalSeed::approvalRequestId).toList(), "approval request");
        verifyUniqueIds(transitions.stream().map(TransitionSeed::transitionId).toList(), "task transition");
        verifyUniqueIds(externalSubmissions.stream()
                .map(ExternalSubmissionSeed::externalSubmissionId).toList(), "external submission");
        verifyUniqueIds(evidence.stream().map(EvidenceSeed::evidenceId).toList(), "completion evidence");
        verifyUniqueIds(requestDrafts.stream().map(DocumentRequestDraftSeed::draftId).toList(),
                "document request draft");
        verifyUniqueIds(audits.stream().map(AuditSeed::auditEventId).toList(), "audit event");
        verifyWorkersExist(tasks, documents, context);
        tasks.forEach(seed -> verifyTask(seed, context));
        documents.forEach(seed -> verifyDocument(seed, context));
        checklists.forEach(seed -> verifyChecklist(seed, context));
        approvals.forEach(seed -> verifyApproval(seed, context));
        transitions.forEach(seed -> transitionSeeder.verifyExisting(seed, context));
        externalSubmissions.forEach(seed -> externalSubmissionSeeder.verifyExisting(seed, context));
        evidence.forEach(seed -> evidenceSeeder.verifyExisting(seed, context));
        requestDrafts.forEach(seed -> requestDraftSeeder.verifyExisting(seed, context));
        audits.forEach(seed -> verifyAudit(seed, context));
    }

    private void verifyTask(TaskSeed seed, DemoOperationalSeedContext context) {
        Task task = taskRepository.findByIdAndCompanyId(seed.taskId(), context.companyId())
                .orElseThrow(() -> new IllegalStateException("a reserved demo task was not seeded"));
        taskSeeder.verifyExisting(task, seed, context);
    }

    private void verifyDocument(DocumentSeed seed, DemoOperationalSeedContext context) {
        WorkerDocument document = workerDocumentRepository.findByIdAndWorkerIdAndCompanyId(
                        seed.documentId(),
                        seed.workerId(),
                        context.companyId()
                )
                .orElseThrow(() -> new IllegalStateException("a reserved demo worker document was not seeded"));
        documentSeeder.verifyExisting(document, seed, context);
    }

    private void verifyChecklist(ChecklistSeed seed, DemoOperationalSeedContext context) {
        TaskChecklistItem item = checklistRepository.findByIdAndTaskIdAndCompanyId(
                        seed.checklistItemId(),
                        seed.taskId(),
                        context.companyId()
                )
                .orElseThrow(() -> new IllegalStateException("a reserved demo checklist item was not seeded"));
        checklistSeeder.verifyExisting(item, seed, context);
    }

    private void verifyApproval(ApprovalSeed seed, DemoOperationalSeedContext context) {
        ApprovalRequest approval = approvalRepository.findByIdAndCompanyId(
                        seed.approvalRequestId(),
                        context.companyId()
                )
                .orElseThrow(() -> new IllegalStateException("a reserved demo approval request was not seeded"));
        Task task = taskRepository.findByIdAndCompanyId(seed.taskId(), context.companyId())
                .orElseThrow(() -> new IllegalStateException("a demo approval task does not exist"));
        approvalSeeder.verifyExisting(approval, seed, context, task);
    }

    private void verifyAudit(AuditSeed seed, DemoOperationalSeedContext context) {
        AuditEvent event = auditSeeder.findExisting(seed)
                .orElseThrow(() -> new IllegalStateException("a reserved demo audit event was not seeded"));
        auditSeeder.verifyExisting(event, seed, context);
    }

    private void verifyWorkersExist(
            List<TaskSeed> tasks,
            List<DocumentSeed> documents,
            DemoOperationalSeedContext context
    ) {
        Set<UUID> workerIds = new HashSet<>();
        tasks.stream().map(TaskSeed::workerId).forEach(workerIds::add);
        documents.stream().map(DocumentSeed::workerId).forEach(workerIds::add);
        boolean missingWorker = workerIds.stream().anyMatch(workerId ->
                workerRepository.findByWorkerIdAndCompanyId(workerId, context.companyId()).isEmpty());
        if (missingWorker) {
            throw new IllegalStateException("a demo task or document worker does not exist in its company");
        }
    }

    private void verifyUniqueIds(List<UUID> ids, String entityName) {
        if (new HashSet<>(ids).size() != ids.size()) {
            throw new IllegalStateException("duplicate reserved demo " + entityName + " id");
        }
    }
}
