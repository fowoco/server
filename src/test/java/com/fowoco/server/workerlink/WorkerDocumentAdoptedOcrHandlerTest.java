package com.fowoco.server.workerlink;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fowoco.server.common.error.ApiException;
import com.fowoco.server.document.application.DocumentOcrService;
import com.fowoco.server.document.application.error.DocumentErrorCode;
import com.fowoco.server.reliability.application.NonRetryableEventHandlingException;
import com.fowoco.server.reliability.application.RetryableEventHandlingException;
import com.fowoco.server.reliability.domain.DomainEventEnvelope;
import com.fowoco.server.reliability.domain.EventActorType;
import com.fowoco.server.reliability.domain.SafeEventPayload;
import com.fowoco.server.workerlink.application.WorkerDocumentAdoptedOcrHandler;
import com.fowoco.server.workerlink.application.WorkerResponseDomainEvents;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkerDocumentAdoptedOcrHandlerTest {

    private static final UUID EVENT_ID = UUID.fromString("81000000-0000-0000-0000-000000000001");
    private static final UUID DOCUMENT_ID = UUID.fromString("81000000-0000-0000-0000-000000000002");
    private static final UUID COMPANY_ID = UUID.fromString("81000000-0000-0000-0000-000000000003");
    private static final UUID ACTOR_ID = UUID.fromString("81000000-0000-0000-0000-000000000004");

    private final DocumentOcrService documentOcrService = mock(DocumentOcrService.class);
    private final WorkerDocumentAdoptedOcrHandler handler =
            new WorkerDocumentAdoptedOcrHandler(documentOcrService);

    @Test
    void adoptedDocumentRequestsOcrWithEventIdAsIdempotencyKey() {
        DomainEventEnvelope event = event();

        handler.handle(event);

        verify(documentOcrService).create(
                org.mockito.ArgumentMatchers.eq(DOCUMENT_ID),
                org.mockito.ArgumentMatchers.eq(EVENT_ID.toString()),
                org.mockito.ArgumentMatchers.argThat(actor ->
                        actor.actorId().equals(ACTOR_ID) && actor.companyId().equals(COMPANY_ID)
                ),
                org.mockito.ArgumentMatchers.argThat(metadata ->
                        metadata.requestId().equals("adoption-request")
                )
        );
    }

    @Test
    void disabledOcrIsRetryableBecauseDeploymentConfigurationCanBeFixed() {
        DomainEventEnvelope event = event();
        when(documentOcrService.create(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        )).thenThrow(new ApiException(DocumentErrorCode.DOCUMENT_OCR_DISABLED));

        assertThatThrownBy(() -> handler.handle(event))
                .isInstanceOfSatisfying(
                        RetryableEventHandlingException.class,
                        failure -> org.assertj.core.api.Assertions.assertThat(failure.errorCode())
                                .isEqualTo(DocumentErrorCode.DOCUMENT_OCR_DISABLED.code())
                );
    }

    @Test
    void invalidAdoptedDocumentIsNotRetriedForever() {
        DomainEventEnvelope event = event();
        when(documentOcrService.create(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        )).thenThrow(new ApiException(DocumentErrorCode.DOCUMENT_OCR_UNSUPPORTED_TYPE));

        assertThatThrownBy(() -> handler.handle(event))
                .isInstanceOfSatisfying(
                        NonRetryableEventHandlingException.class,
                        failure -> org.assertj.core.api.Assertions.assertThat(failure.errorCode())
                                .isEqualTo(DocumentErrorCode.DOCUMENT_OCR_UNSUPPORTED_TYPE.code())
                );
    }

    private DomainEventEnvelope event() {
        return new DomainEventEnvelope(
                EVENT_ID,
                WorkerResponseDomainEvents.DOCUMENT_ADOPTED,
                "1",
                "WorkerDocument",
                DOCUMENT_ID,
                COMPANY_ID,
                EventActorType.HR_USER,
                ACTOR_ID,
                "adoption-request",
                null,
                Instant.parse("2026-08-13T00:00:00Z"),
                SafeEventPayload.empty()
        );
    }
}
