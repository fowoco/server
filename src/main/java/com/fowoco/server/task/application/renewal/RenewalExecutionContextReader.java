package com.fowoco.server.task.application.renewal;

import com.fowoco.server.aiintegration.application.renewal.RenewalCompanySnapshot;
import com.fowoco.server.aiintegration.application.renewal.RenewalDocumentInput;
import com.fowoco.server.aiintegration.application.renewal.RenewalTaskSnapshot;
import com.fowoco.server.aiintegration.application.renewal.RenewalWorkerSnapshot;
import com.fowoco.server.auth.application.ActorAuthorizer;
import com.fowoco.server.auth.application.ActorContext;
import com.fowoco.server.common.error.ApiException;
import com.fowoco.server.common.security.TenantDatabaseContext;
import com.fowoco.server.company.application.port.CompanyRepository;
import com.fowoco.server.company.domain.Company;
import com.fowoco.server.document.application.DocumentOcrResultPayload;
import com.fowoco.server.document.application.port.DocumentOcrRunRepository;
import com.fowoco.server.document.application.port.OcrResultCipher;
import com.fowoco.server.document.domain.DocumentOcrRun;
import com.fowoco.server.document.domain.DocumentOcrRunStatus;
import com.fowoco.server.task.application.TaskContentCodec;
import com.fowoco.server.task.application.error.TaskErrorCode;
import com.fowoco.server.task.application.port.TaskRepository;
import com.fowoco.server.task.domain.Task;
import com.fowoco.server.task.domain.TaskTargetType;
import com.fowoco.server.task.domain.TaskType;
import com.fowoco.server.worker.application.WorkerDocumentSearchQuery;
import com.fowoco.server.worker.application.port.WorkerDocumentRepository;
import com.fowoco.server.worker.application.port.WorkerRepository;
import com.fowoco.server.worker.domain.Worker;
import com.fowoco.server.worker.domain.WorkerDocument;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
class RenewalExecutionContextReader {

    private static final String RENEWAL_WORKFLOW = "WF-STY-001";
    private static final Set<TaskType> RENEWAL_TASK_TYPES = Set.of(
            TaskType.RECONTRACT,
            TaskType.STAY_PERIOD_EXTENSION,
            TaskType.EMPLOYMENT_PERIOD_EXTENSION
    );

    private final ActorAuthorizer authorizer;
    private final TenantDatabaseContext tenantContext;
    private final TaskRepository taskRepository;
    private final WorkerRepository workerRepository;
    private final CompanyRepository companyRepository;
    private final WorkerDocumentRepository workerDocumentRepository;
    private final DocumentOcrRunRepository ocrRunRepository;
    private final OcrResultCipher ocrResultCipher;
    private final TaskContentCodec taskContentCodec;
    private final RenewalSlotAnswerValidator slotAnswerValidator;
    private final ObjectMapper objectMapper;

    RenewalExecutionContextReader(
            ActorAuthorizer authorizer,
            TenantDatabaseContext tenantContext,
            TaskRepository taskRepository,
            WorkerRepository workerRepository,
            CompanyRepository companyRepository,
            WorkerDocumentRepository workerDocumentRepository,
            DocumentOcrRunRepository ocrRunRepository,
            OcrResultCipher ocrResultCipher,
            TaskContentCodec taskContentCodec,
            RenewalSlotAnswerValidator slotAnswerValidator,
            ObjectMapper objectMapper
    ) {
        this.authorizer = authorizer;
        this.tenantContext = tenantContext;
        this.taskRepository = taskRepository;
        this.workerRepository = workerRepository;
        this.companyRepository = companyRepository;
        this.workerDocumentRepository = workerDocumentRepository;
        this.ocrRunRepository = ocrRunRepository;
        this.ocrResultCipher = ocrResultCipher;
        this.taskContentCodec = taskContentCodec;
        this.slotAnswerValidator = slotAnswerValidator;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    RenewalExecutionContext load(
            UUID taskId,
            long expectedVersion,
            Map<String, String> submittedSlotAnswers,
            ActorContext actor
    ) {
        authorizer.requireHrWrite(actor);
        tenantContext.setCompanyIdForCurrentTransaction(actor.companyId());
        Task task = taskRepository.findByIdAndCompanyId(taskId, actor.companyId())
                .orElseThrow(() -> new ApiException(TaskErrorCode.TASK_NOT_FOUND));
        validateTask(task, expectedVersion);
        Worker worker = workerRepository.findByWorkerIdAndCompanyId(task.workerId(), actor.companyId())
                .orElseThrow(() -> new ApiException(TaskErrorCode.WORKER_NOT_FOUND));
        Company company = companyRepository.findById(actor.companyId())
                .orElseThrow(() -> new ApiException(TaskErrorCode.RENEWAL_EXECUTION_NOT_ALLOWED));
        Map<String, Object> businessData = new LinkedHashMap<>(
                taskContentCodec.decodeBusinessData(task.businessDataJson())
        );
        Map<String, String> normalizedAnswers = slotAnswerValidator.validate(
                submittedSlotAnswers,
                businessData.get("renewal_execution")
        );
        businessData.remove("renewal_execution");
        Map<String, Object> storedRenewalInputs = renewalInputs(businessData.remove("renewal_inputs"));
        Map<String, Object> slots = buildSlots(
                businessData,
                storedRenewalInputs,
                normalizedAnswers,
                worker,
                company
        );
        OcrContext ocr = loadOcrContext(worker, actor.companyId());
        return new RenewalExecutionContext(
                task.taskId(),
                task.companyId(),
                task.workerId(),
                Collections.unmodifiableMap(new LinkedHashMap<>(slots)),
                normalizedAnswers,
                ocr.documents(),
                ocr.latestResult(),
                toWorkerSnapshot(worker),
                toCompanySnapshot(company),
                toTaskSnapshot(task, businessData)
        );
    }

    private void validateTask(Task task, long expectedVersion) {
        if (task.version() != expectedVersion) {
            throw new ApiException(TaskErrorCode.CONCURRENT_MODIFICATION);
        }
        if (task.targetType() != TaskTargetType.WORKER
                || task.status().isTerminal()
                || !RENEWAL_WORKFLOW.equals(task.workflowId())
                || !RENEWAL_TASK_TYPES.contains(task.taskType())) {
            throw new ApiException(TaskErrorCode.RENEWAL_EXECUTION_NOT_ALLOWED);
        }
    }

    private Map<String, Object> buildSlots(
            Map<String, Object> businessData,
            Map<String, Object> storedRenewalInputs,
            Map<String, String> submittedSlotAnswers,
            Worker worker,
            Company company
    ) {
        Map<String, Object> slots = new LinkedHashMap<>(businessData);
        slots.putAll(storedRenewalInputs);
        slots.putAll(submittedSlotAnswers);
        put(slots, "worker_id", worker.workerId());
        put(slots, "company_id", company.companyId());
        put(slots, "display_name", worker.displayName());
        put(slots, "nationality_code", worker.nationalityCode());
        put(slots, "preferred_language", worker.preferredLanguage());
        put(slots, "work_status", worker.workStatus().name());
        put(slots, "stay_expiry_date", worker.stayExpiryDate());
        put(slots, "contract_start_date", worker.contractStartDate());
        put(slots, "contract_end_date", worker.contractEndDate());
        put(slots, "employment_permit_end_date", worker.employmentPermitEndDate());
        put(slots, "employment_activity_end_date", worker.employmentActivityEndDate());
        put(slots, "enterprise_name", company.name());
        return slots;
    }

    private Map<String, Object> renewalInputs(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> inputs = new LinkedHashMap<>();
        map.forEach((key, input) -> {
            if (key instanceof String stringKey && input != null) {
                inputs.put(stringKey, input);
            }
        });
        return Map.copyOf(inputs);
    }

    private void put(Map<String, Object> values, String key, Object value) {
        if (value instanceof LocalDate date) {
            values.putIfAbsent(key, date.toString());
        } else if (value != null) {
            values.putIfAbsent(key, value.toString());
        }
    }

    private OcrContext loadOcrContext(Worker worker, UUID companyId) {
        List<WorkerDocument> workerDocuments = workerDocumentRepository.findPage(
                companyId,
                new WorkerDocumentSearchQuery(worker.workerId(), null, null, null, null, 0, 100)
        );
        List<RenewalDocumentInput> documents = new ArrayList<>();
        List<ApprovedOcr> approvedResults = new ArrayList<>();
        for (WorkerDocument document : workerDocuments) {
            Optional<ApprovedOcr> approved = approvedOcr(document, companyId);
            if (approved.isEmpty()) {
                continue;
            }
            ApprovedOcr result = approved.get();
            approvedResults.add(result);
            Map<String, Object> hints = new LinkedHashMap<>();
            hints.put("workerDocumentId", document.workerDocumentId().toString());
            hints.put("submissionStatus", document.submissionStatus().name());
            if (document.expiryDate() != null) {
                hints.put("expiryDate", document.expiryDate().toString());
            }
            documents.add(new RenewalDocumentInput(
                    document.documentType().name(),
                    null,
                    result.fields(),
                    hints
            ));
        }
        Map<String, Object> latest = approvedResults.stream()
                .max(Comparator.comparing(ApprovedOcr::updatedAt))
                .map(ApprovedOcr::payload)
                .orElse(null);
        return new OcrContext(List.copyOf(documents), latest);
    }

    private Optional<ApprovedOcr> approvedOcr(WorkerDocument document, UUID companyId) {
        if (!ocrResultCipher.isAvailable()) {
            return Optional.empty();
        }
        return ocrRunRepository.findLatestByDocumentIdAndCompanyId(document.workerDocumentId(), companyId)
                .filter(run -> run.status() == DocumentOcrRunStatus.APPROVED)
                .map(this::decryptOcr);
    }

    private ApprovedOcr decryptOcr(DocumentOcrRun run) {
        try {
            byte[] plaintext = ocrResultCipher.decrypt(
                    run.resultCiphertext(), run.companyId(), run.ocrRunId()
            );
            DocumentOcrResultPayload payload = objectMapper.readValue(
                    plaintext,
                    DocumentOcrResultPayload.class
            );
            Map<String, Object> fields = new LinkedHashMap<>(payload.fields());
            if (run.correctedFieldsCiphertext() != null) {
                byte[] corrections = ocrResultCipher.decrypt(
                        run.correctedFieldsCiphertext(), run.companyId(), run.ocrRunId()
                );
                Map<String, Object> wrapper = objectMapper.readValue(
                        corrections,
                        new TypeReference<Map<String, Object>>() { }
                );
                Object corrected = wrapper.get("fields");
                if (corrected instanceof Map<?, ?> correctedMap) {
                    correctedMap.forEach((key, value) -> fields.put(String.valueOf(key), value));
                }
            }
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("fields", Map.copyOf(fields));
            if (payload.matchedTemplateId() != null) {
                response.put("matchedTemplateId", payload.matchedTemplateId());
            }
            if (payload.documentSide() != null) {
                response.put("documentSide", payload.documentSide().name());
            }
            return new ApprovedOcr(Map.copyOf(fields), Map.copyOf(response), run.updatedAt());
        } catch (JacksonException exception) {
            throw new IllegalStateException("stored OCR result is invalid", exception);
        }
    }

    private RenewalWorkerSnapshot toWorkerSnapshot(Worker worker) {
        return new RenewalWorkerSnapshot(
                worker.workerId(), worker.companyId(), worker.displayName(),
                worker.nationalityCode(), worker.preferredLanguage(), worker.workStatus().name(),
                worker.visaType(), worker.stayExpiryDate(), worker.contractStartDate(),
                worker.contractEndDate(), worker.employmentPermitEndDate(),
                worker.employmentActivityEndDate(), worker.createdAt(), worker.updatedAt(), worker.version()
        );
    }

    private RenewalCompanySnapshot toCompanySnapshot(Company company) {
        return new RenewalCompanySnapshot(
                company.companyId(), company.name(), company.status().name(),
                company.createdAt(), company.updatedAt(), company.version()
        );
    }

    private RenewalTaskSnapshot toTaskSnapshot(Task task, Map<String, Object> businessData) {
        return new RenewalTaskSnapshot(
                task.taskId(), task.companyId(), task.workerId(), task.caseId(),
                task.taskType().name(), task.workflowId(), task.workflowCatalogVersion(),
                task.title(), task.description(), businessData, task.contentRevision(),
                task.source().name(), task.status().name(), task.dueDate(), task.createdBy(),
                task.updatedBy(), task.createdAt(), task.updatedAt(), task.version()
        );
    }

    private record OcrContext(List<RenewalDocumentInput> documents, Map<String, Object> latestResult) {
    }

    private record ApprovedOcr(Map<String, Object> fields, Map<String, Object> payload, java.time.Instant updatedAt) {
    }
}
