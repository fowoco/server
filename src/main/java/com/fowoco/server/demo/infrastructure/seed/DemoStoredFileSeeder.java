package com.fowoco.server.demo.infrastructure.seed;

import com.fowoco.server.demo.infrastructure.seed.DemoOperationalSeedCatalog.StoredFileSeed;
import com.fowoco.server.file.application.port.StoredFileRepository;
import com.fowoco.server.file.domain.ScanStatus;
import com.fowoco.server.file.domain.StoredFile;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

final class DemoStoredFileSeeder {

    private final StoredFileRepository repository;
    private final JdbcTemplate jdbcTemplate;
    private final DemoFileFixtureInstaller fixtureInstaller;

    DemoStoredFileSeeder(
            StoredFileRepository repository,
            JdbcTemplate jdbcTemplate,
            DemoFileFixtureInstaller fixtureInstaller
    ) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.fixtureInstaller = Objects.requireNonNull(
                fixtureInstaller,
                "fixtureInstaller must not be null"
        );
    }

    void seed(StoredFileSeed seed, DemoOperationalSeedContext context) {
        List<StoredFile> existing = find(seed.storedFileId());
        if (!existing.isEmpty()) {
            verifyMetadata(existing.get(0), seed, context);
            fixtureInstaller.install(seed);
            return;
        }
        fixtureInstaller.install(seed);
        repository.insert(StoredFile.create(
                seed.storedFileId(),
                context.companyId(),
                seed.name(),
                seed.mimeType(),
                fixtureInstaller.expectedSize(seed),
                seed.purpose(),
                seed.taskId(),
                seed.workerId(),
                seed.storageKey(),
                context.now()
        ));
    }

    void verifyExisting(StoredFileSeed seed, DemoOperationalSeedContext context) {
        List<StoredFile> existing = find(seed.storedFileId());
        if (existing.size() != 1) {
            throw new IllegalStateException("a reserved demo stored file was not seeded");
        }
        verifyMetadata(existing.get(0), seed, context);
        fixtureInstaller.verify(seed);
    }

    private List<StoredFile> find(UUID storedFileId) {
        return jdbcTemplate.query(
                "SELECT stored_file_id, company_id, name, mime_type, size, purpose, task_id, "
                        + "worker_id, storage_key, scan_status, verified, created_at "
                        + "FROM stored_file WHERE stored_file_id = ?",
                (resultSet, rowNumber) -> new StoredFile(
                        resultSet.getObject("stored_file_id", UUID.class),
                        resultSet.getObject("company_id", UUID.class),
                        resultSet.getString("name"),
                        resultSet.getString("mime_type"),
                        resultSet.getLong("size"),
                        resultSet.getString("purpose"),
                        resultSet.getObject("task_id", UUID.class),
                        resultSet.getObject("worker_id", UUID.class),
                        resultSet.getString("storage_key"),
                        ScanStatus.valueOf(resultSet.getString("scan_status")),
                        resultSet.getBoolean("verified"),
                        resultSet.getTimestamp("created_at").toInstant()
                ),
                storedFileId
        );
    }

    private void verifyMetadata(
            StoredFile file,
            StoredFileSeed seed,
            DemoOperationalSeedContext context
    ) {
        if (!seed.storedFileId().equals(file.storedFileId())
                || !context.companyId().equals(file.companyId())
                || !seed.name().equals(file.name())
                || !seed.mimeType().equals(file.mimeType())
                || fixtureInstaller.expectedSize(seed) != file.size()
                || !seed.purpose().equals(file.purpose())
                || !Objects.equals(seed.taskId(), file.taskId())
                || !Objects.equals(seed.workerId(), file.workerId())
                || !seed.storageKey().equals(file.storageKey())
                || file.scanStatus() != ScanStatus.NOT_SCANNED
                || file.verified()) {
            throw new IllegalStateException(
                    "a reserved demo stored file id already belongs to different file data"
            );
        }
    }
}
