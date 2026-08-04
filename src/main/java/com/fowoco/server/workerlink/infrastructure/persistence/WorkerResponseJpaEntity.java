package com.fowoco.server.workerlink.infrastructure.persistence;

import com.fowoco.server.workerlink.domain.WorkerResponse;
import com.fowoco.server.workerlink.domain.WorkerResponseType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "worker_response")
public class WorkerResponseJpaEntity {

    @Id
    @Column(name = "response_id", nullable = false, updatable = false)
    private UUID responseId;

    @Column(name = "worker_link_id", nullable = false, updatable = false)
    private UUID workerLinkId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "response_type", nullable = false, updatable = false, length = 30)
    private WorkerResponseType responseType;

    @Column(name = "message", updatable = false, length = 1000)
    private String message;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    protected WorkerResponseJpaEntity() {
    }

    private WorkerResponseJpaEntity(
            UUID responseId,
            UUID workerLinkId,
            UUID companyId,
            WorkerResponseType responseType,
            String message,
            String idempotencyKey,
            Instant receivedAt
    ) {
        this.responseId = responseId;
        this.workerLinkId = workerLinkId;
        this.companyId = companyId;
        this.responseType = responseType;
        this.message = message;
        this.idempotencyKey = idempotencyKey;
        this.receivedAt = receivedAt;
    }

    public static WorkerResponseJpaEntity fromDomain(WorkerResponse response) {
        Objects.requireNonNull(response, "response must not be null");
        return new WorkerResponseJpaEntity(
                response.responseId(),
                response.workerLinkId(),
                response.companyId(),
                response.responseType(),
                response.message(),
                response.idempotencyKey(),
                response.receivedAt()
        );
    }

    public WorkerResponse toDomain() {
        return new WorkerResponse(
                responseId,
                workerLinkId,
                companyId,
                responseType,
                message,
                idempotencyKey,
                receivedAt
        );
    }
}
