package com.fowoco.server.worker.archive.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "worker_archive")
public class WorkerArchiveJpaEntity {

    @Id
    @Column(name = "worker_id", nullable = false, updatable = false)
    private UUID workerId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "archived_at", nullable = false, updatable = false)
    private Instant archivedAt;

    protected WorkerArchiveJpaEntity() {
    }
}
