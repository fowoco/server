package com.fowoco.server.task.application.renewal;

import com.fowoco.server.common.error.ApiException;
import com.fowoco.server.document.application.DocumentOcrDomainEvents;
import com.fowoco.server.reliability.application.NonRetryableEventHandlingException;
import com.fowoco.server.reliability.application.RetryableEventHandlingException;
import com.fowoco.server.reliability.application.port.DomainEventHandler;
import com.fowoco.server.reliability.domain.DomainEventEnvelope;
import com.fowoco.server.task.application.error.TaskErrorCode;
import com.fowoco.server.workerlink.application.WorkerResponseDomainEvents;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public final class RenewalContinuationEventHandler implements DomainEventHandler {

    private static final String HANDLER_NAME = "renewalContinuation";
    private static final Set<String> SUPPORTED_EVENTS = Set.of(
            WorkerResponseDomainEvents.SLOT_ANSWERS_SUBMITTED,
            DocumentOcrDomainEvents.APPROVED
    );

    private final RenewalContinuationService continuationService;

    public RenewalContinuationEventHandler(RenewalContinuationService continuationService) {
        this.continuationService = continuationService;
    }

    @Override
    public String handlerName() {
        return HANDLER_NAME;
    }

    @Override
    public boolean supports(String eventType) {
        return SUPPORTED_EVENTS.contains(eventType);
    }

    @Override
    public void handle(DomainEventEnvelope event) {
        try {
            if (WorkerResponseDomainEvents.SLOT_ANSWERS_SUBMITTED.equals(event.eventType())) {
                continuationService.continueAfterWorkerAnswers(event);
            } else {
                continuationService.continueAfterOcrApproval(event);
            }
        } catch (ApiException exception) {
            if (exception.errorCode() == TaskErrorCode.RENEWAL_RUNTIME_UNAVAILABLE
                    || exception.errorCode() == TaskErrorCode.CONCURRENT_MODIFICATION) {
                throw new RetryableEventHandlingException(exception.errorCode().code());
            }
            throw new NonRetryableEventHandlingException(exception.errorCode().code());
        }
    }
}
