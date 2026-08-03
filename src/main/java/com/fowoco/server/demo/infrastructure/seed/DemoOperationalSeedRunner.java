package com.fowoco.server.demo.infrastructure.seed;

import com.fowoco.server.auth.infrastructure.seed.DemoAuthSeedProperties;
import com.fowoco.server.demo.infrastructure.seed.DemoOperationalSeedCatalog.AuditSeed;
import com.fowoco.server.demo.infrastructure.seed.DemoOperationalSeedCatalog.DocumentSeed;
import com.fowoco.server.demo.infrastructure.seed.DemoOperationalSeedCatalog.TaskSeed;
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
    private final DemoAuditEventSeeder auditSeeder;
    private final DemoOperationalSeedVerifier verifier;

    DemoOperationalSeedRunner(
            DemoAuthSeedProperties properties,
            Clock clock,
            DemoOperationalSeedCatalog catalog,
            DemoTaskSeeder taskSeeder,
            DemoWorkerDocumentSeeder documentSeeder,
            DemoAuditEventSeeder auditSeeder,
            DemoOperationalSeedVerifier verifier
    ) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.catalog = Objects.requireNonNull(catalog, "catalog must not be null");
        this.taskSeeder = Objects.requireNonNull(taskSeeder, "taskSeeder must not be null");
        this.documentSeeder = Objects.requireNonNull(documentSeeder, "documentSeeder must not be null");
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
                catalog.demoDocuments(),
                catalog.demoAudits(),
                demoContext
        );
        seedDataset(catalog.testTasks(), catalog.testDocuments(), List.of(), testContext);
        LOGGER.info(
                "demo_operational_seed ready demo_task_count={} demo_document_count={} "
                        + "test_task_count={} test_document_count={} audit_count={}",
                catalog.demoTasks().size(),
                catalog.demoDocuments().size(),
                catalog.testTasks().size(),
                catalog.testDocuments().size(),
                catalog.demoAudits().size()
        );
    }

    private void seedDataset(
            List<TaskSeed> tasks,
            List<DocumentSeed> documents,
            List<AuditSeed> audits,
            DemoOperationalSeedContext context
    ) {
        tasks.forEach(seed -> taskSeeder.seed(seed, context));
        documents.forEach(seed -> documentSeeder.seed(seed, context));
        audits.forEach(seed -> auditSeeder.seed(seed, context));
        verifier.verify(tasks, documents, audits, context);
    }
}
