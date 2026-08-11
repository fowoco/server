package com.fowoco.server.workerlink.application;

import com.fowoco.server.auth.application.ActorContext;
import com.fowoco.server.common.error.ApiException;
import com.fowoco.server.common.error.ErrorCode;
import com.fowoco.server.common.web.RequestMetadata;
import com.fowoco.server.workerlink.application.error.WorkerLinkErrorCode;
import com.fowoco.server.workerlink.application.port.WorkerLinkSmsMessage;
import com.fowoco.server.workerlink.application.port.WorkerLinkSmsProviderException;
import com.fowoco.server.workerlink.application.port.WorkerLinkSmsSender;
import com.fowoco.server.workerlink.infrastructure.sms.WorkerPortalUrlFactory;
import org.springframework.stereotype.Service;

@Service
public class WorkerLinkSmsDeliveryService {

    private final WorkerLinkSmsDeliveryStateService stateService;
    private final WorkerLinkSmsSender smsSender;
    private final WorkerPortalUrlFactory portalUrlFactory;

    public WorkerLinkSmsDeliveryService(
            WorkerLinkSmsDeliveryStateService stateService,
            WorkerLinkSmsSender smsSender,
            WorkerPortalUrlFactory portalUrlFactory
    ) {
        this.stateService = stateService;
        this.smsSender = smsSender;
        this.portalUrlFactory = portalUrlFactory;
    }

    public WorkerLinkDeliveryResult deliver(
            WorkerLinkSmsDeliveryCommand command,
            ActorContext actor,
            RequestMetadata metadata
    ) {
        String recipientPhone;
        try {
            recipientPhone = WorkerLinkSmsRecipient.normalizeKoreanMobile(command.recipientPhone());
        } catch (IllegalArgumentException exception) {
            throw new ApiException(
                    ErrorCode.VALIDATION_FAILED,
                    "recipient_phone은 국내 휴대전화 번호 형식이어야 합니다."
            );
        }

        WorkerLinkSmsDeliveryStart start = stateService.begin(command, actor, metadata);
        if (!start.sendRequired()) {
            return start.result();
        }

        String workerUrl = portalUrlFactory.create(command.rawToken()).toString();
        try {
            smsSender.send(new WorkerLinkSmsMessage(
                    recipientPhone,
                    "[FOWOCO] 회사에서 보낸 서류 제출 요청입니다.\n" + workerUrl
            ));
        } catch (WorkerLinkSmsProviderException exception) {
            if (exception.failureType() == WorkerLinkSmsProviderException.FailureType.DISABLED) {
                stateService.markRejected(command.workerLinkId(), actor, metadata);
                throw new ApiException(WorkerLinkErrorCode.WORKER_LINK_SMS_PROVIDER_DISABLED);
            }
            if (exception.failureType() == WorkerLinkSmsProviderException.FailureType.REJECTED) {
                stateService.markRejected(command.workerLinkId(), actor, metadata);
                throw new ApiException(WorkerLinkErrorCode.WORKER_LINK_SMS_DELIVERY_FAILED);
            }
            stateService.markReviewRequired(command.workerLinkId(), actor, metadata);
            throw new ApiException(WorkerLinkErrorCode.WORKER_LINK_SMS_DELIVERY_REVIEW_REQUIRED);
        }

        return stateService.markAccepted(command.workerLinkId(), actor, metadata);
    }
}
