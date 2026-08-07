package com.fowoco.server.workerimport.application;

import com.fowoco.server.audit.application.port.AuditEventRepository;
import com.fowoco.server.audit.domain.ActorType;
import com.fowoco.server.audit.domain.AuditAction;
import com.fowoco.server.audit.domain.AuditEvent;
import com.fowoco.server.audit.domain.AuditTargetType;
import com.fowoco.server.auth.application.ActorContext;
import com.fowoco.server.auth.domain.UserRole;
import com.fowoco.server.common.error.ApiException;
import com.fowoco.server.common.id.UuidGenerator;
import com.fowoco.server.common.security.TenantDatabaseContext;
import com.fowoco.server.common.time.DatabaseTimestamp;
import com.fowoco.server.common.web.RequestMetadata;
import com.fowoco.server.file.application.port.FileStorage;
import com.fowoco.server.file.application.port.StoredFileRepository;
import com.fowoco.server.file.domain.StoredFile;
import com.fowoco.server.worker.application.port.WorkerRepository;
import com.fowoco.server.worker.domain.Worker;
import com.fowoco.server.workerimport.application.error.WorkerImportErrorCode;
import com.fowoco.server.workerimport.application.port.WorkerImportFileParser;
import com.fowoco.server.workerimport.application.port.WorkerImportRepository;
import com.fowoco.server.workerimport.domain.WorkerImportField;
import com.fowoco.server.workerimport.domain.WorkerImportRowStatus;
import com.fowoco.server.workerimport.domain.WorkerImportStatus;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkerImportService {

    private static final String AUDIT_EVENT_VERSION = "1";
    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final int MAX_PAGE_SIZE = 100;

    private final WorkerImportRepository repository;
    private final WorkerImportFileParser parser;
    private final FileStorage fileStorage;
    private final StoredFileRepository storedFileRepository;
    private final WorkerRepository workerRepository;
    private final AuditEventRepository auditRepository;
    private final TenantDatabaseContext tenantDatabaseContext;
    private final UuidGenerator uuidGenerator;
    private final Clock clock;
    private final Duration sourceRetention;

    public WorkerImportService(
            WorkerImportRepository repository,
            WorkerImportFileParser parser,
            FileStorage fileStorage,
            StoredFileRepository storedFileRepository,
            WorkerRepository workerRepository,
            AuditEventRepository auditRepository,
            TenantDatabaseContext tenantDatabaseContext,
            UuidGenerator uuidGenerator,
            Clock clock,
            @Value("${app.worker-import.source-retention:7d}") Duration sourceRetention
    ) {
        this.repository = repository;
        this.parser = parser;
        this.fileStorage = fileStorage;
        this.storedFileRepository = storedFileRepository;
        this.workerRepository = workerRepository;
        this.auditRepository = auditRepository;
        this.tenantDatabaseContext = tenantDatabaseContext;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
        this.sourceRetention = sourceRetention;
    }

    @Transactional
    public WorkerImportView create(
            String fileName,
            byte[] content,
            String idempotencyKey,
            ActorContext actor,
            RequestMetadata metadata
    ) {
        bindTenant(actor);
        if (content == null || content.length == 0) {
            throw new ApiException(WorkerImportErrorCode.IMPORT_FILE_EMPTY);
        }
        if (content.length > MAX_FILE_SIZE) {
            throw new ApiException(WorkerImportErrorCode.IMPORT_FILE_TOO_LARGE);
        }
        if (fileName == null || fileName.isBlank()) {
            throw new ApiException(WorkerImportErrorCode.IMPORT_FILE_TYPE_UNSUPPORTED);
        }
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        String keyHash = sha256(normalizedKey.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String requestHash = requestHash(fileName, content);
        var existing = repository.findByCreateKey(actor.companyId(), keyHash);
        if (existing.isPresent()) {
            if (!existing.get().createRequestHash().equals(requestHash)) {
                throw new ApiException(WorkerImportErrorCode.IMPORT_IDEMPOTENCY_CONFLICT);
            }
            return view(existing.get(), 0, 100);
        }

        ParsedWorkerImport parsed = parser.parse(fileName, content);
        Instant now = DatabaseTimestamp.now(clock);
        UUID sourceFileId = uuidGenerator.generate();
        String storageKey = sourceFileId.toString();
        String safeMimeType = fileName.toLowerCase(Locale.ROOT).endsWith(".csv")
                ? "text/csv"
                : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        StoredFile sourceFile = StoredFile.create(
                sourceFileId,
                actor.companyId(),
                fileName,
                safeMimeType,
                content.length,
                "WORKER_IMPORT_SOURCE",
                null,
                null,
                storageKey,
                now
        );
        fileStorage.store(storageKey, new ByteArrayInputStream(content), content.length, safeMimeType);
        storedFileRepository.insert(sourceFile);

        UUID importId = uuidGenerator.generate();
        WorkerImportJobRecord job = new WorkerImportJobRecord(
                importId,
                actor.companyId(),
                sourceFileId,
                actor.actorId(),
                WorkerImportStatus.UPLOADED,
                parsed.headers(),
                Map.of(),
                keyHash,
                requestHash,
                null,
                null,
                parsed.rows().size(),
                0,
                0,
                0,
                0,
                now.plus(sourceRetention),
                now,
                now,
                0
        );
        List<WorkerImportRowRecord> rows = new ArrayList<>();
        for (int index = 0; index < parsed.rows().size(); index++) {
            rows.add(new WorkerImportRowRecord(
                    uuidGenerator.generate(), importId, actor.companyId(), index + 2,
                    parsed.rows().get(index), Map.of(), Map.of(), List.of(),
                    WorkerImportRowStatus.PENDING, null, now, now, 0
            ));
        }
        repository.insert(job, rows);
        appendAudit(actor, AuditAction.WORKER_IMPORT_CREATED, importId, "근로자 명단 가져오기 생성", metadata, now);
        return new WorkerImportView(job, rows.stream().limit(100).toList(), 0, 100);
    }

    @Transactional(readOnly = true)
    public WorkerImportView find(UUID importId, int page, int size, ActorContext actor) {
        bindTenant(actor);
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new ApiException(WorkerImportErrorCode.IMPORT_FILE_INVALID);
        }
        WorkerImportJobRecord job = requireJob(actor.companyId(), importId);
        return view(job, page, size);
    }

    @Transactional
    public WorkerImportView saveMappings(
            UUID importId,
            long expectedVersion,
            Map<String, String> requestedMappings,
            ActorContext actor,
            RequestMetadata metadata
    ) {
        bindTenant(actor);
        WorkerImportJobRecord job = requireVersion(actor.companyId(), importId, expectedVersion);
        if (job.status() == WorkerImportStatus.COMMITTED) {
            throw new ApiException(WorkerImportErrorCode.IMPORT_STATE_INVALID);
        }
        Map<String, WorkerImportField> mappings = validateMappings(job.sourceHeaders(), requestedMappings);
        Instant now = DatabaseTimestamp.nowNotBefore(clock, job.createdAt());
        List<WorkerImportRowRecord> rows = repository.findAllRows(actor.companyId(), importId);
        for (WorkerImportRowRecord row : rows) {
            if (row.status() != WorkerImportRowStatus.COMMITTED && row.status() != WorkerImportRowStatus.EXCLUDED) {
                repository.updateRow(
                        actor.companyId(), importId, row.rowNumber(), row.overrideValues(), Map.of(), List.of(),
                        WorkerImportRowStatus.PENDING, null, now
                );
            }
        }
        Counts counts = counts(repository.findAllRows(actor.companyId(), importId));
        updateJob(job, WorkerImportStatus.MAPPED, mappings, counts, null, null, now);
        appendAudit(actor, AuditAction.WORKER_IMPORT_MAPPING_UPDATED, importId, "가져오기 열 연결 수정", metadata, now);
        return view(requireJob(actor.companyId(), importId), 0, 100);
    }

    @Transactional
    public WorkerImportView patchRows(
            UUID importId,
            long expectedVersion,
            List<WorkerImportRowPatch> patches,
            ActorContext actor,
            RequestMetadata metadata
    ) {
        bindTenant(actor);
        WorkerImportJobRecord job = requireVersion(actor.companyId(), importId, expectedVersion);
        if (job.status() == WorkerImportStatus.COMMITTED || job.mappings().isEmpty()) {
            throw new ApiException(WorkerImportErrorCode.IMPORT_STATE_INVALID);
        }
        Map<Integer, WorkerImportRowRecord> rows = repository.findAllRows(actor.companyId(), importId).stream()
                .collect(Collectors.toMap(WorkerImportRowRecord::rowNumber, Function.identity()));
        if (patches.stream().map(WorkerImportRowPatch::rowNumber).distinct().count() != patches.size()) {
            throw new ApiException(WorkerImportErrorCode.IMPORT_MAPPING_INVALID);
        }
        Instant now = DatabaseTimestamp.nowNotBefore(clock, job.createdAt());
        for (WorkerImportRowPatch patch : patches) {
            WorkerImportRowRecord row = rows.get(patch.rowNumber());
            if (row == null || row.status() == WorkerImportRowStatus.COMMITTED) {
                throw new ApiException(WorkerImportErrorCode.IMPORT_MAPPING_INVALID);
            }
            Map<String, String> overrides = new LinkedHashMap<>(row.overrideValues());
            patch.values().forEach((key, value) -> {
                WorkerImportField.fromKey(key);
                overrides.put(key, normalizeCell(value));
            });
            boolean excluded = patch.excluded() != null ? patch.excluded() : row.status() == WorkerImportRowStatus.EXCLUDED;
            repository.updateRow(
                    actor.companyId(), importId, row.rowNumber(), overrides, Map.of(), List.of(),
                    excluded ? WorkerImportRowStatus.EXCLUDED : WorkerImportRowStatus.PENDING,
                    null,
                    now
            );
        }
        Counts counts = counts(repository.findAllRows(actor.companyId(), importId));
        updateJob(job, WorkerImportStatus.MAPPED, job.mappings(), counts, null, null, now);
        appendAudit(actor, AuditAction.WORKER_IMPORT_ROWS_UPDATED, importId, "가져오기 행 수정", metadata, now);
        return view(requireJob(actor.companyId(), importId), 0, 100);
    }

    @Transactional
    public WorkerImportView validate(
            UUID importId,
            long expectedVersion,
            ActorContext actor,
            RequestMetadata metadata,
            boolean retry
    ) {
        bindTenant(actor);
        WorkerImportJobRecord job = requireVersion(actor.companyId(), importId, expectedVersion);
        if (job.mappings().isEmpty() || job.status() == WorkerImportStatus.COMMITTED) {
            throw new ApiException(WorkerImportErrorCode.IMPORT_STATE_INVALID);
        }
        List<WorkerImportRowRecord> rows = repository.findAllRows(actor.companyId(), importId);
        Map<String, Long> names = rows.stream()
                .filter(row -> row.status() != WorkerImportRowStatus.EXCLUDED
                        && row.status() != WorkerImportRowStatus.COMMITTED)
                .map(row -> effectiveValues(row, job.mappings()).get(WorkerImportField.DISPLAY_NAME.key()))
                .filter(Objects::nonNull)
                .map(value -> value.strip().toLowerCase(Locale.ROOT))
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        Instant now = DatabaseTimestamp.nowNotBefore(clock, job.createdAt());
        for (WorkerImportRowRecord row : rows) {
            if (row.status() == WorkerImportRowStatus.EXCLUDED || row.status() == WorkerImportRowStatus.COMMITTED) {
                continue;
            }
            Map<String, String> normalized = normalize(effectiveValues(row, job.mappings()));
            List<ImportValidationError> errors = validateRow(normalized, names, actor.companyId());
            repository.updateRow(
                    actor.companyId(), importId, row.rowNumber(), row.overrideValues(), normalized, errors,
                    errors.isEmpty() ? WorkerImportRowStatus.VALID : WorkerImportRowStatus.INVALID,
                    null,
                    now
            );
        }
        Counts counts = counts(repository.findAllRows(actor.companyId(), importId));
        WorkerImportStatus status = counts.invalid() > 0 || counts.valid() == 0
                ? WorkerImportStatus.REVIEW_REQUIRED
                : WorkerImportStatus.READY;
        updateJob(job, status, job.mappings(), counts, null, null, now);
        appendAudit(
                actor,
                retry ? AuditAction.WORKER_IMPORT_RETRIED : AuditAction.WORKER_IMPORT_VALIDATED,
                importId,
                retry ? "오류 행 재검증" : "가져오기 행 검증",
                metadata,
                now
        );
        return view(requireJob(actor.companyId(), importId), 0, 100);
    }

    @Transactional
    public WorkerImportView commit(
            UUID importId,
            long expectedVersion,
            Set<Integer> selectedRows,
            String idempotencyKey,
            ActorContext actor,
            RequestMetadata metadata
    ) {
        bindTenant(actor);
        WorkerImportJobRecord job = requireJob(actor.companyId(), importId);
        String keyHash = sha256(normalizeIdempotencyKey(idempotencyKey)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String requestHash = sha256(selectedRows.stream().sorted().map(String::valueOf)
                .collect(Collectors.joining(","))
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        if (keyHash.equals(job.lastCommitIdempotencyKeyHash())) {
            if (!requestHash.equals(job.lastCommitRequestHash())) {
                throw new ApiException(WorkerImportErrorCode.IMPORT_IDEMPOTENCY_CONFLICT);
            }
            return view(job, 0, 100);
        }
        if (job.version() != expectedVersion) {
            throw new ApiException(WorkerImportErrorCode.IMPORT_VERSION_CONFLICT);
        }
        if (job.status() != WorkerImportStatus.READY && job.status() != WorkerImportStatus.REVIEW_REQUIRED) {
            throw new ApiException(WorkerImportErrorCode.IMPORT_STATE_INVALID);
        }
        List<WorkerImportRowRecord> rows = repository.findAllRows(actor.companyId(), importId);
        List<WorkerImportRowRecord> candidates = rows.stream()
                .filter(row -> row.status() == WorkerImportRowStatus.VALID)
                .filter(row -> selectedRows.isEmpty() || selectedRows.contains(row.rowNumber()))
                .toList();
        if (candidates.isEmpty()) {
            throw new ApiException(WorkerImportErrorCode.IMPORT_NO_VALID_ROWS);
        }
        Instant now = DatabaseTimestamp.nowNotBefore(clock, job.createdAt());
        for (WorkerImportRowRecord row : candidates) {
            Map<String, String> values = row.normalizedValues();
            UUID workerId = uuidGenerator.generate();
            Worker worker = Worker.create(
                    workerId,
                    actor.companyId(),
                    values.get(WorkerImportField.DISPLAY_NAME.key()),
                    values.get(WorkerImportField.NATIONALITY_CODE.key()),
                    values.get(WorkerImportField.PREFERRED_LANGUAGE.key()),
                    values.get(WorkerImportField.VISA_TYPE.key()),
                    date(values, WorkerImportField.STAY_EXPIRY_DATE),
                    date(values, WorkerImportField.CONTRACT_START_DATE),
                    date(values, WorkerImportField.CONTRACT_END_DATE),
                    date(values, WorkerImportField.EMPLOYMENT_PERMIT_END_DATE),
                    date(values, WorkerImportField.EMPLOYMENT_ACTIVITY_END_DATE),
                    now
            );
            workerRepository.insert(worker);
            repository.updateRow(
                    actor.companyId(), importId, row.rowNumber(), row.overrideValues(), row.normalizedValues(),
                    List.of(), WorkerImportRowStatus.COMMITTED, workerId, now
            );
        }
        Counts counts = counts(repository.findAllRows(actor.companyId(), importId));
        WorkerImportStatus status = counts.invalid() > 0
                ? WorkerImportStatus.REVIEW_REQUIRED
                : counts.valid() > 0 ? WorkerImportStatus.READY : WorkerImportStatus.COMMITTED;
        updateJob(job, status, job.mappings(), counts, keyHash, requestHash, now);
        appendAudit(actor, AuditAction.WORKER_IMPORT_COMMITTED, importId, "정상 행 근로자 등록 확정", metadata, now);
        return view(requireJob(actor.companyId(), importId), 0, 100);
    }

    private Map<String, WorkerImportField> validateMappings(
            List<String> headers,
            Map<String, String> requested
    ) {
        if (requested == null || requested.isEmpty()) {
            throw new ApiException(WorkerImportErrorCode.IMPORT_MAPPING_INVALID);
        }
        Set<String> sourceHeaders = new HashSet<>(headers);
        Set<WorkerImportField> targets = new HashSet<>();
        Map<String, WorkerImportField> mappings = new LinkedHashMap<>();
        try {
            requested.forEach((source, targetKey) -> {
                if (!sourceHeaders.contains(source)) {
                    throw new IllegalArgumentException("unknown source");
                }
                WorkerImportField target = WorkerImportField.fromKey(targetKey);
                if (!targets.add(target)) {
                    throw new IllegalArgumentException("duplicate target");
                }
                mappings.put(source, target);
            });
        } catch (IllegalArgumentException exception) {
            throw new ApiException(WorkerImportErrorCode.IMPORT_MAPPING_INVALID);
        }
        if (!targets.contains(WorkerImportField.DISPLAY_NAME)) {
            throw new ApiException(WorkerImportErrorCode.IMPORT_MAPPING_INVALID);
        }
        return mappings;
    }

    private Map<String, String> effectiveValues(
            WorkerImportRowRecord row,
            Map<String, WorkerImportField> mappings
    ) {
        Map<String, String> result = new LinkedHashMap<>();
        mappings.forEach((source, target) -> result.put(target.key(), row.sourceValues().get(source)));
        result.putAll(row.overrideValues());
        return result;
    }

    private Map<String, String> normalize(Map<String, String> values) {
        Map<String, String> normalized = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            String normalizedValue = normalizeCell(value);
            if (WorkerImportField.NATIONALITY_CODE.key().equals(key) && normalizedValue != null) {
                normalizedValue = normalizedValue.toUpperCase(Locale.ROOT);
            }
            if (WorkerImportField.VISA_TYPE.key().equals(key) && normalizedValue != null) {
                normalizedValue = normalizedValue.toUpperCase(Locale.ROOT);
            }
            normalized.put(key, normalizedValue);
        });
        return normalized;
    }

    private List<ImportValidationError> validateRow(
            Map<String, String> values,
            Map<String, Long> names,
            UUID companyId
    ) {
        List<ImportValidationError> errors = new ArrayList<>();
        String displayName = values.get(WorkerImportField.DISPLAY_NAME.key());
        if (displayName == null || displayName.isBlank()) {
            errors.add(error(WorkerImportField.DISPLAY_NAME, "REQUIRED", "표시 이름을 입력해 주세요."));
        } else if (displayName.length() > 120) {
            errors.add(error(WorkerImportField.DISPLAY_NAME, "TOO_LONG", "표시 이름은 120자 이하여야 합니다."));
        } else if (names.getOrDefault(displayName.toLowerCase(Locale.ROOT), 0L) > 1
                || repository.existsWorkerByDisplayName(companyId, displayName)) {
            errors.add(error(WorkerImportField.DISPLAY_NAME, "DUPLICATE_CANDIDATE", "같은 표시 이름의 근로자가 있습니다."));
        }
        String nationality = values.get(WorkerImportField.NATIONALITY_CODE.key());
        if (nationality != null && !nationality.matches("[A-Z]{2}")) {
            errors.add(error(WorkerImportField.NATIONALITY_CODE, "INVALID_FORMAT", "국적은 ISO 2자리 코드로 입력해 주세요."));
        }
        bounded(values, WorkerImportField.PREFERRED_LANGUAGE, 20, errors);
        bounded(values, WorkerImportField.VISA_TYPE, 20, errors);
        for (WorkerImportField dateField : List.of(
                WorkerImportField.STAY_EXPIRY_DATE,
                WorkerImportField.CONTRACT_START_DATE,
                WorkerImportField.CONTRACT_END_DATE,
                WorkerImportField.EMPLOYMENT_PERMIT_END_DATE,
                WorkerImportField.EMPLOYMENT_ACTIVITY_END_DATE
        )) {
            parseDate(values.get(dateField.key()), dateField, errors);
        }
        LocalDate start = safeDate(values.get(WorkerImportField.CONTRACT_START_DATE.key()));
        LocalDate end = safeDate(values.get(WorkerImportField.CONTRACT_END_DATE.key()));
        if (start != null && end != null && end.isBefore(start)) {
            errors.add(error(WorkerImportField.CONTRACT_END_DATE, "DATE_ORDER", "계약 종료일은 시작일보다 빠를 수 없습니다."));
        }
        return List.copyOf(errors);
    }

    private void bounded(
            Map<String, String> values,
            WorkerImportField field,
            int max,
            List<ImportValidationError> errors
    ) {
        String value = values.get(field.key());
        if (value != null && value.length() > max) {
            errors.add(error(field, "TOO_LONG", field.key() + " 값이 너무 깁니다."));
        }
    }

    private void parseDate(String value, WorkerImportField field, List<ImportValidationError> errors) {
        if (value == null) {
            return;
        }
        try {
            LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            errors.add(error(field, "INVALID_DATE", "날짜는 YYYY-MM-DD 형식이어야 합니다."));
        }
    }

    private LocalDate safeDate(String value) {
        try {
            return value == null ? null : LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private LocalDate date(Map<String, String> values, WorkerImportField field) {
        String value = values.get(field.key());
        return value == null ? null : LocalDate.parse(value);
    }

    private ImportValidationError error(WorkerImportField field, String code, String message) {
        return new ImportValidationError(field.key(), code, message);
    }

    private String normalizeCell(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.strip();
        if (normalized.length() > 500 || normalized.matches("^[=+\\-@].*")) {
            throw new ApiException(WorkerImportErrorCode.IMPORT_FORMULA_NOT_ALLOWED);
        }
        return normalized;
    }

    private Counts counts(List<WorkerImportRowRecord> rows) {
        int valid = 0;
        int invalid = 0;
        int excluded = 0;
        int committed = 0;
        for (WorkerImportRowRecord row : rows) {
            switch (row.status()) {
                case VALID -> valid++;
                case INVALID -> invalid++;
                case EXCLUDED -> excluded++;
                case COMMITTED -> committed++;
                case PENDING -> { }
            }
        }
        return new Counts(valid, invalid, excluded, committed);
    }

    private void updateJob(
            WorkerImportJobRecord job,
            WorkerImportStatus status,
            Map<String, WorkerImportField> mappings,
            Counts counts,
            String commitKeyHash,
            String commitRequestHash,
            Instant now
    ) {
        boolean updated = repository.updateJob(
                job.companyId(), job.importId(), job.version(), status, mappings,
                counts.valid(), counts.invalid(), counts.excluded(), counts.committed(),
                commitKeyHash, commitRequestHash, now
        );
        if (!updated) {
            throw new ApiException(WorkerImportErrorCode.IMPORT_VERSION_CONFLICT);
        }
    }

    private WorkerImportView view(WorkerImportJobRecord job, int page, int size) {
        return new WorkerImportView(
                job,
                repository.findRows(job.companyId(), job.importId(), page * size, size),
                page,
                size
        );
    }

    private WorkerImportJobRecord requireJob(UUID companyId, UUID importId) {
        return repository.findJob(companyId, importId)
                .orElseThrow(() -> new ApiException(WorkerImportErrorCode.IMPORT_NOT_FOUND));
    }

    private WorkerImportJobRecord requireVersion(UUID companyId, UUID importId, long expectedVersion) {
        WorkerImportJobRecord job = requireJob(companyId, importId);
        if (job.version() != expectedVersion) {
            throw new ApiException(WorkerImportErrorCode.IMPORT_VERSION_CONFLICT);
        }
        return job;
    }

    private String normalizeIdempotencyKey(String value) {
        if (value == null || value.strip().length() < 8 || value.strip().length() > 100) {
            throw new ApiException(WorkerImportErrorCode.IMPORT_FILE_INVALID);
        }
        return value.strip();
    }

    private String requestHash(String fileName, byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(fileName.strip().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(content);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }

    private String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }

    private void appendAudit(
            ActorContext actor,
            AuditAction action,
            UUID importId,
            String summary,
            RequestMetadata metadata,
            Instant now
    ) {
        auditRepository.append(new AuditEvent(
                uuidGenerator.generate(), actor.companyId(), ActorType.HR_USER, actor.actorId(),
                actor.roles().stream().min(Comparator.comparingInt(this::rolePriority)).orElseThrow(),
                action, AuditTargetType.WORKER_IMPORT, importId, metadata.requestId(), metadata.traceId(),
                AUDIT_EVENT_VERSION, summary, now
        ));
    }

    private int rolePriority(UserRole role) {
        return switch (role) {
            case ADMIN -> 0;
            case HR -> 1;
            case VIEWER -> 2;
        };
    }

    private void bindTenant(ActorContext actor) {
        tenantDatabaseContext.setCompanyIdForCurrentTransaction(actor.companyId());
    }

    private record Counts(int valid, int invalid, int excluded, int committed) {
    }
}
