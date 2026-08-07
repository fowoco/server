package com.fowoco.server.workerimport.api;

import com.fowoco.server.workerimport.application.WorkerImportView;
import com.fowoco.server.workerimport.domain.WorkerImportStatus;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record WorkerImportResponse(
        UUID importId,
        UUID sourceFileId,
        WorkerImportStatus status,
        List<String> sourceHeaders,
        Map<String, String> mappings,
        int totalRows,
        int validRows,
        int invalidRows,
        int excludedRows,
        int committedRows,
        Instant sourceFileExpiresAt,
        long version,
        List<WorkerImportRowResponse> rows,
        int page,
        int size
) {
    static WorkerImportResponse from(WorkerImportView view) {
        Map<String, String> mappings = new LinkedHashMap<>();
        view.job().mappings().forEach((source, target) -> mappings.put(source, target.key()));
        return new WorkerImportResponse(
                view.job().importId(),
                view.job().sourceFileId(),
                view.job().status(),
                view.job().sourceHeaders(),
                mappings,
                view.job().totalRows(),
                view.job().validRows(),
                view.job().invalidRows(),
                view.job().excludedRows(),
                view.job().committedRows(),
                view.job().sourceFileExpiresAt(),
                view.job().version(),
                view.rows().stream().map(WorkerImportRowResponse::from).toList(),
                view.page(),
                view.size()
        );
    }
}
