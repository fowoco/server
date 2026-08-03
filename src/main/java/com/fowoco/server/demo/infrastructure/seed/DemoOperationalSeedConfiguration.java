package com.fowoco.server.demo.infrastructure.seed;

import com.fowoco.server.audit.application.port.AuditEventRepository;
import com.fowoco.server.auth.infrastructure.seed.DemoAuthSeedProperties;
import com.fowoco.server.task.application.TaskContentCodec;
import com.fowoco.server.task.application.port.TaskRepository;
import com.fowoco.server.worker.application.port.WorkerDocumentRepository;
import com.fowoco.server.worker.application.port.WorkerRepository;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class DemoOperationalSeedConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "app.demo-seed", name = "enabled", havingValue = "true")
    DemoOperationalSeedRunner demoOperationalSeedRunner(
            DemoAuthSeedProperties properties,
            TaskRepository taskRepository,
            TaskContentCodec taskContentCodec,
            WorkerDocumentRepository workerDocumentRepository,
            WorkerRepository workerRepository,
            AuditEventRepository auditEventRepository,
            Clock clock
    ) {
        DemoOperationalSeedCatalog catalog = new DemoOperationalSeedCatalog();
        DemoTaskSeeder taskSeeder = new DemoTaskSeeder(taskRepository, taskContentCodec);
        DemoWorkerDocumentSeeder documentSeeder = new DemoWorkerDocumentSeeder(workerDocumentRepository);
        DemoAuditEventSeeder auditSeeder = new DemoAuditEventSeeder(auditEventRepository);
        DemoOperationalSeedVerifier verifier = new DemoOperationalSeedVerifier(
                taskRepository,
                workerDocumentRepository,
                workerRepository,
                taskSeeder,
                documentSeeder,
                auditSeeder
        );
        return new DemoOperationalSeedRunner(
                properties,
                clock,
                catalog,
                taskSeeder,
                documentSeeder,
                auditSeeder,
                verifier
        );
    }
}
