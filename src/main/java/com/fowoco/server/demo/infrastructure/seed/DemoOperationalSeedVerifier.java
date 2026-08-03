package com.fowoco.server.demo.infrastructure.seed;

import com.fowoco.server.audit.domain.AuditEvent;
import com.fowoco.server.demo.infrastructure.seed.DemoOperationalSeedCatalog.AuditSeed;
import com.fowoco.server.demo.infrastructure.seed.DemoOperationalSeedCatalog.DocumentSeed;
import com.fowoco.server.demo.infrastructure.seed.DemoOperationalSeedCatalog.TaskSeed;
import com.fowoco.server.task.application.port.TaskRepository;
import com.fowoco.server.task.domain.Task;
import com.fowoco.server.worker.application.port.WorkerDocumentRepository;
import com.fowoco.server.worker.domain.WorkerDocument;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

final class DemoOperationalSeedVerifier {

    private final TaskRepository taskRepository;
    private final WorkerDocumentRepository workerDocumentRepository;
    private final DemoTaskSeeder taskSeeder;
    private final DemoWorkerDocumentSeeder documentSeeder;
    private final DemoAuditEventSeeder auditSeeder;

    DemoOperationalSeedVerifier(
            TaskRepository taskRepository,
            WorkerDocumentRepository workerDocumentRepository,
            DemoTaskSeeder taskSeeder,
            DemoWorkerDocumentSeeder documentSeeder,
            DemoAuditEventSeeder auditSeeder
    ) {
        this.taskRepository = Objects.requireNonNull(taskRepository, "taskRepository must not be null");
        this.workerDocumentRepository = Objects.requireNonNull(
                workerDocumentRepository,
                "workerDocumentRepository must not be null"
        );
        this.taskSeeder = Objects.requireNonNull(taskSeeder, "taskSeeder must not be null");
        this.documentSeeder = Objects.requireNonNull(documentSeeder, "documentSeeder must not be null");
        this.auditSeeder = Objects.requireNonNull(auditSeeder, "auditSeeder must not be null");
    }

    void verify(DemoOperationalSeedCatalog catalog, DemoOperationalSeedContext context) {
        verifyUniqueIds(catalog.tasks().stream().map(TaskSeed::taskId).toList(), "task");
        verifyUniqueIds(catalog.documents().stream().map(DocumentSeed::documentId).toList(), "document");
        verifyUniqueIds(catalog.audits().stream().map(AuditSeed::auditEventId).toList(), "audit event");
        verifyDocumentWorkersHaveTasks(catalog);
        catalog.tasks().forEach(seed -> verifyTask(seed, context));
        catalog.documents().forEach(seed -> verifyDocument(seed, context));
        catalog.audits().forEach(seed -> verifyAudit(seed, context));
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

    private void verifyAudit(AuditSeed seed, DemoOperationalSeedContext context) {
        AuditEvent event = auditSeeder.findExisting(seed, context)
                .orElseThrow(() -> new IllegalStateException("a reserved demo audit event was not seeded"));
        auditSeeder.verifyExisting(event, seed, context);
    }

    private void verifyDocumentWorkersHaveTasks(DemoOperationalSeedCatalog catalog) {
        Set<UUID> taskWorkerIds = catalog.tasks().stream()
                .map(TaskSeed::workerId)
                .collect(java.util.stream.Collectors.toSet());
        boolean orphanedDocument = catalog.documents().stream()
                .map(DocumentSeed::workerId)
                .anyMatch(workerId -> !taskWorkerIds.contains(workerId));
        if (orphanedDocument) {
            throw new IllegalStateException("a demo document worker has no demo task");
        }
    }

    private void verifyUniqueIds(List<UUID> ids, String entityName) {
        if (new HashSet<>(ids).size() != ids.size()) {
            throw new IllegalStateException("duplicate reserved demo " + entityName + " id");
        }
    }
}
