package com.fowoco.server.workerimport.application.port;

import com.fowoco.server.workerimport.application.ImportValidationError;
import com.fowoco.server.workerimport.application.WorkerImportCommitRecord;
import com.fowoco.server.workerimport.application.WorkerImportJobRecord;
import com.fowoco.server.workerimport.application.WorkerImportRowRecord;
import com.fowoco.server.workerimport.domain.WorkerImportField;
import com.fowoco.server.workerimport.domain.WorkerImportRowStatus;
import com.fowoco.server.workerimport.domain.WorkerImportStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface WorkerImportRepository {
    void insert(WorkerImportJobRecord job, List<WorkerImportRowRecord> rows);

    Optional<WorkerImportJobRecord> findJob(UUID companyId, UUID importId);

    Optional<WorkerImportJobRecord> findByCreateKey(UUID companyId, String keyHash);

    Optional<WorkerImportCommitRecord> findCommitByKey(UUID companyId, UUID importId, String keyHash);

    void insertCommit(WorkerImportCommitRecord record);

    List<WorkerImportRowRecord> findRows(UUID companyId, UUID importId, int offset, int limit);

    List<WorkerImportRowRecord> findAllRows(UUID companyId, UUID importId);

    boolean existsWorkerByDisplayName(UUID companyId, String displayName);

    boolean updateJob(
            UUID companyId,
            UUID importId,
            long expectedVersion,
            WorkerImportStatus status,
            Map<String, WorkerImportField> mappings,
            int validRows,
            int invalidRows,
            int excludedRows,
            int committedRows,
            Instant updatedAt
    );

    void updateRow(
            UUID companyId,
            UUID importId,
            int rowNumber,
            Map<String, String> overrideValues,
            Map<String, String> normalizedValues,
            List<ImportValidationError> errors,
            WorkerImportRowStatus status,
            UUID workerId,
            Instant updatedAt
    );
}
