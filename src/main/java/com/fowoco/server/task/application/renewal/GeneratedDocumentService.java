package com.fowoco.server.task.application.renewal;

import com.fowoco.server.aiintegration.application.document.DocumentGenerationClient;
import com.fowoco.server.aiintegration.application.document.DocumentGenerationRequest;
import com.fowoco.server.aiintegration.application.document.GeneratedDocumentFile;
import com.fowoco.server.aiintegration.application.error.AiRuntimeCallException;
import com.fowoco.server.aiintegration.application.error.AiRuntimeFailureCode;
import com.fowoco.server.aiintegration.application.renewal.RenewalGeneratedDocument;
import com.fowoco.server.auth.application.ActorContext;
import com.fowoco.server.common.web.RequestMetadata;
import com.fowoco.server.file.application.FileCreateCommand;
import com.fowoco.server.file.application.FileService;
import com.fowoco.server.file.domain.StoredFile;
import com.fowoco.server.worker.application.WorkerDocumentCreateCommand;
import com.fowoco.server.worker.application.WorkerDocumentPatchCommand;
import com.fowoco.server.worker.application.WorkerDocumentService;
import com.fowoco.server.worker.domain.DocumentType;
import com.fowoco.server.worker.domain.SubmissionStatus;
import com.fowoco.server.worker.domain.WorkerDocument;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
final class GeneratedDocumentService {

    private static final String GENERATED_DRAFT_PURPOSE = "AI_GENERATED_DRAFT";
    private static final Map<String, DocumentType> DOCUMENT_TYPES = Map.of(
            "standard_labor_contract_v6", DocumentType.CONTRACT,
            "employment_extension_application_v12_3", DocumentType.PERMIT,
            "immigration_integrated_application_v34", DocumentType.PERMIT,
            "identity_guaranty_v129", DocumentType.PERMIT
    );

    private final DocumentGenerationClient documentGenerationClient;
    private final FileService fileService;
    private final WorkerDocumentService workerDocumentService;

    GeneratedDocumentService(
            DocumentGenerationClient documentGenerationClient,
            FileService fileService,
            WorkerDocumentService workerDocumentService
    ) {
        this.documentGenerationClient = documentGenerationClient;
        this.fileService = fileService;
        this.workerDocumentService = workerDocumentService;
    }

    List<PreparedRenewalDocument> prepare(List<RenewalGeneratedDocument> documents) {
        return documents.stream()
                .map(this::prepare)
                .toList();
    }

    private PreparedRenewalDocument prepare(RenewalGeneratedDocument document) {
        GeneratedDocumentFile generated = documentGenerationClient.generate(new DocumentGenerationRequest(
                document.templateId(), document.format(), document.values()
        ));
        if (!document.format().equals(generated.format())) {
            throw new AiRuntimeCallException(
                    AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT,
                    "Generated document format does not match the request."
            );
        }
        return new PreparedRenewalDocument(document, generated);
    }

    List<GeneratedDocumentResult> store(
            UUID taskId,
            UUID workerId,
            List<PreparedRenewalDocument> documents,
            ActorContext actor,
            RequestMetadata metadata
    ) {
        List<GeneratedDocumentResult> results = new ArrayList<>(documents.size());
        for (PreparedRenewalDocument document : documents) {
            results.add(store(taskId, workerId, document, actor, metadata));
        }
        return List.copyOf(results);
    }

    private GeneratedDocumentResult store(
            UUID taskId,
            UUID workerId,
            PreparedRenewalDocument prepared,
            ActorContext actor,
            RequestMetadata metadata
    ) {
        RenewalGeneratedDocument descriptor = prepared.descriptor();
        GeneratedDocumentFile generated = prepared.file();
        byte[] content = generated.content();
        StoredFile storedFile = fileService.upload(
                new FileCreateCommand(
                        actor.companyId(),
                        generated.fileName(),
                        mimeType(descriptor.format()),
                        content.length,
                        GENERATED_DRAFT_PURPOSE,
                        taskId,
                        workerId,
                        new ByteArrayInputStream(content)
                ),
                actor,
                metadata
        );
        WorkerDocument document = workerDocumentService.register(
                new WorkerDocumentCreateCommand(
                        workerId,
                        taskId,
                        documentType(descriptor.templateId()),
                        SubmissionStatus.SUBMITTED,
                        null,
                        null,
                        "AI 생성 초안 - HR 검토 필요"
                ),
                actor
        );
        WorkerDocument linked = workerDocumentService.patch(
                new WorkerDocumentPatchCommand(
                        document.workerDocumentId(),
                        workerId,
                        null,
                        null,
                        null,
                        null,
                        null,
                        storedFile.storedFileId(),
                        document.version()
                ),
                actor,
                metadata
        );
        return new GeneratedDocumentResult(
                descriptor.templateId(),
                descriptor.format(),
                "GENERATED",
                storedFile.storedFileId(),
                linked.workerDocumentId()
        );
    }

    private DocumentType documentType(String templateId) {
        DocumentType type = DOCUMENT_TYPES.get(templateId);
        if (type == null) {
            throw new IllegalArgumentException("Unsupported Renewal document template");
        }
        return type;
    }

    private String mimeType(String format) {
        return switch (format) {
            case "hwp" -> "application/vnd.hancom.hwp";
            case "hwpx" -> "application/vnd.hancom.hwpx";
            default -> throw new IllegalArgumentException("Unsupported generated document format");
        };
    }
}
