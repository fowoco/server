package com.fowoco.server.worker.archive.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fowoco.server.worker.archive.domain.WorkerArchive;
import java.time.Instant;
import java.util.UUID;

public record WorkerArchiveResponse(
        @JsonProperty("worker_id") UUID workerId,
        @JsonProperty("archived_at") Instant archivedAt,
        @JsonProperty("archived_by") UUID archivedBy,
        @JsonProperty("archive_reason") String archiveReason,
        @JsonProperty("worker_version") long workerVersion
) {
    public static WorkerArchiveResponse from(WorkerArchive archive) {
        return new WorkerArchiveResponse(
                archive.workerId(),
                archive.archivedAt(),
                archive.archivedBy(),
                archive.archiveReason(),
                archive.workerVersion()
        );
    }
}
