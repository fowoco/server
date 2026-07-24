package com.fowoco.server.worker.infrastructure.persistence;

import com.fowoco.server.worker.domain.Worker;
import com.fowoco.server.worker.domain.WorkerStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "worker")
public class WorkerJpaEntity {

    @Id
    @Column(name = "worker_id", nullable = false, updatable = false)
    private UUID workerId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(name = "nationality_code", length = 10)
    private String nationalityCode;

    @Column(name = "preferred_language", length = 20)
    private String preferredLanguage;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_status", nullable = false, length = 20)
    private WorkerStatus workStatus;

    @Column(name = "stay_expiry_date")
    private LocalDate stayExpiryDate;

    @Column(name = "contract_start_date")
    private LocalDate contractStartDate;

    @Column(name = "contract_end_date")
    private LocalDate contractEndDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected WorkerJpaEntity() {
    }

    private WorkerJpaEntity(
            UUID workerId,
            UUID companyId,
            String displayName,
            String nationalityCode,
            String preferredLanguage,
            WorkerStatus workStatus,
            LocalDate stayExpiryDate,
            LocalDate contractStartDate,
            LocalDate contractEndDate,
            Instant createdAt,
            Instant updatedAt,
            long version
    ) {
        this.workerId = workerId;
        this.companyId = companyId;
        this.displayName = displayName;
        this.nationalityCode = nationalityCode;
        this.preferredLanguage = preferredLanguage;
        this.workStatus = workStatus;
        this.stayExpiryDate = stayExpiryDate;
        this.contractStartDate = contractStartDate;
        this.contractEndDate = contractEndDate;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    public static WorkerJpaEntity fromDomain(Worker worker) {
        Objects.requireNonNull(worker, "worker must not be null");
        return new WorkerJpaEntity(
                worker.workerId(),
                worker.companyId(),
                worker.displayName(),
                worker.nationalityCode(),
                worker.preferredLanguage(),
                worker.workStatus(),
                worker.stayExpiryDate(),
                worker.contractStartDate(),
                worker.contractEndDate(),
                worker.createdAt(),
                worker.updatedAt(),
                worker.version()
        );
    }

    public Worker toDomain() {
        return new Worker(
                workerId,
                companyId,
                displayName,
                nationalityCode,
                preferredLanguage,
                workStatus,
                stayExpiryDate,
                contractStartDate,
                contractEndDate,
                createdAt,
                updatedAt,
                version
        );
    }

    public void applyState(Worker worker) {
        Objects.requireNonNull(worker, "worker must not be null");
        if (!workerId.equals(worker.workerId())
                || !companyId.equals(worker.companyId())
                || !createdAt.equals(worker.createdAt())) {
            throw new IllegalArgumentException("immutable worker fields must not change");
        }
        if (version != worker.version()) {
            throw new IllegalArgumentException("worker version does not match");
        }
        this.displayName = worker.displayName();
        this.nationalityCode = worker.nationalityCode();
        this.preferredLanguage = worker.preferredLanguage();
        this.workStatus = worker.workStatus();
        this.stayExpiryDate = worker.stayExpiryDate();
        this.contractStartDate = worker.contractStartDate();
        this.contractEndDate = worker.contractEndDate();
        this.updatedAt = worker.updatedAt();
    }
}
