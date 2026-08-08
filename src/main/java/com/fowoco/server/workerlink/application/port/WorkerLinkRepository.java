package com.fowoco.server.workerlink.application.port;

import com.fowoco.server.workerlink.domain.WorkerLink;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkerLinkRepository {

    void insert(WorkerLink workerLink);

    WorkerLink update(WorkerLink workerLink);

    Optional<WorkerLink> findByTokenHash(String tokenHash);

    Optional<WorkerLink> findByIdAndCompanyId(UUID workerLinkId, UUID companyId);

    Optional<WorkerLink> findActiveByTaskIdAndCompanyId(UUID taskId, UUID companyId);

    Optional<WorkerLink> findByTaskIdAndIdempotencyKey(UUID taskId, String idempotencyKey);

    List<WorkerLink> findAllByTaskIdAndCompanyId(UUID taskId, UUID companyId);
}
