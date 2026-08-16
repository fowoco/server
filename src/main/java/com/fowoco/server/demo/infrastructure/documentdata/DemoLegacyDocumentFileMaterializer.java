package com.fowoco.server.demo.infrastructure.documentdata;

import com.fowoco.server.demo.infrastructure.DemoDocumentFileIds;
import com.fowoco.server.demo.infrastructure.documentdata.DemoDocumentFixtureCatalog.DemoDocumentFixture;
import com.fowoco.server.demo.infrastructure.documentdata.DemoDocumentFixtureCatalog.FixtureFormat;
import com.fowoco.server.demo.infrastructure.documentdata.DemoDocumentFixtureCatalog.PassportIdentity;
import com.fowoco.server.worker.domain.DocumentType;
import com.fowoco.server.worker.domain.SubmissionStatus;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Adds deterministic files to the pre-existing operational demo document metadata rows. */
final class DemoLegacyDocumentFileMaterializer {

    private static final String SOURCE = "LEGACY";
    private static final String PURPOSE = "DEMO_LEGACY_DOCUMENT";
    private static final UUID ORIGINAL_CONTRACT_DOCUMENT_ID =
            UUID.fromString("95000000-0000-0000-0000-000000000007");
    private static final UUID ORIGINAL_CONTRACT_FILE_ID =
            UUID.fromString("94800000-0000-0000-0000-000000000001");

    private final JdbcTemplate jdbcTemplate;
    private final DemoDocumentFileInstaller fileInstaller;
    private final SyntheticDocumentGenerator generator;

    DemoLegacyDocumentFileMaterializer(
            JdbcTemplate jdbcTemplate,
            DemoDocumentFileInstaller fileInstaller,
            SyntheticDocumentGenerator generator
    ) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.fileInstaller = Objects.requireNonNull(fileInstaller, "fileInstaller must not be null");
        this.generator = Objects.requireNonNull(generator, "generator must not be null");
    }

    MaterializedFiles importFiles(LocalDate anchorDate, Instant now) {
        for (DemoDocumentFixture fixture : fixtures(anchorDate)) {
            byte[] content = content(fixture, anchorDate);
            fileInstaller.install(fixture.storageKey(), content);
            seedStoredFile(fixture, content, now);
            linkWorkerDocument(fixture, now);
        }
        return verifyFiles(anchorDate);
    }

    MaterializedFiles verifyFiles(LocalDate anchorDate) {
        int imageCount = 0;
        int pdfCount = 0;
        List<DemoDocumentFixture> fixtures = fixtures(anchorDate);
        for (DemoDocumentFixture fixture : fixtures) {
            byte[] content = content(fixture, anchorDate);
            verifyWorkerDocumentLink(fixture);
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT * FROM stored_file WHERE stored_file_id = ? AND company_id = ?",
                    fixture.fileId(),
                    DemoDocumentFixtureCatalog.COMPANY_ID
            );
            if (rows.size() != 1) {
                throw new IllegalStateException("materialized demo stored file is missing");
            }
            verifyStoredFile(fixture, content, rows.get(0));
            fileInstaller.verify(fixture.storageKey(), content);
            switch (fixture.format()) {
                case PNG, JPEG -> imageCount++;
                case PDF -> pdfCount++;
                default -> throw new IllegalStateException(
                        "operational demo documents must materialize as image or PDF"
                );
            }
        }
        return new MaterializedFiles(fixtures.size(), imageCount, pdfCount);
    }

    void cleanupFiles(LocalDate anchorDate, Instant now) {
        for (DemoDocumentFixture fixture : fixtures(anchorDate)) {
            UUID currentFileId = workerDocumentFileId(fixture.documentId());
            if (!fixture.fileId().equals(currentFileId)) {
                if (!Objects.equals(originalFileId(fixture.documentId()), currentFileId)) {
                    throw new IllegalStateException(
                            "operational demo document contains an unexpected file link"
                    );
                }
                continue;
            }
            byte[] content = content(fixture, anchorDate);
            List<Map<String, Object>> files = jdbcTemplate.queryForList(
                    "SELECT * FROM stored_file WHERE stored_file_id = ? AND company_id = ?",
                    fixture.fileId(),
                    DemoDocumentFixtureCatalog.COMPANY_ID
            );
            if (files.size() != 1) {
                throw new IllegalStateException("materialized demo stored file is missing");
            }
            verifyStoredFile(fixture, content, files.get(0));
            fileInstaller.verify(fixture.storageKey(), content);
            int updated = jdbcTemplate.update(
                    "UPDATE worker_document SET file_id = ?, updated_at = ?, version = version + 1 "
                            + "WHERE worker_document_id = ? AND company_id = ? AND file_id = ?",
                    originalFileId(fixture.documentId()),
                    Timestamp.from(now),
                    fixture.documentId(),
                    DemoDocumentFixtureCatalog.COMPANY_ID,
                    fixture.fileId()
            );
            if (updated != 1) {
                throw new IllegalStateException("failed to detach materialized operational demo file");
            }
            jdbcTemplate.update(
                    "DELETE FROM stored_file WHERE stored_file_id = ? AND company_id = ?",
                    fixture.fileId(),
                    DemoDocumentFixtureCatalog.COMPANY_ID
            );
            fileInstaller.cleanup(fixture.storageKey(), content);
        }
    }

    private List<DemoDocumentFixture> fixtures(LocalDate anchorDate) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT wd.worker_document_id, wd.worker_id, wd.task_id, wd.document_type,
                       wd.submission_status, wd.issue_date, wd.expiry_date, wd.destination,
                       wd.note, wd.file_id, w.display_name, w.nationality_code,
                       w.preferred_language, w.visa_type
                  FROM worker_document wd
                  JOIN worker w
                    ON w.worker_id = wd.worker_id
                   AND w.company_id = wd.company_id
                 WHERE wd.company_id = ?
                   AND wd.source = ?
                 ORDER BY wd.worker_document_id
                """,
                DemoDocumentFixtureCatalog.COMPANY_ID,
                SOURCE
        );
        List<DemoDocumentFixture> fixtures = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            UUID documentId = (UUID) row.get("worker_document_id");
            if (!DemoDocumentFileIds.isOperationalDemoDocumentId(documentId)) {
                continue;
            }
            SubmissionStatus status = SubmissionStatus.valueOf(row.get("submission_status").toString());
            if (status == SubmissionStatus.MISSING) {
                continue;
            }
            UUID workerId = (UUID) row.get("worker_id");
            PassportIdentity identity = DemoDocumentFixtureCatalog.identityForWorkerId(workerId);
            verifyWorkerIdentity(identity, row);
            DocumentType documentType = DocumentType.valueOf(row.get("document_type").toString());
            FixtureFormat format = format(documentType);
            String extension = format == FixtureFormat.PDF ? "pdf" : "png";
            LocalDate expiryDate = localDate(row.get("expiry_date"));
            String storageFilename = "legacy-%s-%s.%s".formatted(
                    documentType.name().toLowerCase(Locale.ROOT).replace('_', '-'),
                    documentId.toString().substring(documentId.toString().length() - 3),
                    extension
            );
            fixtures.add(new DemoDocumentFixture(
                    documentId,
                    DemoDocumentFileIds.materializedFileId(documentId),
                    workerId,
                    (UUID) row.get("task_id"),
                    documentType,
                    status,
                    days(anchorDate, localDate(row.get("issue_date"))),
                    days(anchorDate, expiryDate),
                    originalFilename(documentType, identity, documentId, extension),
                    storageFilename,
                    format == FixtureFormat.PDF ? "application/pdf" : "image/png",
                    format,
                    title(documentType),
                    Objects.toString(row.get("destination"), "NOT SET"),
                    Objects.toString(row.get("note"), "NOT SET"),
                    identity
            ));
        }
        if (fixtures.size() != 67) {
            throw new IllegalStateException(
                    "operational Demo Company must have exactly 67 non-missing document rows"
            );
        }
        return List.copyOf(fixtures);
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

    private void linkWorkerDocument(DemoDocumentFixture fixture, Instant now) {
        UUID currentFileId = workerDocumentFileId(fixture.documentId());
        if (fixture.fileId().equals(currentFileId)) {
            return;
        }
        if (!Objects.equals(originalFileId(fixture.documentId()), currentFileId)) {
            throw new IllegalStateException(
                    "operational demo document already links to an unexpected file"
            );
        }
        int updated = currentFileId == null
                ? jdbcTemplate.update(
                        "UPDATE worker_document SET file_id = ?, updated_at = ?, version = version + 1 "
                                + "WHERE worker_document_id = ? AND company_id = ? AND source = ? "
                                + "AND file_id IS NULL",
                        fixture.fileId(),
                        Timestamp.from(now),
                        fixture.documentId(),
                        DemoDocumentFixtureCatalog.COMPANY_ID,
                        SOURCE
                )
                : jdbcTemplate.update(
                        "UPDATE worker_document SET file_id = ?, updated_at = ?, version = version + 1 "
                                + "WHERE worker_document_id = ? AND company_id = ? AND source = ? "
                                + "AND file_id = ?",
                        fixture.fileId(),
                        Timestamp.from(now),
                        fixture.documentId(),
                        DemoDocumentFixtureCatalog.COMPANY_ID,
                        SOURCE,
                        currentFileId
                );
        if (updated != 1) {
            throw new IllegalStateException("failed to link materialized operational demo file");
        }
    }

    private void verifyWorkerDocumentLink(DemoDocumentFixture fixture) {
        if (!fixture.fileId().equals(workerDocumentFileId(fixture.documentId()))) {
            throw new IllegalStateException("operational demo document file link is missing");
        }
    }

    private UUID workerDocumentFileId(UUID documentId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT file_id FROM worker_document WHERE worker_document_id = ? AND company_id = ? "
                        + "AND source = ?",
                documentId,
                DemoDocumentFixtureCatalog.COMPANY_ID,
                SOURCE
        );
        if (rows.size() != 1) {
            throw new IllegalStateException("operational demo document metadata row is missing");
        }
        return (UUID) rows.get(0).get("file_id");
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
            throw new IllegalStateException(
                    "materialized demo stored file id belongs to different data"
            );
        }
    }

    private void verifyWorkerIdentity(PassportIdentity identity, Map<String, Object> row) {
        if (!identity.displayName().equals(row.get("display_name"))
                || !identity.nationalityCode().equals(row.get("nationality_code"))
                || !identity.preferredLanguage().equals(row.get("preferred_language"))
                || !identity.visaType().equals(row.get("visa_type"))) {
            throw new IllegalStateException(
                    "operational document metadata does not match the linked worker identity"
            );
        }
    }

    private byte[] content(DemoDocumentFixture fixture, LocalDate anchorDate) {
        return generator.generate(
                fixture,
                relativeDate(anchorDate, fixture.issueDays()),
                relativeDate(anchorDate, fixture.expiryDays())
        );
    }

    private FixtureFormat format(DocumentType type) {
        return switch (type) {
            case PASSPORT_COPY, ARC -> FixtureFormat.PNG;
            case CONTRACT, PERMIT -> FixtureFormat.PDF;
            default -> throw new IllegalArgumentException(
                    "unsupported operational demo document type: " + type
            );
        };
    }

    private String originalFilename(
            DocumentType type,
            PassportIdentity identity,
            UUID documentId,
            String extension
    ) {
        String name = switch (type) {
            case PASSPORT_COPY -> "여권사본";
            case ARC -> "외국인등록증";
            case CONTRACT -> "근로계약서";
            case PERMIT -> "고용허가서";
            default -> throw new IllegalArgumentException("unsupported document type: " + type);
        };
        return "%s_%s_%s.%s".formatted(
                name,
                identity.displayName().replace(" ", ""),
                documentId.toString().substring(documentId.toString().length() - 3),
                extension
        );
    }

    private String title(DocumentType type) {
        return switch (type) {
            case PASSPORT_COPY -> "Operational metadata passport copy";
            case ARC -> "Operational metadata residence card copy";
            case CONTRACT -> "Operational metadata employment contract";
            case PERMIT -> "Operational metadata employment permit";
            default -> throw new IllegalArgumentException("unsupported document type: " + type);
        };
    }

    private UUID originalFileId(UUID documentId) {
        return ORIGINAL_CONTRACT_DOCUMENT_ID.equals(documentId) ? ORIGINAL_CONTRACT_FILE_ID : null;
    }

    private Integer days(LocalDate anchorDate, LocalDate value) {
        return value == null ? null : Math.toIntExact(ChronoUnit.DAYS.between(anchorDate, value));
    }

    private LocalDate relativeDate(LocalDate anchorDate, Integer days) {
        return days == null ? null : anchorDate.plusDays(days);
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

    record MaterializedFiles(int fileCount, int imageCount, int pdfCount) {
    }
}
