package com.fowoco.server.document.application;

import com.fowoco.server.reliability.application.port.DomainEventHandler;
import com.fowoco.server.reliability.domain.DomainEventEnvelope;
import org.springframework.stereotype.Component;

@Component
public final class DocumentOcrExecutionRequestedHandler implements DomainEventHandler {

    private static final String HANDLER_NAME = "documentOcrExecution";

    private final DocumentOcrService documentOcrService;

    public DocumentOcrExecutionRequestedHandler(DocumentOcrService documentOcrService) {
        this.documentOcrService = documentOcrService;
    }

    @Override
    public String handlerName() {
        return HANDLER_NAME;
    }

    @Override
    public boolean supports(String eventType) {
        return DocumentOcrDomainEvents.EXECUTION_REQUESTED.equals(eventType);
    }

    @Override
    public void handle(DomainEventEnvelope event) {
        documentOcrService.executeFromOutbox(event.aggregateId(), event.companyId());
    }
}
