package com.fowoco.server.demo.infrastructure.documentdata;

import static org.assertj.core.api.Assertions.assertThat;

import com.fowoco.server.demo.infrastructure.documentdata.DemoDocumentDataService.DemoDocumentDataReport;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@ActiveProfiles("test")
@SpringBootTest(properties = {
        "app.demo-seed.enabled=true",
        "app.demo-seed.admin-password=Demo-password-1!",
        "app.demo-document-data.command=none",
        "app.document.ocr.enabled=true",
        "app.document.ocr.encryption-key-base64=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "app.document.ocr.key-version=test-v1"
})
class DemoDocumentDataIntegrationTest {

    private static final Path FILE_STORAGE = Path.of(
            System.getProperty("java.io.tmpdir"),
            "fowoco-demo-documents-" + UUID.randomUUID()
    );

    @Autowired
    private DemoDocumentDataService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApplicationContext applicationContext;

    @DynamicPropertySource
    static void fileStorage(DynamicPropertyRegistry registry) {
        registry.add("app.file-storage.local-path", FILE_STORAGE::toString);
    }

    @Test
    void importsVerifiesCleansAndReimportsWithoutDuplicates() throws Exception {
        assertReport(service.importData());
        assertReport(service.verifyData());
        assertCounts(43, 42, 1);
        assertMaterializedLegacyFiles(67);

        applicationContext.getBean("demoOperationalSeedRunner", ApplicationRunner.class)
                .run(new DefaultApplicationArguments(new String[0]));
        assertReport(service.verifyData());

        assertReport(service.importData());
        assertCounts(43, 42, 1);
        assertMaterializedLegacyFiles(67);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM stored_file WHERE company_id = ? AND checksum_sha256 IS NOT NULL",
                Integer.class,
                DemoDocumentFixtureCatalog.COMPANY_ID
        )).isEqualTo(109);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM worker_document WHERE company_id = ? AND source = 'DEMO_SEED' "
                        + "AND issue_date IS NOT NULL",
                Integer.class,
                DemoDocumentFixtureCatalog.COMPANY_ID
        )).isEqualTo(42);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM worker_document WHERE company_id = ? AND submission_status = 'DRAFT'",
                Integer.class,
                DemoDocumentFixtureCatalog.COMPANY_ID
        )).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT checksum_sha256) FROM stored_file "
                        + "WHERE company_id = ? AND storage_key LIKE '%/passport-copy-worker-%'",
                Integer.class,
                DemoDocumentFixtureCatalog.COMPANY_ID
        )).isEqualTo(27);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT worker_id) FROM worker_document "
                        + "WHERE company_id = ? AND source = 'DEMO_SEED' "
                        + "AND document_type = 'PASSPORT_COPY' AND file_id IS NOT NULL "
                        + "AND submission_status = 'VERIFIED' AND expiry_date > CURRENT_DATE",
                Integer.class,
                DemoDocumentFixtureCatalog.COMPANY_ID
        )).isEqualTo(28);

        service.cleanupData();
        assertCounts(0, 0, 0);
        assertMaterializedLegacyFiles(0);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM worker_document WHERE company_id = ? AND source = 'LEGACY' "
                        + "AND file_id IS NOT NULL",
                Integer.class,
                DemoDocumentFixtureCatalog.COMPANY_ID
        )).isEqualTo(1);

        assertReport(service.importData());
        assertCounts(43, 42, 1);
        assertMaterializedLegacyFiles(67);
    }

    private void assertReport(DemoDocumentDataReport report) {
        int taskLinked = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM worker_document WHERE company_id = ? "
                        + "AND source IN ('DEMO_SEED', 'LEGACY') AND task_id IS NOT NULL",
                Integer.class,
                DemoDocumentFixtureCatalog.COMPANY_ID
        );
        assertThat(report).isEqualTo(new DemoDocumentDataReport(
                126, 109, 71, 34, 1, 3, taskLinked, 17, 28, 67
        ));
    }

    private void assertMaterializedLegacyFiles(int expected) {
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM stored_file WHERE company_id = ? "
                        + "AND purpose = 'DEMO_LEGACY_DOCUMENT'",
                Integer.class,
                DemoDocumentFixtureCatalog.COMPANY_ID
        )).isEqualTo(expected);
    }

    private void assertCounts(int documents, int files, int ocrRuns) {
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM worker_document WHERE company_id = ? AND source = 'DEMO_SEED'",
                Integer.class,
                DemoDocumentFixtureCatalog.COMPANY_ID
        )).isEqualTo(documents);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM stored_file WHERE company_id = ? AND purpose = 'DEMO_WORKER_DOCUMENT'",
                Integer.class,
                DemoDocumentFixtureCatalog.COMPANY_ID
        )).isEqualTo(files);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document_ocr_run WHERE company_id = ? AND ocr_run_id = ?",
                Integer.class,
                DemoDocumentFixtureCatalog.COMPANY_ID,
                DemoDocumentFixtureCatalog.OCR_RUN_ID
        )).isEqualTo(ocrRuns);
    }
}
