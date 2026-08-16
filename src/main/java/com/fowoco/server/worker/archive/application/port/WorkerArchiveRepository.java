package com.fowoco.server.worker.archive.application.port;

import com.fowoco.server.worker.archive.application.WorkerArchiveBlocker;
import com.fowoco.server.worker.archive.domain.WorkerArchive;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkerArchiveRepository {

    boolean lockWorker(UUID workerId, UUID companyId);

    Optional<WorkerArchive> find(UUID workerId, UUID companyId);

    List<WorkerArchiveBlocker> findOperationalBlockers(UUID workerId, UUID companyId, Instant now);

    boolean reserveWorkerVersion(UUID workerId, UUID companyId, long expectedVersion, Instant now);

    void insert(WorkerArchive archive);
}
