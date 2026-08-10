package com.fowoco.server.workerlink.application;

import com.fowoco.server.auth.application.ActorContext;
import com.fowoco.server.common.error.ApiException;
import com.fowoco.server.common.error.ErrorCode;
import com.fowoco.server.common.security.TenantDatabaseContext;
import com.fowoco.server.common.web.RequestMetadata;
import com.fowoco.server.workerlink.application.error.WorkerLinkErrorCode;
import com.fowoco.server.workerlink.application.port.WorkerLinkRepository;
import com.fowoco.server.workerlink.application.port.WorkerLinkSmsMessage;
import com.fowoco.server.workerlink.application.port.WorkerLinkSmsProviderException;
import com.fowoco.server.workerlink.application.port.WorkerLinkSmsSender;
import com.fowoco.server.workerlink.domain.WorkerLink;
import com.fowoco.server.workerlink.domain.WorkerLinkDeliveryStatus;
import com.fowoco.server.workerlink.infrastructure.security.WorkerLinkHasher;
import com.fowoco.server.workerlink.infrastructure.sms.WorkerPortalUrlFactory;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkerLinkSmsDeliveryService {

    private final WorkerLinkRepository workerLinkRepository;
    private final WorkerLinkDeliveryService workerLinkDeliveryService;
    private final WorkerLinkSmsSender smsSender;
    private final WorkerPortalUrlFactory portalUrlFactory;
    private final WorkerLinkHasher workerLinkHasher;
    private final TenantDatabaseContext tenantDatabaseContext;
    private final Clock clock;

    public WorkerLinkSmsDeliveryService(
            WorkerLinkRepository workerLinkRepository,
            WorkerLinkDeliveryService workerLinkDeliveryService,
            WorkerLinkSmsSender smsSender,
            WorkerPortalUrlFactory portalUrlFactory,
            WorkerLinkHasher workerLinkHasher,
            TenantDatabaseContext tenantDatabaseContext,
            Clock clock
    ) {
        this.workerLinkRepository = workerLinkRepository;
        this.workerLinkDeliveryService = workerLinkDeliveryService;
        this.smsSender = smsSender;
        this.portalUrlFactory = portalUrlFactory;
        this.workerLinkHasher = workerLinkHasher;
        this.tenantDatabaseContext = tenantDatabaseContext;
        this.clock = clock;
    }

    @Transactional
    public WorkerLinkDeliveryResult deliver(
            WorkerLinkSmsDeliveryCommand command,
            ActorContext actor,
            RequestMetadata metadata
    ) {
        tenantDatabaseContext.setCompanyIdForCurrentTransaction(actor.companyId());
        WorkerLink link = workerLinkRepository
                .findByIdAndCompanyIdForUpdate(command.workerLinkId(), actor.companyId())
                .orElseThrow(() -> new ApiException(WorkerLinkErrorCode.WORKER_LINK_RESOURCE_NOT_FOUND));

        Instant now = clock.instant();
        if (!link.isUsable(now)) {
            throw new ApiException(WorkerLinkErrorCode.WORKER_LINK_NOT_ACTIVE);
        }
        verifyMatches(link.idempotencyKey(), workerLinkHasher.hash(command.idempotencyKey()));
        verifyMatches(link.tokenHash(), workerLinkHasher.hash(command.rawToken()));

        if (link.deliveryStatus() == WorkerLinkDeliveryStatus.SENT) {
            return WorkerLinkDeliveryResult.from(link);
        }

        String recipientPhone;
        try {
            recipientPhone = WorkerLinkSmsRecipient.normalizeKoreanMobile(command.recipientPhone());
        } catch (IllegalArgumentException exception) {
            throw new ApiException(
                    ErrorCode.VALIDATION_FAILED,
                    "recipient_phone은 국내 휴대전화 번호 형식이어야 합니다."
            );
        }

        String workerUrl = portalUrlFactory.create(command.rawToken()).toString();
        try {
            smsSender.send(new WorkerLinkSmsMessage(
                    recipientPhone,
                    "[FOWOCO] 회사에서 보낸 서류 제출 요청입니다.\n" + workerUrl
            ));
        } catch (WorkerLinkSmsProviderException exception) {
            if (exception.failureType() == WorkerLinkSmsProviderException.FailureType.DISABLED) {
                throw new ApiException(WorkerLinkErrorCode.WORKER_LINK_SMS_PROVIDER_DISABLED);
            }
            throw new ApiException(WorkerLinkErrorCode.WORKER_LINK_SMS_DELIVERY_FAILED);
        }

        return workerLinkDeliveryService.markSent(command.workerLinkId(), actor, metadata);
    }

    private void verifyMatches(String expectedHash, String actualHash) {
        if (!MessageDigest.isEqual(
                expectedHash.getBytes(StandardCharsets.US_ASCII),
                actualHash.getBytes(StandardCharsets.US_ASCII)
        )) {
            throw new ApiException(WorkerLinkErrorCode.WORKER_LINK_SMS_REQUEST_MISMATCH);
        }
    }
}
