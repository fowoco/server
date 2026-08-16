package com.fowoco.server.demo.infrastructure.documentdata;

import com.fowoco.server.auth.infrastructure.seed.DemoAuthSeedProperties;
import com.fowoco.server.demo.infrastructure.documentdata.DemoDocumentDataService.DemoDocumentDataReport;
import java.util.Locale;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(3)
class DemoDocumentDataCommandRunner implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(DemoDocumentDataCommandRunner.class);

    private final DemoDocumentDataService service;
    private final DemoAuthSeedProperties seedProperties;
    private final ConfigurableApplicationContext applicationContext;
    private final String command;

    DemoDocumentDataCommandRunner(
            DemoDocumentDataService service,
            DemoAuthSeedProperties seedProperties,
            ConfigurableApplicationContext applicationContext,
            @Value("${app.demo-document-data.command:none}") String command
    ) {
        this.service = Objects.requireNonNull(service, "service must not be null");
        this.seedProperties = Objects.requireNonNull(seedProperties, "seedProperties must not be null");
        this.applicationContext = Objects.requireNonNull(applicationContext, "applicationContext must not be null");
        this.command = Objects.requireNonNull(command, "command must not be null");
    }

    @Override
    public void run(ApplicationArguments arguments) {
        Command parsed = Command.parse(command);
        if (parsed == Command.NONE) {
            return;
        }
        if (!seedProperties.enabled()) {
            throw new IllegalStateException("DEMO_SEED_ENABLED=true is required for demo document data commands");
        }
        DemoDocumentDataReport report = switch (parsed) {
            case IMPORT -> service.importData();
            case VERIFY -> service.verifyData();
            case CLEANUP -> service.cleanupData();
            case NONE -> throw new IllegalStateException("unreachable demo document command");
        };
        LOGGER.info(
                "demo_document_data command={} document_count={} file_count={} image_count={} "
                        + "pdf_count={} hwp_count={} hwpx_count={} task_linked_count={} missing_count={} "
                        + "passport_worker_count={} legacy_materialized_file_count={}",
                parsed.name().toLowerCase(Locale.ROOT),
                report.documentCount(),
                report.fileCount(),
                report.imageCount(),
                report.pdfCount(),
                report.hwpCount(),
                report.hwpxCount(),
                report.taskLinkedDocumentCount(),
                report.missingDocumentCount(),
                report.passportWorkerCount(),
                report.legacyMaterializedFileCount()
        );
        applicationContext.close();
    }

    private enum Command {
        NONE,
        IMPORT,
        VERIFY,
        CLEANUP;

        static Command parse(String value) {
            try {
                return Command.valueOf(value.strip().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException(
                        "app.demo-document-data.command must be one of none, import, verify, cleanup",
                        exception
                );
            }
        }
    }
}
