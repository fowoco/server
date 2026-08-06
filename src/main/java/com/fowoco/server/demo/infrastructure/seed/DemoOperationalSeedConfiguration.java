package com.fowoco.server.demo.infrastructure.seed;

import com.fowoco.server.audit.application.port.AuditEventRepository;
import com.fowoco.server.approval.application.port.ApprovalRequestRepository;
import com.fowoco.server.approval.application.port.EvidenceRepository;
import com.fowoco.server.approval.application.port.ExternalSubmissionRepository;
import com.fowoco.server.approval.application.SafeJsonService;
import com.fowoco.server.auth.infrastructure.seed.DemoAuthSeedProperties;
import com.fowoco.server.document.application.port.DocumentRequestDraftRepository;
import com.fowoco.server.file.application.port.FileStorage;
import com.fowoco.server.file.application.port.StoredFileRepository;
import com.fowoco.server.file.infrastructure.LocalFileStorage;
import com.fowoco.server.task.application.TaskContentCodec;
import com.fowoco.server.task.application.port.TaskChecklistRepository;
import com.fowoco.server.task.application.port.TaskRepository;
import com.fowoco.server.worker.application.port.WorkerDocumentRepository;
import com.fowoco.server.worker.application.port.WorkerRepository;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
public class DemoOperationalSeedConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "app.demo-seed", name = "enabled", havingValue = "true")
    DemoOperationalSeedRunner demoOperationalSeedRunner(
            DemoAuthSeedProperties properties,
            TaskRepository taskRepository,
            TaskContentCodec taskContentCodec,
            TaskChecklistRepository checklistRepository,
            ApprovalRequestRepository approvalRepository,
            SafeJsonService safeJsonService,
            StoredFileRepository storedFileRepository,
            FileStorage fileStorage,
            ExternalSubmissionRepository externalSubmissionRepository,
            EvidenceRepository evidenceRepository,
            DocumentRequestDraftRepository documentRequestDraftRepository,
            WorkerDocumentRepository workerDocumentRepository,
            WorkerRepository workerRepository,
            AuditEventRepository auditEventRepository,
            EntityManager entityManager,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            Clock clock,
            @Value("${app.file-storage.local-path}") String localFileStoragePath
    ) {
        if (!(fileStorage instanceof LocalFileStorage)) {
            throw new IllegalStateException(
                    "demo file fixtures currently require LocalFileStorage"
            );
        }
        DemoOperationalSeedCatalog catalog = new DemoOperationalSeedCatalog();
        DemoCaseSeeder caseSeeder = new DemoCaseSeeder(jdbcTemplate, objectMapper);
        DemoTaskSeeder taskSeeder = new DemoTaskSeeder(taskRepository, taskContentCodec);
        DemoStoredFileSeeder storedFileSeeder = new DemoStoredFileSeeder(
                storedFileRepository,
                jdbcTemplate,
                new DemoFileFixtureInstaller(localFileStoragePath)
        );
        DemoWorkerDocumentSeeder documentSeeder = new DemoWorkerDocumentSeeder(workerDocumentRepository);
        DemoTaskChecklistSeeder checklistSeeder = new DemoTaskChecklistSeeder(checklistRepository);
        DemoApprovalRequestSeeder approvalSeeder = new DemoApprovalRequestSeeder(
                approvalRepository,
                taskRepository,
                safeJsonService
        );
        DemoTaskTransitionSeeder transitionSeeder = new DemoTaskTransitionSeeder(jdbcTemplate);
        DemoExternalSubmissionSeeder externalSubmissionSeeder = new DemoExternalSubmissionSeeder(
                externalSubmissionRepository,
                entityManager,
                jdbcTemplate
        );
        DemoEvidenceSeeder evidenceSeeder = new DemoEvidenceSeeder(
                evidenceRepository,
                entityManager,
                jdbcTemplate
        );
        DemoDocumentRequestDraftSeeder requestDraftSeeder = new DemoDocumentRequestDraftSeeder(
                documentRequestDraftRepository,
                jdbcTemplate
        );
        DemoAuditEventSeeder auditSeeder = new DemoAuditEventSeeder(auditEventRepository);
        DemoOperationalSeedVerifier verifier = new DemoOperationalSeedVerifier(
                taskRepository,
                workerDocumentRepository,
                workerRepository,
                checklistRepository,
                approvalRepository,
                taskSeeder,
                documentSeeder,
                checklistSeeder,
                approvalSeeder,
                storedFileSeeder,
                transitionSeeder,
                externalSubmissionSeeder,
                evidenceSeeder,
                requestDraftSeeder,
                auditSeeder
        );
        return new DemoOperationalSeedRunner(
                properties,
                clock,
                catalog,
                caseSeeder,
                taskSeeder,
                documentSeeder,
                checklistSeeder,
                approvalSeeder,
                storedFileSeeder,
                transitionSeeder,
                externalSubmissionSeeder,
                evidenceSeeder,
                requestDraftSeeder,
                auditSeeder,
                verifier
        );
    }
}
