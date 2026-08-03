package com.fowoco.server.demo.infrastructure.seed;

import com.fowoco.server.auth.infrastructure.seed.DemoAuthSeedProperties;
import java.time.Clock;
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
        DemoOperationalSeedContext context = DemoOperationalSeedContext.from(properties, clock);
        catalog.tasks().forEach(seed -> taskSeeder.seed(seed, context));
        catalog.documents().forEach(seed -> documentSeeder.seed(seed, context));
        catalog.audits().forEach(seed -> auditSeeder.seed(seed, context));
        verifier.verify(catalog, context);
        LOGGER.info(
                "demo_operational_seed ready company_id={} task_count={} document_count={} audit_count={}",
                context.companyId(),
                catalog.tasks().size(),
                catalog.documents().size(),
                catalog.audits().size()
        );
    }
}
