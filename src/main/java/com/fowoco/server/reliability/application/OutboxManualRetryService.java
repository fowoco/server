package com.fowoco.server.reliability.application;

import com.fowoco.server.audit.application.port.AuditEventRepository;
import com.fowoco.server.audit.domain.ActorType;
import com.fowoco.server.audit.domain.AuditAction;
import com.fowoco.server.audit.domain.AuditEvent;
import com.fowoco.server.audit.domain.AuditTargetType;
import com.fowoco.server.auth.application.ActorAuthorizer;
import com.fowoco.server.auth.application.ActorContext;
import com.fowoco.server.auth.domain.UserRole;
import com.fowoco.server.common.error.ApiException;
import com.fowoco.server.common.error.ErrorCode;
import com.fowoco.server.common.id.UuidGenerator;
import com.fowoco.server.common.security.TenantDatabaseContext;
import com.fowoco.server.common.time.DatabaseTimestamp;
import com.fowoco.server.common.web.RequestMetadata;
import com.fowoco.server.reliability.application.error.OutboxErrorCode;
import com.fowoco.server.reliability.application.port.EventPublicationRepository;
import com.fowoco.server.reliability.application.port.OutboxManualRetryRepository;
import com.fowoco.server.reliability.domain.EventPublication;
import com.fowoco.server.reliability.domain.EventPublicationStatus;
import com.fowoco.server.reliability.domain.OutboxManualRetry;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxManualRetryService {

    private static final String AUDIT_EVENT_VERSION = "1.0";

    private final ActorAuthorizer actorAuthorizer;
    private final TenantDatabaseContext tenantDatabaseContext;
    private final EventPublicationRepository publicationRepository;
    private final OutboxManualRetryRepository retryRepository;
    private final AuditEventRepository auditEventRepository;
    private final UuidGenerator uuidGenerator;
    private final Clock clock;

    public OutboxManualRetryService(
            ActorAuthorizer actorAuthorizer,
            TenantDatabaseContext tenantDatabaseContext,
            EventPublicationRepository publicationRepository,
            OutboxManualRetryRepository retryRepository,
            AuditEventRepository auditEventRepository,
            UuidGenerator uuidGenerator,
            Clock clock
    ) {
        this.actorAuthorizer = actorAuthorizer;
        this.tenantDatabaseContext = tenantDatabaseContext;
        this.publicationRepository = publicationRepository;
        this.retryRepository = retryRepository;
        this.auditEventRepository = auditEventRepository;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional
    public OutboxManualRetryResult requestRetry(
            UUID eventId,
            long expectedVersion,
            String reason,
            String idempotencyKey,
            ActorContext actor,
            RequestMetadata metadata
    ) {
        actorAuthorizer.requireAnyRole(actor, UserRole.ADMIN);
        tenantDatabaseContext.setCompanyIdForCurrentTransaction(actor.companyId());

        String normalizedReason = normalizeReason(reason);
        String keyHash = sha256(normalizeIdempotencyKey(idempotencyKey));
        String requestHash = sha256(expectedVersion + "\n" + normalizedReason);
        OutboxManualRetry existing = retryRepository
                .findByEventIdAndCompanyIdAndKeyHash(eventId, actor.companyId(), keyHash)
                .orElse(null);
        if (existing != null) {
            return replay(existing, requestHash);
        }

        EventPublication publication = publicationRepository
                .findByIdAndCompanyIdForUpdate(eventId, actor.companyId())
                .orElseThrow(() -> new ApiException(OutboxErrorCode.OUTBOX_EVENT_NOT_FOUND));

        existing = retryRepository
                .findByEventIdAndCompanyIdAndKeyHash(eventId, actor.companyId(), keyHash)
                .orElse(null);
        if (existing != null) {
            return replay(existing, requestHash);
        }
        if (publication.version() != expectedVersion) {
            throw new ApiException(OutboxErrorCode.OUTBOX_EVENT_VERSION_CONFLICT);
        }
        if (publication.status() != EventPublicationStatus.REVIEW_REQUIRED
                || publication.leaseOwner() != null
                || publication.leaseExpiresAt() != null) {
            throw new ApiException(OutboxErrorCode.OUTBOX_EVENT_NOT_REVIEW_REQUIRED);
        }

        Instant now = DatabaseTimestamp.nowNotBefore(clock, publication.updatedAt());
        int previousAttemptCount = publication.attemptCount();
        publication.requestManualRetry(expectedVersion, now);
        EventPublication saved = publicationRepository.save(publication);
        OutboxManualRetry retry = retryRepository.append(new OutboxManualRetry(
                uuidGenerator.generate(),
                actor.companyId(),
                eventId,
                keyHash,
                requestHash,
                normalizedReason,
                actor.actorId(),
                metadata.requestId(),
                metadata.traceId(),
                previousAttemptCount,
                saved.status(),
                saved.version(),
                now
        ));
        appendAudit(retry, actor, metadata, now);

        return result(retry, false);
    }

    private OutboxManualRetryResult replay(OutboxManualRetry existing, String requestHash) {
        if (!existing.requestHash().equals(requestHash)) {
            throw new ApiException(OutboxErrorCode.OUTBOX_RETRY_IDEMPOTENCY_CONFLICT);
        }
        return result(existing, true);
    }

    private OutboxManualRetryResult result(OutboxManualRetry retry, boolean alreadyRequested) {
        return new OutboxManualRetryResult(
                retry.eventId(),
                retry.acceptedStatus(),
                retry.acceptedVersion(),
                retry.createdAt(),
                alreadyRequested
        );
    }

    private String normalizeReason(String reason) {
        if (reason == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        String normalized = reason.trim();
        if (normalized.length() < 10 || normalized.length() > 300) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED);
        }
        return normalized;
    }

    private String normalizeIdempotencyKey(String key) {
        if (key == null || key.isBlank() || key.length() > 100) {
            throw new ApiException(OutboxErrorCode.OUTBOX_RETRY_INVALID_IDEMPOTENCY_KEY);
        }
        return key.trim();
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }

    private void appendAudit(
            OutboxManualRetry retry,
            ActorContext actor,
            RequestMetadata metadata,
            Instant now
    ) {
        auditEventRepository.append(new AuditEvent(
                uuidGenerator.generate(),
                actor.companyId(),
                ActorType.HR_USER,
                actor.actorId(),
                UserRole.ADMIN,
                AuditAction.OUTBOX_MANUAL_RETRY_REQUESTED,
                AuditTargetType.OUTBOX_EVENT,
                retry.eventId(),
                metadata.requestId(),
                metadata.traceId(),
                AUDIT_EVENT_VERSION,
                "REVIEW_REQUIRED 이벤트 재처리 요청: " + retry.reason(),
                now
        ));
    }
}
