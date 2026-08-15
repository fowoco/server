package com.fowoco.server.demo.infrastructure.documentdata;

import com.fowoco.server.aiintegration.application.ocr.AiOcrDocumentSide;
import com.fowoco.server.aiintegration.application.ocr.AiOcrStatus;
import com.fowoco.server.common.security.TenantDatabaseContext;
import com.fowoco.server.demo.infrastructure.documentdata.DemoDocumentFixtureCatalog.DemoDocumentFixture;
import com.fowoco.server.demo.infrastructure.documentdata.DemoDocumentFixtureCatalog.FixtureFormat;
import com.fowoco.server.document.application.DocumentOcrResultPayload;
import com.fowoco.server.document.application.port.DocumentOcrRunRepository;
import com.fowoco.server.document.application.port.OcrResultCipher;
import com.fowoco.server.document.domain.DocumentOcrRun;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
class DemoDocumentDataService {

    private static final String SOURCE = "DEMO_SEED";
    private static final String PURPOSE = "DEMO_WORKER_DOCUMENT";
    private static final String OCR_IDEMPOTENCY_HASH = sha256Text("demo-document-ocr-review-v1");
    private static final String OCR_REQUEST_HASH = sha256Text(
            DemoDocumentFixtureCatalog.OCR_DOCUMENT_ID.toString()
    );

    private final JdbcTemplate jdbcTemplate;
    private final TenantDatabaseContext tenantDatabaseContext;
    private final DocumentOcrRunRepository ocrRunRepository;
    private final OcrResultCipher ocrResultCipher;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final SyntheticDocumentGenerator generator = new SyntheticDocumentGenerator();
    private final DemoDocumentFileInstaller fileInstaller;

    DemoDocumentDataService(
            JdbcTemplate jdbcTemplate,
            TenantDatabaseContext tenantDatabaseContext,
            DocumentOcrRunRepository ocrRunRepository,
            OcrResultCipher ocrResultCipher,
            ObjectMapper objectMapper,
            Clock clock,
            @Value("${app.file-storage.local-path}") String localFileStoragePath
    ) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.tenantDatabaseContext = Objects.requireNonNull(
                tenantDatabaseContext, "tenantDatabaseContext must not be null"
        );
        this.ocrRunRepository = Objects.requireNonNull(ocrRunRepository, "ocrRunRepository must not be null");
        this.ocrResultCipher = Objects.requireNonNull(ocrResultCipher, "ocrResultCipher must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.fileInstaller = new DemoDocumentFileInstaller(localFileStoragePath);
    }

    @Transactional
    DemoDocumentDataReport importData() {
        bindTenant();
        requireBaseSeed();
        requireOcrEncryption();
        LocalDate anchorDate = anchorDate();
        Instant now = clock.instant();
        for (DemoDocumentFixture fixture : DemoDocumentFixtureCatalog.fixtures()) {
            requireWorkerAndTask(fixture);
            byte[] content = content(fixture, anchorDate);
            if (content != null) {
                fileInstaller.install(fixture.storageKey(), content);
                seedStoredFile(fixture, content, now);
            }
            seedWorkerDocument(fixture, anchorDate, now);
        }
        seedOcrReview(now);
        return verifyInternal(anchorDate);
    }

    @Transactional(readOnly = true)
    DemoDocumentDataReport verifyData() {
        bindTenant();
        requireBaseSeed();
        requireOcrEncryption();
        return verifyInternal(anchorDate());
    }

    @Transactional
    DemoDocumentDataReport cleanupData() {
        bindTenant();
        requireBaseSeed();
        LocalDate anchorDate = anchorDate();
        verifyOwnedRowsBeforeCleanup(anchorDate);

        jdbcTemplate.update(
                "DELETE FROM document_ocr_run WHERE ocr_run_id = ? AND company_id = ?",
                DemoDocumentFixtureCatalog.OCR_RUN_ID,
                DemoDocumentFixtureCatalog.COMPANY_ID
        );
        for (DemoDocumentFixture fixture : DemoDocumentFixtureCatalog.fixtures()) {
            jdbcTemplate.update(
                    "DELETE FROM worker_document WHERE worker_document_id = ? AND company_id = ?",
                    fixture.documentId(), DemoDocumentFixtureCatalog.COMPANY_ID
            );
        }
        for (DemoDocumentFixture fixture : DemoDocumentFixtureCatalog.fixtures()) {
            byte[] content = content(fixture, anchorDate);
            if (content == null) {
                continue;
            }
            jdbcTemplate.update(
                    "DELETE FROM stored_file WHERE stored_file_id = ? AND company_id = ?",
                    fixture.fileId(), DemoDocumentFixtureCatalog.COMPANY_ID
            );
            fileInstaller.cleanup(fixture.storageKey(), content);
        }
        return new DemoDocumentDataReport(0, 0, 0, 0, 0, 0, 0, 0);
    }

    private void seedStoredFile(DemoDocumentFixture fixture, byte[] content, Instant now) {
        List<Map<String, Object>> existing = jdbcTemplate.queryForList(
                "SELECT * FROM stored_file WHERE stored_file_id = ?",
                fixture.fileId()
        );
        if (!existing.isEmpty()) {
            verifyStoredFile(fixture, content, existing.get(0));
            return;
        }
        jdbcTemplate.update(
                """
                INSERT INTO stored_file (
                    stored_file_id, company_id, name, mime_type, size, purpose,
                    task_id, worker_id, storage_key, scan_status, verified,
                    checksum_sha256, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'NOT_SCANNED', TRUE, ?, ?, ?)
                """,
                fixture.fileId(),
                DemoDocumentFixtureCatalog.COMPANY_ID,
                fixture.originalFilename(),
                fixture.contentType(),
                content.length,
                PURPOSE,
                fixture.taskId(),
                fixture.workerId(),
                fixture.storageKey(),
                DemoDocumentFileInstaller.sha256(content),
                Timestamp.from(now),
                Timestamp.from(now)
        );
    }

    private void seedWorkerDocument(DemoDocumentFixture fixture, LocalDate anchorDate, Instant now) {
        List<Map<String, Object>> existing = jdbcTemplate.queryForList(
                "SELECT * FROM worker_document WHERE worker_document_id = ?",
                fixture.documentId()
        );
        if (!existing.isEmpty()) {
            verifyWorkerDocument(fixture, anchorDate, existing.get(0));
            return;
        }
        jdbcTemplate.update(
                """
                INSERT INTO worker_document (
                    worker_document_id, worker_id, company_id, task_id, document_type,
                    submission_status, issue_date, expiry_date, destination, note,
                    file_id, source, created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                """,
                fixture.documentId(),
                fixture.workerId(),
                DemoDocumentFixtureCatalog.COMPANY_ID,
                fixture.taskId(),
                fixture.documentType().name(),
                fixture.status().name(),
                date(relativeDate(anchorDate, fixture.issueDays())),
                date(relativeDate(anchorDate, fixture.expiryDays())),
                fixture.destination(),
                fixture.note(),
                fixture.fileId(),
                SOURCE,
                Timestamp.from(now),
                Timestamp.from(now)
        );
    }

    private void seedOcrReview(Instant now) {
        var existing = ocrRunRepository.findByIdAndCompanyId(
                DemoDocumentFixtureCatalog.OCR_RUN_ID,
                DemoDocumentFixtureCatalog.COMPANY_ID
        );
        if (existing.isPresent()) {
            verifyOcrRun(existing.get());
            return;
        }
        byte[] plaintext = ocrPayload();
        String ciphertext = ocrResultCipher.encrypt(
                plaintext,
                DemoDocumentFixtureCatalog.COMPANY_ID,
                DemoDocumentFixtureCatalog.OCR_RUN_ID
        );
        DocumentOcrRun run = DocumentOcrRun.create(
                        DemoDocumentFixtureCatalog.OCR_RUN_ID,
                        DemoDocumentFixtureCatalog.COMPANY_ID,
                        DemoDocumentFixtureCatalog.OCR_DOCUMENT_ID,
                        fixtureById(DemoDocumentFixtureCatalog.OCR_DOCUMENT_ID).fileId(),
                        DemoDocumentFixtureCatalog.ADMIN_USER_ID,
                        DemoDocumentFixtureCatalog.OCR_RUNTIME_REQUEST_ID,
                        OCR_IDEMPOTENCY_HASH,
                        OCR_REQUEST_HASH,
                        com.fowoco.server.worker.domain.DocumentType.ARC,
                        null,
                        now
                )
                .start(now)
                .complete(AiOcrStatus.REVIEW_REQUIRED, ciphertext, ocrResultCipher.keyVersion(), now);
        ocrRunRepository.insert(run);
    }

    private DemoDocumentDataReport verifyInternal(LocalDate anchorDate) {
        int fileCount = 0;
        int imageCount = 0;
        int pdfCount = 0;
        int hwpCount = 0;
        int hwpxCount = 0;
        for (DemoDocumentFixture fixture : DemoDocumentFixtureCatalog.fixtures()) {
            requireWorkerAndTask(fixture);
            List<Map<String, Object>> documentRows = jdbcTemplate.queryForList(
                    "SELECT * FROM worker_document WHERE worker_document_id = ?",
                    fixture.documentId()
            );
            if (documentRows.size() != 1) {
                throw new IllegalStateException("demo worker document fixture is missing");
            }
            verifyWorkerDocument(fixture, anchorDate, documentRows.get(0));
            byte[] content = content(fixture, anchorDate);
            if (content == null) {
                continue;
            }
            List<Map<String, Object>> fileRows = jdbcTemplate.queryForList(
                    "SELECT * FROM stored_file WHERE stored_file_id = ?",
                    fixture.fileId()
            );
            if (fileRows.size() != 1) {
                throw new IllegalStateException("demo stored file fixture is missing");
            }
            verifyStoredFile(fixture, content, fileRows.get(0));
            fileInstaller.verify(fixture.storageKey(), content);
            fileCount++;
            switch (fixture.format()) {
                case PNG, JPEG -> imageCount++;
                case PDF -> pdfCount++;
                case HWP -> hwpCount++;
                case HWPX -> hwpxCount++;
                case NONE -> throw new IllegalStateException("missing fixture unexpectedly had content");
            }
        }
        DocumentOcrRun ocrRun = ocrRunRepository.findByIdAndCompanyId(
                        DemoDocumentFixtureCatalog.OCR_RUN_ID,
                        DemoDocumentFixtureCatalog.COMPANY_ID
                )
                .orElseThrow(() -> new IllegalStateException("demo OCR review fixture is missing"));
        verifyOcrRun(ocrRun);
        int taskLinked = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM worker_document WHERE source = ? AND company_id = ? AND task_id IS NOT NULL",
                Integer.class,
                SOURCE,
                DemoDocumentFixtureCatalog.COMPANY_ID
        );
        int missing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM worker_document WHERE source = ? AND company_id = ? AND submission_status = 'MISSING'",
                Integer.class,
                SOURCE,
                DemoDocumentFixtureCatalog.COMPANY_ID
        );
        return new DemoDocumentDataReport(
                DemoDocumentFixtureCatalog.fixtures().size(),
                fileCount,
                imageCount,
                pdfCount,
                hwpCount,
                hwpxCount,
                taskLinked,
                missing
        );
    }

    private void verifyOwnedRowsBeforeCleanup(LocalDate anchorDate) {
        for (DemoDocumentFixture fixture : DemoDocumentFixtureCatalog.fixtures()) {
            List<Map<String, Object>> documents = jdbcTemplate.queryForList(
                    "SELECT * FROM worker_document WHERE worker_document_id = ?",
                    fixture.documentId()
            );
            if (!documents.isEmpty()) {
                verifyWorkerDocument(fixture, anchorDate, documents.get(0));
            }
            byte[] content = content(fixture, anchorDate);
            if (content == null) {
                continue;
            }
            List<Map<String, Object>> files = jdbcTemplate.queryForList(
                    "SELECT * FROM stored_file WHERE stored_file_id = ?",
                    fixture.fileId()
            );
            if (!files.isEmpty()) {
                verifyStoredFile(fixture, content, files.get(0));
                fileInstaller.verify(fixture.storageKey(), content);
            }
        }
    }

    private void verifyStoredFile(
            DemoDocumentFixture fixture,
            byte[] content,
            Map<String, Object> row
    ) {
        if (!fixture.fileId().equals(row.get("stored_file_id"))
                || !DemoDocumentFixtureCatalog.COMPANY_ID.equals(row.get("company_id"))
                || !fixture.originalFilename().equals(row.get("name"))
                || !fixture.contentType().equals(row.get("mime_type"))
                || ((Number) row.get("size")).longValue() != content.length
                || !PURPOSE.equals(row.get("purpose"))
                || !Objects.equals(fixture.taskId(), row.get("task_id"))
                || !fixture.workerId().equals(row.get("worker_id"))
                || !fixture.storageKey().equals(row.get("storage_key"))
                || !"NOT_SCANNED".equals(row.get("scan_status").toString())
                || !Boolean.TRUE.equals(row.get("verified"))
                || !DemoDocumentFileInstaller.sha256(content).equals(row.get("checksum_sha256"))) {
            throw new IllegalStateException("reserved demo stored file id belongs to different data");
        }
    }

    private void verifyWorkerDocument(
            DemoDocumentFixture fixture,
            LocalDate anchorDate,
            Map<String, Object> row
    ) {
        if (!fixture.documentId().equals(row.get("worker_document_id"))
                || !fixture.workerId().equals(row.get("worker_id"))
                || !DemoDocumentFixtureCatalog.COMPANY_ID.equals(row.get("company_id"))
                || !Objects.equals(fixture.taskId(), row.get("task_id"))
                || !fixture.documentType().name().equals(row.get("document_type").toString())
                || !fixture.status().name().equals(row.get("submission_status").toString())
                || !Objects.equals(relativeDate(anchorDate, fixture.issueDays()), localDate(row.get("issue_date")))
                || !Objects.equals(relativeDate(anchorDate, fixture.expiryDays()), localDate(row.get("expiry_date")))
                || !fixture.destination().equals(row.get("destination"))
                || !fixture.note().equals(row.get("note"))
                || !Objects.equals(fixture.fileId(), row.get("file_id"))
                || !SOURCE.equals(row.get("source"))) {
            throw new IllegalStateException("reserved demo worker document id belongs to different data");
        }
    }

    private void verifyOcrRun(DocumentOcrRun run) {
        if (!DemoDocumentFixtureCatalog.OCR_RUN_ID.equals(run.ocrRunId())
                || !DemoDocumentFixtureCatalog.COMPANY_ID.equals(run.companyId())
                || !DemoDocumentFixtureCatalog.OCR_DOCUMENT_ID.equals(run.workerDocumentId())
                || run.status() != com.fowoco.server.document.domain.DocumentOcrRunStatus.REVIEW_REQUIRED
                || run.resultCiphertext() == null
                || run.resultKeyVersion() == null) {
            throw new IllegalStateException("reserved demo OCR run id belongs to different data");
        }
        byte[] decrypted = ocrResultCipher.decrypt(
                run.resultCiphertext(), run.companyId(), run.ocrRunId()
        );
        DocumentOcrResultPayload actualPayload;
        try {
            actualPayload = objectMapper.readValue(decrypted, DocumentOcrResultPayload.class);
        } catch (JacksonException exception) {
            throw new IllegalStateException("failed to deserialize demo OCR payload", exception);
        }
        if (!ocrPayloadObject().equals(actualPayload)) {
            throw new IllegalStateException("demo OCR result payload does not match the fixture");
        }
    }

    private byte[] ocrPayload() {
        try {
            return objectMapper.writeValueAsBytes(ocrPayloadObject());
        } catch (JacksonException exception) {
            throw new IllegalStateException("failed to serialize demo OCR payload", exception);
        }
    }

    private DocumentOcrResultPayload ocrPayloadObject() {
        return new DocumentOcrResultPayload(
                43019L,
                AiOcrDocumentSide.FRONT,
                new TreeMap<>(Map.of(
                        "alien_registration_number", "SYNTHETIC-ARC-0001",
                        "visa_type", "E-9",
                        "stay_expiration_date", "2099-12-31",
                        "residence_address_1", "DEMO ADDRESS - NOT REAL"
                )),
                new TreeMap<>(Map.of(
                        "alien_registration_number", new BigDecimal("0.61"),
                        "visa_type", new BigDecimal("0.98"),
                        "stay_expiration_date", new BigDecimal("0.82"),
                        "residence_address_1", new BigDecimal("0.58")
                )),
                List.of("LOW_CONFIDENCE_IDENTIFIER", "SYNTHETIC_FIXTURE_REVIEW")
        );
    }

    private void requireBaseSeed() {
        Integer company = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM company WHERE company_id = ? AND status = 'ACTIVE'",
                Integer.class,
                DemoDocumentFixtureCatalog.COMPANY_ID
        );
        Integer worker = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM worker WHERE worker_id = ? AND company_id = ? AND display_name = ?",
                Integer.class,
                DemoDocumentFixtureCatalog.GOLD_WORKER_ID,
                DemoDocumentFixtureCatalog.COMPANY_ID,
                "응웬반A"
        );
        if (company != 1 || worker != 1) {
            throw new IllegalStateException("base Demo Company and Gold Worker seed must exist first");
        }
    }

    private void requireWorkerAndTask(DemoDocumentFixture fixture) {
        Integer worker = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM worker WHERE worker_id = ? AND company_id = ?",
                Integer.class,
                fixture.workerId(),
                DemoDocumentFixtureCatalog.COMPANY_ID
        );
        if (worker != 1) {
            throw new IllegalStateException("demo document worker is missing");
        }
        if (fixture.taskId() == null) {
            return;
        }
        Integer task = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM task WHERE task_id = ? AND worker_id = ? AND company_id = ?",
                Integer.class,
                fixture.taskId(),
                fixture.workerId(),
                DemoDocumentFixtureCatalog.COMPANY_ID
        );
        if (task != 1) {
            throw new IllegalStateException("demo document task relationship is invalid");
        }
    }

    private void requireOcrEncryption() {
        if (!ocrResultCipher.isAvailable()) {
            throw new IllegalStateException(
                    "demo document data requires DOCUMENT_OCR_ENABLED and OCR_RESULT_ENCRYPTION_KEY_BASE64"
            );
        }
    }

    private void bindTenant() {
        tenantDatabaseContext.setCompanyIdForCurrentTransaction(DemoDocumentFixtureCatalog.COMPANY_ID);
    }

    private byte[] content(DemoDocumentFixture fixture, LocalDate anchorDate) {
        return fixture.format() == FixtureFormat.NONE
                ? null
                : generator.generate(
                        fixture,
                        relativeDate(anchorDate, fixture.issueDays()),
                        relativeDate(anchorDate, fixture.expiryDays())
                );
    }

    private DemoDocumentFixture fixtureById(UUID documentId) {
        return DemoDocumentFixtureCatalog.fixtures().stream()
                .filter(fixture -> fixture.documentId().equals(documentId))
                .findFirst()
                .orElseThrow();
    }

    private LocalDate relativeDate(LocalDate today, Integer days) {
        return days == null ? null : today.plusDays(days);
    }

    private LocalDate anchorDate() {
        DemoDocumentFixture anchorFixture = DemoDocumentFixtureCatalog.fixtures().get(0);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT expiry_date FROM worker_document WHERE worker_document_id = ? AND company_id = ?",
                anchorFixture.documentId(), DemoDocumentFixtureCatalog.COMPANY_ID
        );
        if (rows.isEmpty()) {
            return LocalDate.now(clock);
        }
        LocalDate expiryDate = localDate(rows.get(0).get("expiry_date"));
        if (expiryDate == null || anchorFixture.expiryDays() == null) {
            throw new IllegalStateException("demo document anchor date is missing");
        }
        return expiryDate.minusDays(anchorFixture.expiryDays());
    }

    private Date date(LocalDate value) {
        return value == null ? null : Date.valueOf(value);
    }

    private LocalDate localDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        return ((Date) value).toLocalDate();
    }

    private static String sha256Text(String value) {
        return DemoDocumentFileInstaller.sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    record DemoDocumentDataReport(
            int documentCount,
            int fileCount,
            int imageCount,
            int pdfCount,
            int hwpCount,
            int hwpxCount,
            int taskLinkedDocumentCount,
            int missingDocumentCount
    ) {
    }
}
