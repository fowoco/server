package com.fowoco.server.demo.infrastructure.documentdata;

import static org.assertj.core.api.Assertions.assertThat;

import com.fowoco.server.demo.infrastructure.documentdata.DemoDocumentDataService.DemoDocumentDataReport;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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

    @DynamicPropertySource
    static void fileStorage(DynamicPropertyRegistry registry) {
        registry.add("app.file-storage.local-path", FILE_STORAGE::toString);
    }

    @Test
    void importsVerifiesCleansAndReimportsWithoutDuplicates() {
        assertReport(service.importData());
        assertReport(service.verifyData());
        assertCounts(16, 15, 1);

        assertReport(service.importData());
        assertCounts(16, 15, 1);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM stored_file WHERE company_id = ? AND checksum_sha256 IS NOT NULL",
                Integer.class,
                DemoDocumentFixtureCatalog.COMPANY_ID
        )).isEqualTo(15);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM worker_document WHERE company_id = ? AND source = 'DEMO_SEED' "
                        + "AND issue_date IS NOT NULL",
                Integer.class,
                DemoDocumentFixtureCatalog.COMPANY_ID
        )).isEqualTo(15);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM worker_document WHERE company_id = ? AND submission_status = 'DRAFT'",
                Integer.class,
                DemoDocumentFixtureCatalog.COMPANY_ID
        )).isEqualTo(2);

        service.cleanupData();
        assertCounts(0, 0, 0);

        assertReport(service.importData());
        assertCounts(16, 15, 1);
    }

    private void assertReport(DemoDocumentDataReport report) {
        assertThat(report).isEqualTo(new DemoDocumentDataReport(16, 15, 4, 7, 1, 3, 4, 1));
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
