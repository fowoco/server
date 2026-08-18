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
import com.fowoco.server.worker.domain.WorkerDocumentSource;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
final class GeneratedDocumentService {

    private static final String GENERATED_DRAFT_PURPOSE = "AI_GENERATED_DRAFT";
    private static final Map<String, DocumentType> DOCUMENT_TYPES = Map.of(
            "standard_labor_contract_v6", DocumentType.CONTRACT,
            "employment_extension_application_v12_3", DocumentType.EMPLOYMENT_EXTENSION_APPLICATION,
            "immigration_integrated_application_v34", DocumentType.INTEGRATED_APPLICATION,
            "identity_guaranty_v129", DocumentType.IDENTITY_GUARANTY
    );
    private static final Map<String, Set<String>> TEMPLATE_IDS_BY_TASK_TYPE = Map.of(
            "RECONTRACT", Set.of("standard_labor_contract_v6"),
            "EMPLOYMENT_PERIOD_EXTENSION", Set.of("employment_extension_application_v12_3"),
            "STAY_PERIOD_EXTENSION", Set.of(
                    "immigration_integrated_application_v34",
                    "identity_guaranty_v129"
            )
    );
    private static final Map<String, Set<String>> REQUIRED_VALUES_BY_TEMPLATE = Map.of(
            "standard_labor_contract_v6", Set.of(
                    "employee_name",
                    "employee_birthdate",
                    "enterprise_name"
            ),
            "employment_extension_application_v12_3", Set.of(
                    "employee_1_name",
                    "employee_1_resident_number",
                    "employee_1_passport_number"
            ),
            "immigration_integrated_application_v34", Set.of(
                    "given_names",
                    "passport_number",
                    "birth_year",
                    "birth_month",
                    "birth_day"
            ),
            "identity_guaranty_v129", Set.of(
                    "foreign_name",
                    "foreign_birthdate",
                    "foreign_passport"
            )
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

    List<PreparedRenewalDocument> prepare(
            String taskType,
            List<RenewalGeneratedDocument> documents
    ) {
        Set<String> allowedTemplateIds = TEMPLATE_IDS_BY_TASK_TYPE.get(taskType);
        if (allowedTemplateIds == null) {
            throw new IllegalArgumentException("Unsupported Renewal task type");
        }
        List<RenewalGeneratedDocument> routedDocuments = documents.stream()
                .filter(document -> allowedTemplateIds.contains(document.templateId()))
                .toList();
        Set<String> routedTemplateIds = routedDocuments.stream()
                .map(RenewalGeneratedDocument::templateId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (!routedTemplateIds.equals(allowedTemplateIds)
                || routedTemplateIds.size() != routedDocuments.size()) {
            throw new AiRuntimeCallException(
                    AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT,
                    "Generated documents do not match the current task templates."
            );
        }
        routedDocuments.forEach(this::validateRequiredValues);
        return routedDocuments.stream()
                .map(this::prepare)
                .toList();
    }

    private void validateRequiredValues(RenewalGeneratedDocument document) {
        Set<String> required = REQUIRED_VALUES_BY_TEMPLATE.get(document.templateId());
        boolean missingRequiredValue = required == null
                || required.stream().anyMatch(key -> !hasValue(document.values().get(key)));
        if (missingRequiredValue) {
            throw new AiRuntimeCallException(
                    AiRuntimeFailureCode.INVALID_RESPONSE_CONTRACT,
                    "Generated document is missing a required mapped value."
            );
        }
    }

    private boolean hasValue(Object value) {
        return value != null && (!(value instanceof String text) || !text.isBlank());
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
                        WorkerDocumentSource.AI_GENERATED,
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

    DocumentType documentType(String templateId) {
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
