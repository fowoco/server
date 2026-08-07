package com.fowoco.server.workerimport.infrastructure.persistence;

import com.fowoco.server.workerimport.application.ImportValidationError;
import com.fowoco.server.workerimport.application.WorkerImportJobRecord;
import com.fowoco.server.workerimport.application.WorkerImportRowRecord;
import com.fowoco.server.workerimport.application.port.WorkerImportRepository;
import com.fowoco.server.workerimport.domain.WorkerImportField;
import com.fowoco.server.workerimport.domain.WorkerImportRowStatus;
import com.fowoco.server.workerimport.domain.WorkerImportStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Repository
public class JdbcWorkerImportRepository implements WorkerImportRepository {

    private static final String JOB_COLUMNS = """
            import_id, company_id, source_file_id, created_by, status,
            source_headers_json, mapping_json, create_idempotency_key_hash,
            create_request_hash, last_commit_idempotency_key_hash,
            last_commit_request_hash, total_rows, valid_rows, invalid_rows,
            excluded_rows, committed_rows, source_file_expires_at,
            created_at, updated_at, version
            """;
    private static final String ROW_COLUMNS = """
            import_row_id, import_id, company_id, row_number,
            source_values_json, override_values_json, normalized_values_json,
            validation_errors_json, status, worker_id, created_at, updated_at, version
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcWorkerImportRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void insert(WorkerImportJobRecord job, List<WorkerImportRowRecord> rows) {
        jdbcTemplate.update(
                """
                INSERT INTO worker_import_job (
                    import_id, company_id, source_file_id, created_by, status,
                    source_headers_json, mapping_json, create_idempotency_key_hash,
                    create_request_hash, total_rows, valid_rows, invalid_rows,
                    excluded_rows, committed_rows, source_file_expires_at,
                    created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                job.importId(), job.companyId(), job.sourceFileId(), job.createdBy(), job.status().name(),
                encode(job.sourceHeaders()), encodeMappings(job.mappings()), job.createIdempotencyKeyHash(),
                job.createRequestHash(), job.totalRows(), job.validRows(), job.invalidRows(),
                job.excludedRows(), job.committedRows(), timestamp(job.sourceFileExpiresAt()),
                timestamp(job.createdAt()), timestamp(job.updatedAt()), job.version()
        );
        for (WorkerImportRowRecord row : rows) {
            jdbcTemplate.update(
                    """
                    INSERT INTO worker_import_row (
                        import_row_id, import_id, company_id, row_number,
                        source_values_json, override_values_json, normalized_values_json,
                        validation_errors_json, status, worker_id, created_at, updated_at, version
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    row.importRowId(), row.importId(), row.companyId(), row.rowNumber(),
                    encode(row.sourceValues()), encode(row.overrideValues()), encode(row.normalizedValues()),
                    encode(row.validationErrors()), row.status().name(), row.workerId(),
                    timestamp(row.createdAt()), timestamp(row.updatedAt()), row.version()
            );
        }
    }

    @Override
    public Optional<WorkerImportJobRecord> findJob(UUID companyId, UUID importId) {
        return jdbcTemplate.query(
                "SELECT " + JOB_COLUMNS + " FROM worker_import_job WHERE company_id = ? AND import_id = ?",
                this::mapJob,
                companyId,
                importId
        ).stream().findFirst();
    }

    @Override
    public Optional<WorkerImportJobRecord> findByCreateKey(UUID companyId, String keyHash) {
        return jdbcTemplate.query(
                "SELECT " + JOB_COLUMNS
                        + " FROM worker_import_job WHERE company_id = ? AND create_idempotency_key_hash = ?",
                this::mapJob,
                companyId,
                keyHash
        ).stream().findFirst();
    }

    @Override
    public List<WorkerImportRowRecord> findRows(UUID companyId, UUID importId, int offset, int limit) {
        return jdbcTemplate.query(
                "SELECT " + ROW_COLUMNS
                        + " FROM worker_import_row WHERE company_id = ? AND import_id = ?"
                        + " ORDER BY row_number LIMIT ? OFFSET ?",
                this::mapRow,
                companyId,
                importId,
                limit,
                offset
        );
    }

    @Override
    public List<WorkerImportRowRecord> findAllRows(UUID companyId, UUID importId) {
        return jdbcTemplate.query(
                "SELECT " + ROW_COLUMNS
                        + " FROM worker_import_row WHERE company_id = ? AND import_id = ? ORDER BY row_number",
                this::mapRow,
                companyId,
                importId
        );
    }

    @Override
    public boolean existsWorkerByDisplayName(UUID companyId, String displayName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM worker WHERE company_id = ? AND LOWER(display_name) = LOWER(?)",
                Integer.class,
                companyId,
                displayName
        );
        return count != null && count > 0;
    }

    @Override
    public boolean updateJob(
            UUID companyId,
            UUID importId,
            long expectedVersion,
            WorkerImportStatus status,
            Map<String, WorkerImportField> mappings,
            int validRows,
            int invalidRows,
            int excludedRows,
            int committedRows,
            String commitKeyHash,
            String commitRequestHash,
            Instant updatedAt
    ) {
        return jdbcTemplate.update(
                """
                UPDATE worker_import_job
                   SET status = ?, mapping_json = ?, valid_rows = ?, invalid_rows = ?,
                       excluded_rows = ?, committed_rows = ?,
                       last_commit_idempotency_key_hash = COALESCE(?, last_commit_idempotency_key_hash),
                       last_commit_request_hash = COALESCE(?, last_commit_request_hash),
                       updated_at = ?, version = version + 1
                 WHERE company_id = ? AND import_id = ? AND version = ?
                """,
                status.name(), encodeMappings(mappings), validRows, invalidRows,
                excludedRows, committedRows, commitKeyHash, commitRequestHash,
                timestamp(updatedAt), companyId, importId, expectedVersion
        ) == 1;
    }

    @Override
    public void updateRow(
            UUID companyId,
            UUID importId,
            int rowNumber,
            Map<String, String> overrideValues,
            Map<String, String> normalizedValues,
            List<ImportValidationError> errors,
            WorkerImportRowStatus status,
            UUID workerId,
            Instant updatedAt
    ) {
        int updated = jdbcTemplate.update(
                """
                UPDATE worker_import_row
                   SET override_values_json = ?, normalized_values_json = ?,
                       validation_errors_json = ?, status = ?, worker_id = ?,
                       updated_at = ?, version = version + 1
                 WHERE company_id = ? AND import_id = ? AND row_number = ?
                """,
                encode(overrideValues), encode(normalizedValues), encode(errors), status.name(), workerId,
                timestamp(updatedAt), companyId, importId, rowNumber
        );
        if (updated != 1) {
            throw new IllegalStateException("worker import row update count must be one");
        }
    }

    private WorkerImportJobRecord mapJob(ResultSet resultSet, int rowNumber) throws SQLException {
        return new WorkerImportJobRecord(
                uuid(resultSet, "import_id"),
                uuid(resultSet, "company_id"),
                uuid(resultSet, "source_file_id"),
                uuid(resultSet, "created_by"),
                WorkerImportStatus.valueOf(resultSet.getString("status")),
                decodeStringList(resultSet.getString("source_headers_json")),
                decodeMappings(resultSet.getString("mapping_json")),
                resultSet.getString("create_idempotency_key_hash"),
                resultSet.getString("create_request_hash"),
                resultSet.getString("last_commit_idempotency_key_hash"),
                resultSet.getString("last_commit_request_hash"),
                resultSet.getInt("total_rows"),
                resultSet.getInt("valid_rows"),
                resultSet.getInt("invalid_rows"),
                resultSet.getInt("excluded_rows"),
                resultSet.getInt("committed_rows"),
                instant(resultSet, "source_file_expires_at"),
                instant(resultSet, "created_at"),
                instant(resultSet, "updated_at"),
                resultSet.getLong("version")
        );
    }

    private WorkerImportRowRecord mapRow(ResultSet resultSet, int ignored) throws SQLException {
        return new WorkerImportRowRecord(
                uuid(resultSet, "import_row_id"),
                uuid(resultSet, "import_id"),
                uuid(resultSet, "company_id"),
                resultSet.getInt("row_number"),
                decodeStringMap(resultSet.getString("source_values_json")),
                decodeStringMap(resultSet.getString("override_values_json")),
                decodeStringMap(resultSet.getString("normalized_values_json")),
                decodeErrors(resultSet.getString("validation_errors_json")),
                WorkerImportRowStatus.valueOf(resultSet.getString("status")),
                nullableUuid(resultSet, "worker_id"),
                instant(resultSet, "created_at"),
                instant(resultSet, "updated_at"),
                resultSet.getLong("version")
        );
    }

    private String encodeMappings(Map<String, WorkerImportField> mappings) {
        Map<String, String> encoded = new LinkedHashMap<>();
        mappings.forEach((source, target) -> encoded.put(source, target.key()));
        return encode(encoded);
    }

    private Map<String, WorkerImportField> decodeMappings(String json) {
        Map<String, WorkerImportField> result = new LinkedHashMap<>();
        decodeStringMap(json).forEach((source, target) -> result.put(source, WorkerImportField.fromKey(target)));
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> decodeStringMap(String json) {
        try {
            Map<String, Object> raw = objectMapper.readValue(json, Map.class);
            Map<String, String> result = new LinkedHashMap<>();
            raw.forEach((key, value) -> result.put(key, value == null ? null : value.toString()));
            return result;
        } catch (JacksonException exception) {
            throw new IllegalStateException("stored worker import map cannot be decoded", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> decodeStringList(String json) {
        try {
            List<Object> raw = objectMapper.readValue(json, List.class);
            return raw.stream().map(Object::toString).toList();
        } catch (JacksonException exception) {
            throw new IllegalStateException("stored worker import headers cannot be decoded", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private List<ImportValidationError> decodeErrors(String json) {
        try {
            List<Map<String, Object>> raw = objectMapper.readValue(json, List.class);
            List<ImportValidationError> result = new ArrayList<>();
            for (Map<String, Object> value : raw) {
                result.add(new ImportValidationError(
                        String.valueOf(value.get("field")),
                        String.valueOf(value.get("code")),
                        String.valueOf(value.get("message"))
                ));
            }
            return result;
        } catch (JacksonException exception) {
            throw new IllegalStateException("stored worker import errors cannot be decoded", exception);
        }
    }

    private String encode(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("worker import data cannot be encoded", exception);
        }
    }

    private UUID uuid(ResultSet resultSet, String column) throws SQLException {
        Object value = resultSet.getObject(column);
        return value instanceof UUID uuid ? uuid : UUID.fromString(value.toString());
    }

    private UUID nullableUuid(ResultSet resultSet, String column) throws SQLException {
        Object value = resultSet.getObject(column);
        return value == null ? null : value instanceof UUID uuid ? uuid : UUID.fromString(value.toString());
    }

    private Instant instant(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getTimestamp(column).toInstant();
    }

    private Timestamp timestamp(Instant instant) {
        return Timestamp.from(instant);
    }
}
