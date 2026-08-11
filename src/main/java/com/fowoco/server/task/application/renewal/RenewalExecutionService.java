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
import java.util.List;
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
        return telemetry.measure(metadata.requestId(), taskId, TOTAL, () ->
                executeMeasured(taskId, command, actor, metadata)
        );
    }

    private RenewalExecutionResult executeMeasured(
            UUID taskId,
            RenewalExecutionCommand command,
            ActorContext actor,
            RequestMetadata metadata
    ) {
        RenewalExecutionContext context = telemetry.measure(
                metadata.requestId(),
                taskId,
                CONTEXT_LOAD,
                () -> contextReader.load(taskId, command.expectedVersion(), actor)
        );
        RenewalRunRequest request = new RenewalRunRequest(
                uuidGenerator.generate(),
                uuidGenerator.generate(),
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
                    metadata.requestId(),
                    taskId,
                    RENEWAL_RUNTIME_CALL,
                    () -> runtimeClient.run(request, AiRuntimeCallContext.withoutTrace())
            );
            List<PreparedRenewalDocument> generatedDocuments =
                    "generate".equals(response.scenario())
                            ? telemetry.measure(
                                    metadata.requestId(),
                                    taskId,
                                    DOCUMENT_GENERATION,
                                    () -> generatedDocumentService.prepare(response.generatedDocuments())
                            )
                            : List.of();
            return telemetry.measure(
                    metadata.requestId(),
                    taskId,
                    RESULT_APPLY,
                    () -> resultApplier.apply(
                            taskId,
                            command.expectedVersion(),
                            response,
                            generatedDocuments,
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
