package com.fowoco.server.task.application.renewal;

import static com.fowoco.server.task.application.renewal.RenewalExecutionTelemetry.Stage.CONTEXT_LOAD;
import static com.fowoco.server.task.application.renewal.RenewalExecutionTelemetry.Stage.DOCUMENT_GENERATION;
import static com.fowoco.server.task.application.renewal.RenewalExecutionTelemetry.Stage.RENEWAL_RUNTIME_CALL;
import static com.fowoco.server.task.application.renewal.RenewalExecutionTelemetry.Stage.RESULT_APPLY;
import static com.fowoco.server.task.application.renewal.RenewalExecutionTelemetry.Stage.TOTAL;

import com.fowoco.server.aiintegration.application.error.AiRuntimeCallException;
import com.fowoco.server.aiintegration.application.error.AiRuntimeContractException;
import com.fowoco.server.aiintegration.application.error.AiRuntimeFailureCode;
import com.fowoco.server.aiintegration.application.model.AiRuntimeCallContext;
import com.fowoco.server.aiintegration.application.port.RenewalRuntimeClient;
import com.fowoco.server.aiintegration.application.renewal.RenewalRunRequest;
import com.fowoco.server.aiintegration.application.renewal.RenewalRunResponse;
import com.fowoco.server.auth.application.ActorContext;
import com.fowoco.server.common.error.ApiException;
import com.fowoco.server.common.id.UuidGenerator;
import com.fowoco.server.common.web.RequestMetadata;
import com.fowoco.server.task.application.error.TaskErrorCode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public final class RenewalExecutionService {

    private final RenewalExecutionContextReader contextReader;
    private final RenewalRuntimeClient runtimeClient;
    private final RenewalExecutionResultApplier resultApplier;
    private final GeneratedDocumentService generatedDocumentService;
    private final RenewalExecutionTelemetry telemetry;
    private final UuidGenerator uuidGenerator;

    RenewalExecutionService(
            RenewalExecutionContextReader contextReader,
            RenewalRuntimeClient runtimeClient,
            RenewalExecutionResultApplier resultApplier,
            GeneratedDocumentService generatedDocumentService,
            RenewalExecutionTelemetry telemetry,
            UuidGenerator uuidGenerator
    ) {
        this.contextReader = contextReader;
        this.runtimeClient = runtimeClient;
        this.resultApplier = resultApplier;
        this.generatedDocumentService = generatedDocumentService;
        this.telemetry = telemetry;
        this.uuidGenerator = uuidGenerator;
    }

    public RenewalExecutionResult execute(
            UUID taskId,
            RenewalExecutionCommand command,
            ActorContext actor,
            RequestMetadata metadata
    ) {
        UUID runtimeRequestId = uuidGenerator.generate();
        UUID attemptId = uuidGenerator.generate();
        return telemetry.measure(runtimeRequestId, metadata.requestId(), TOTAL, () ->
                executeMeasured(
                        taskId, command, actor, metadata, runtimeRequestId, attemptId, false, true
                )
        );
    }

    public RenewalExecutionResult executeWorkerContinuation(
            UUID taskId,
            String instruction,
            long expectedVersion,
            Map<String, String> slotAnswers,
            ActorContext delegatedActor,
            RequestMetadata metadata,
            UUID continuationEventId
    ) {
        UUID attemptId = UUID.nameUUIDFromBytes(
                (continuationEventId + ":renewal-attempt").getBytes(StandardCharsets.UTF_8)
        );
        RenewalExecutionCommand command = new RenewalExecutionCommand(
                instruction, expectedVersion, slotAnswers
        );
        return telemetry.measure(continuationEventId, metadata.requestId(), TOTAL, () ->
                executeMeasured(
                        taskId,
                        command,
                        delegatedActor,
                        metadata,
                        continuationEventId,
                        attemptId,
                        true,
                        false
                )
        );
    }

    public RenewalExecutionResult executeOcrContinuation(
            UUID taskId,
            String instruction,
            long expectedVersion,
            ActorContext approvingActor,
            RequestMetadata metadata,
            UUID continuationEventId
    ) {
        UUID attemptId = UUID.nameUUIDFromBytes(
                (continuationEventId + ":renewal-attempt").getBytes(StandardCharsets.UTF_8)
        );
        RenewalExecutionCommand command = new RenewalExecutionCommand(
                instruction, expectedVersion, Map.of()
        );
        return telemetry.measure(continuationEventId, metadata.requestId(), TOTAL, () ->
                executeMeasured(
                        taskId,
                        command,
                        approvingActor,
                        metadata,
                        continuationEventId,
                        attemptId,
                        false,
                        false
                )
        );
    }

    private RenewalExecutionResult executeMeasured(
            UUID taskId,
            RenewalExecutionCommand command,
            ActorContext actor,
            RequestMetadata metadata,
            UUID runtimeRequestId,
            UUID attemptId,
            boolean workerContinuation,
            boolean generateDocuments
    ) {
        RenewalExecutionContext context = telemetry.measure(
                runtimeRequestId,
                metadata.requestId(),
                CONTEXT_LOAD,
                () -> workerContinuation
                        ? contextReader.loadWorkerContinuation(
                                taskId,
                                command.expectedVersion(),
                                command.slotAnswers(),
                                actor
                        )
                        : contextReader.load(
                                taskId,
                                command.expectedVersion(),
                                command.slotAnswers(),
                                actor
                        )
        );
        RenewalRunRequest request = new RenewalRunRequest(
                runtimeRequestId,
                attemptId,
                command.instruction(),
                context.workerId(),
                context.companyId(),
                context.taskId(),
                context.slots(),
                context.documents(),
                context.ocrResult(),
                context.worker(),
                context.company(),
                context.task()
        );
        try {
            RenewalRunResponse response = telemetry.measure(
                    runtimeRequestId,
                    metadata.requestId(),
                    RENEWAL_RUNTIME_CALL,
                    () -> runtimeClient.run(request, AiRuntimeCallContext.withoutTrace())
            );
            List<PreparedRenewalDocument> generatedDocuments =
                    generateDocuments && "generate".equals(response.scenario())
                            ? telemetry.measure(
                                    runtimeRequestId,
                                    metadata.requestId(),
                                    DOCUMENT_GENERATION,
                                    () -> generatedDocumentService.prepare(response.generatedDocuments())
                            )
                            : List.of();
            return telemetry.measure(
                    runtimeRequestId,
                    metadata.requestId(),
                    RESULT_APPLY,
                    () -> resultApplier.apply(
                            taskId,
                            command.expectedVersion(),
                            response,
                            generatedDocuments,
                            context.submittedSlotAnswers(),
                            actor,
                            metadata
                    )
            );
        } catch (AiRuntimeContractException exception) {
            throw contractFailure(exception.failureCode());
        } catch (AiRuntimeCallException exception) {
            throw contractFailure(exception.failureCode());
        }
    }

    private ApiException contractFailure(AiRuntimeFailureCode code) {
        if (code == AiRuntimeFailureCode.INVALID_REQUEST_CONTRACT
                || code == AiRuntimeFailureCode.SENSITIVE_DATA_REJECTED
                || code == AiRuntimeFailureCode.UNEXPECTED_WORKFLOW) {
            return new ApiException(TaskErrorCode.RENEWAL_EXECUTION_NOT_ALLOWED);
        }
        return new ApiException(TaskErrorCode.RENEWAL_RUNTIME_UNAVAILABLE);
    }
}
