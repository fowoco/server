package com.fowoco.server.workerlink.application;

import com.fowoco.server.auth.application.ActorContext;
import com.fowoco.server.auth.domain.UserRole;
import com.fowoco.server.common.error.ApiException;
import com.fowoco.server.common.web.RequestMetadata;
import com.fowoco.server.document.application.DocumentOcrService;
import com.fowoco.server.document.application.error.DocumentErrorCode;
import com.fowoco.server.reliability.application.NonRetryableEventHandlingException;
import com.fowoco.server.reliability.application.RetryableEventHandlingException;
import com.fowoco.server.reliability.application.port.DomainEventHandler;
import com.fowoco.server.reliability.domain.DomainEventEnvelope;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public final class WorkerDocumentAdoptedOcrHandler implements DomainEventHandler {

    private static final String HANDLER_NAME = "workerDocumentOcrRequest";

    private final DocumentOcrService documentOcrService;

    public WorkerDocumentAdoptedOcrHandler(DocumentOcrService documentOcrService) {
        this.documentOcrService = documentOcrService;
    }

    @Override
    public String handlerName() {
        return HANDLER_NAME;
    }

    @Override
    public boolean supports(String eventType) {
        return WorkerResponseDomainEvents.DOCUMENT_ADOPTED.equals(eventType);
    }

    @Override
    public void handle(DomainEventEnvelope event) {
        ActorContext actor = new ActorContext(
                event.actorId(), event.companyId(), Set.of(UserRole.HR)
        );
        try {
            documentOcrService.create(
                    event.aggregateId(),
                    event.eventId().toString(),
                    actor,
                    new RequestMetadata(event.requestId(), event.traceId())
            );
        } catch (ApiException exception) {
            if (exception.errorCode() == DocumentErrorCode.DOCUMENT_OCR_DISABLED) {
                throw new RetryableEventHandlingException(exception.errorCode().code());
            }
            throw new NonRetryableEventHandlingException(exception.errorCode().code());
        }
    }
}
