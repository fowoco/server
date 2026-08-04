package com.fowoco.server.demo.infrastructure.seed;

import com.fowoco.server.auth.infrastructure.seed.DemoAuthSeedProperties;
import com.fowoco.server.demo.infrastructure.seed.DemoOperationalSeedCatalog.AuditSeed;
import com.fowoco.server.demo.infrastructure.seed.DemoOperationalSeedCatalog.ApprovalSeed;
import com.fowoco.server.demo.infrastructure.seed.DemoOperationalSeedCatalog.ChecklistSeed;
import com.fowoco.server.demo.infrastructure.seed.DemoOperationalSeedCatalog.DocumentSeed;
import com.fowoco.server.demo.infrastructure.seed.DemoOperationalSeedCatalog.DocumentRequestDraftSeed;
import com.fowoco.server.demo.infrastructure.seed.DemoOperationalSeedCatalog.EvidenceSeed;
import com.fowoco.server.demo.infrastructure.seed.DemoOperationalSeedCatalog.ExternalSubmissionSeed;
import com.fowoco.server.demo.infrastructure.seed.DemoOperationalSeedCatalog.StoredFileSeed;
import com.fowoco.server.demo.infrastructure.seed.DemoOperationalSeedCatalog.TaskSeed;
import com.fowoco.server.demo.infrastructure.seed.DemoOperationalSeedCatalog.TransitionSeed;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.annotation.Transactional;

@Order(2)
class DemoOperationalSeedRunner implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(DemoOperationalSeedRunner.class);

    private final DemoAuthSeedProperties properties;
    private final Clock clock;
    private final DemoOperationalSeedCatalog catalog;
    private final DemoTaskSeeder taskSeeder;
    private final DemoWorkerDocumentSeeder documentSeeder;
    private final DemoTaskChecklistSeeder checklistSeeder;
    private final DemoApprovalRequestSeeder approvalSeeder;
    private final DemoStoredFileSeeder storedFileSeeder;
    private final DemoTaskTransitionSeeder transitionSeeder;
    private final DemoExternalSubmissionSeeder externalSubmissionSeeder;
    private final DemoEvidenceSeeder evidenceSeeder;
    private final DemoDocumentRequestDraftSeeder requestDraftSeeder;
    private final DemoAuditEventSeeder auditSeeder;
    private final DemoOperationalSeedVerifier verifier;

    DemoOperationalSeedRunner(
            DemoAuthSeedProperties properties,
            Clock clock,
            DemoOperationalSeedCatalog catalog,
            DemoTaskSeeder taskSeeder,
            DemoWorkerDocumentSeeder documentSeeder,
            DemoTaskChecklistSeeder checklistSeeder,
            DemoApprovalRequestSeeder approvalSeeder,
            DemoStoredFileSeeder storedFileSeeder,
            DemoTaskTransitionSeeder transitionSeeder,
            DemoExternalSubmissionSeeder externalSubmissionSeeder,
            DemoEvidenceSeeder evidenceSeeder,
            DemoDocumentRequestDraftSeeder requestDraftSeeder,
            DemoAuditEventSeeder auditSeeder,
            DemoOperationalSeedVerifier verifier
    ) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.catalog = Objects.requireNonNull(catalog, "catalog must not be null");
        this.taskSeeder = Objects.requireNonNull(taskSeeder, "taskSeeder must not be null");
        this.documentSeeder = Objects.requireNonNull(documentSeeder, "documentSeeder must not be null");
        this.checklistSeeder = Objects.requireNonNull(checklistSeeder, "checklistSeeder must not be null");
        this.approvalSeeder = Objects.requireNonNull(approvalSeeder, "approvalSeeder must not be null");
        this.storedFileSeeder = Objects.requireNonNull(
                storedFileSeeder,
                "storedFileSeeder must not be null"
        );
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
        this.verifier = Objects.requireNonNull(verifier, "verifier must not be null");
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        Instant now = clock.instant();
        LocalDate today = LocalDate.now(clock);
        DemoOperationalSeedContext demoContext = DemoOperationalSeedContext.demo(properties, today, now);
        DemoOperationalSeedContext testContext = DemoOperationalSeedContext.test(properties, today, now);
        seedDataset(
                catalog.demoTasks(),
                catalog.demoStoredFiles(),
                catalog.demoDocuments(),
                catalog.demoChecklists(),
                catalog.demoApprovals(),
                catalog.demoTransitions(),
                catalog.demoExternalSubmissions(),
                catalog.demoEvidence(),
                catalog.demoDocumentRequestDrafts(),
                catalog.demoAudits(),
                demoContext
        );
        seedDataset(
                catalog.testTasks(),
                List.of(),
                catalog.testDocuments(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                catalog.testAudits(),
                testContext
        );
        LOGGER.info(
                "demo_operational_seed ready demo_task_count={} demo_stored_file_count={} "
                        + "demo_document_count={} "
                        + "checklist_count={} approval_count={} transition_count={} "
                        + "external_submission_count={} evidence_count={} request_draft_count={} "
                        + "test_task_count={} test_document_count={} demo_audit_count={} "
                        + "test_audit_count={}",
                catalog.demoTasks().size(),
                catalog.demoStoredFiles().size(),
                catalog.demoDocuments().size(),
                catalog.demoChecklists().size(),
                catalog.demoApprovals().size(),
                catalog.demoTransitions().size(),
                catalog.demoExternalSubmissions().size(),
                catalog.demoEvidence().size(),
                catalog.demoDocumentRequestDrafts().size(),
                catalog.testTasks().size(),
                catalog.testDocuments().size(),
                catalog.demoAudits().size(),
                catalog.testAudits().size()
        );
    }

    private void seedDataset(
            List<TaskSeed> tasks,
            List<StoredFileSeed> storedFiles,
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
        tasks.forEach(seed -> taskSeeder.seed(seed, context));
        storedFiles.forEach(seed -> storedFileSeeder.seed(seed, context));
        documents.forEach(seed -> documentSeeder.seed(seed, context));
        checklists.forEach(seed -> checklistSeeder.seed(seed, context));
        approvals.forEach(seed -> approvalSeeder.seed(seed, context));
        transitions.forEach(seed -> transitionSeeder.seed(seed, context));
        externalSubmissions.forEach(seed -> externalSubmissionSeeder.seed(seed, context));
        evidence.forEach(seed -> evidenceSeeder.seed(seed, context));
        requestDrafts.forEach(seed -> requestDraftSeeder.seed(seed, context));
        audits.forEach(seed -> auditSeeder.seed(seed, context));
        verifier.verify(
                tasks,
                storedFiles,
                documents,
                checklists,
                approvals,
                transitions,
                externalSubmissions,
                evidence,
                requestDrafts,
                audits,
                context
        );
    }
}
