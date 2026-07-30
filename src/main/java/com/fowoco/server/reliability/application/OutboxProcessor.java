package com.fowoco.server.reliability.application;

import com.fowoco.server.reliability.application.port.DomainEventHandler;
import com.fowoco.server.reliability.config.OutboxWorkerIdentity;
import com.fowoco.server.reliability.domain.EventPublication;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OutboxProcessor {

    private static final Logger log = LoggerFactory.getLogger(OutboxProcessor.class);

    private final OutboxWorkerIdentity workerIdentity;
    private final OutboxClaimService claimService;
    private final OutboxReadService readService;
    private final OutboxHandlerRegistry handlerRegistry;
    private final OutboxHandlerTransaction handlerTransaction;
    private final OutboxCompletionTransaction completionTransaction;
    private final OutboxFailureTransaction failureTransaction;

    public OutboxProcessor(
            OutboxWorkerIdentity workerIdentity,
            OutboxClaimService claimService,
            OutboxReadService readService,
            OutboxHandlerRegistry handlerRegistry,
            OutboxHandlerTransaction handlerTransaction,
            OutboxCompletionTransaction completionTransaction,
            OutboxFailureTransaction failureTransaction
    ) {
        this.workerIdentity = workerIdentity;
        this.claimService = claimService;
        this.readService = readService;
        this.handlerRegistry = handlerRegistry;
        this.handlerTransaction = handlerTransaction;
        this.completionTransaction = completionTransaction;
        this.failureTransaction = failureTransaction;
    }

    public int processAvailable() {
        List<OutboxClaimService.ClaimedEvent> claimedEvents =
                claimService.claimBatch(workerIdentity.value());
        claimedEvents.forEach(this::processOne);
        return claimedEvents.size();
    }

    private void processOne(OutboxClaimService.ClaimedEvent claimedEvent) {
        EventPublication publication = readService.requirePublication(
                claimedEvent.eventId(),
                claimedEvent.companyId()
        );
        try {
            List<DomainEventHandler> handlers =
                    handlerRegistry.handlersFor(publication.eventType());
            for (DomainEventHandler handler : handlers) {
                handlerTransaction.deliver(
                        claimedEvent.eventId(),
                        claimedEvent.companyId(),
                        workerIdentity.value(),
                        handler
                );
            }
            completionTransaction.complete(
                    claimedEvent.eventId(),
                    claimedEvent.companyId(),
                    workerIdentity.value()
            );
        } catch (RuntimeException failure) {
            try {
                OutboxFailureTransaction.FailureOutcome outcome =
                        failureTransaction.recordFailure(
                                claimedEvent.eventId(),
                                claimedEvent.companyId(),
                                workerIdentity.value(),
                                failure
                        );
                log.warn(
                        "Outbox event processing failed: eventId={}, eventType={}, "
                                + "attempt={}, errorCode={}, retryScheduled={}",
                        claimedEvent.eventId(),
                        publication.eventType(),
                        publication.attemptCount(),
                        outcome.errorCode(),
                        outcome.retryScheduled()
                );
            } catch (RuntimeException recordingFailure) {
                log.error(
                        "Outbox failure state could not be recorded: eventId={}, eventType={}",
                        claimedEvent.eventId(),
                        publication.eventType()
                );
            }
        }
    }
}
