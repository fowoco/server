package com.fowoco.server.worker.archive.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fowoco.server.worker.archive.application.WorkerArchiveBlocker;
import com.fowoco.server.worker.archive.application.WorkerArchiveEligibility;
import java.util.List;
import java.util.UUID;

public record WorkerArchiveEligibilityResponse(
        @JsonProperty("worker_id") UUID workerId,
        boolean archivable,
        List<WorkerArchiveBlocker> blockers,
        @JsonProperty("worker_version") long workerVersion
) {
    public static WorkerArchiveEligibilityResponse from(WorkerArchiveEligibility eligibility) {
        return new WorkerArchiveEligibilityResponse(
                eligibility.workerId(),
                eligibility.archivable(),
                eligibility.blockers(),
                eligibility.workerVersion()
        );
    }
}
