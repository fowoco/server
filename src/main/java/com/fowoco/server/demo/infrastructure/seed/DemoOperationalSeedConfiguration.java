package com.fowoco.server.demo.infrastructure.seed;

import com.fowoco.server.audit.application.port.AuditEventRepository;
import com.fowoco.server.approval.application.port.ApprovalRequestRepository;
import com.fowoco.server.auth.infrastructure.seed.DemoAuthSeedProperties;
import com.fowoco.server.task.application.TaskContentCodec;
import com.fowoco.server.task.application.port.TaskChecklistRepository;
import com.fowoco.server.task.application.port.TaskRepository;
import com.fowoco.server.worker.application.port.WorkerDocumentRepository;
import com.fowoco.server.worker.application.port.WorkerRepository;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

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
            WorkerDocumentRepository workerDocumentRepository,
            WorkerRepository workerRepository,
            AuditEventRepository auditEventRepository,
            JdbcTemplate jdbcTemplate,
            Clock clock
    ) {
        DemoOperationalSeedCatalog catalog = new DemoOperationalSeedCatalog();
        DemoTaskSeeder taskSeeder = new DemoTaskSeeder(taskRepository, taskContentCodec);
        DemoWorkerDocumentSeeder documentSeeder = new DemoWorkerDocumentSeeder(workerDocumentRepository);
        DemoTaskChecklistSeeder checklistSeeder = new DemoTaskChecklistSeeder(checklistRepository);
        DemoApprovalRequestSeeder approvalSeeder = new DemoApprovalRequestSeeder(
                approvalRepository,
                taskRepository
        );
        DemoTaskTransitionSeeder transitionSeeder = new DemoTaskTransitionSeeder(jdbcTemplate);
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
                transitionSeeder,
                auditSeeder
        );
        return new DemoOperationalSeedRunner(
                properties,
                clock,
                catalog,
                taskSeeder,
                documentSeeder,
                checklistSeeder,
                approvalSeeder,
                transitionSeeder,
                auditSeeder,
                verifier
        );
    }
}
