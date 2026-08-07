package com.fowoco.server.workerimport.application;

import com.fowoco.server.workerimport.domain.WorkerImportField;
import com.fowoco.server.workerimport.domain.WorkerImportStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record WorkerImportJobRecord(
        UUID importId,
        UUID companyId,
        UUID sourceFileId,
        UUID createdBy,
        WorkerImportStatus status,
        List<String> sourceHeaders,
        Map<String, WorkerImportField> mappings,
        String createIdempotencyKeyHash,
        String createRequestHash,
        String lastCommitIdempotencyKeyHash,
        String lastCommitRequestHash,
        int totalRows,
        int validRows,
        int invalidRows,
        int excludedRows,
        int committedRows,
        Instant sourceFileExpiresAt,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
}
