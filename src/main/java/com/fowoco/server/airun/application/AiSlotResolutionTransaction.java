package com.fowoco.server.airun.application;

import com.fowoco.server.aiintegration.application.model.AiContextRequirement;
import com.fowoco.server.aiintegration.application.model.WorkerContext;
import com.fowoco.server.aiintegration.application.model.WorkflowConstraint;
import com.fowoco.server.airun.application.error.AiContextResolutionException;
import com.fowoco.server.airun.application.error.AiContextResolutionFailureCode;
import com.fowoco.server.common.security.TenantDatabaseContext;
import com.fowoco.server.worker.application.WorkerAiContextSnapshot;
import com.fowoco.server.worker.application.port.WorkerAiContextReader;
import com.fowoco.server.workflow.application.WorkflowCatalogService;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves allow-listed Worker context in a short database transaction.
 * The Runtime HTTP call happens after this transaction has completed.
 */
@Service
public class AiSlotResolutionTransaction {

    private final WorkflowCatalogService workflowCatalogService;
    private final WorkerAiContextReader workerContextReader;
    private final TenantDatabaseContext tenantDatabaseContext;

    public AiSlotResolutionTransaction(
            WorkflowCatalogService workflowCatalogService,
            WorkerAiContextReader workerContextReader,
            TenantDatabaseContext tenantDatabaseContext
    ) {
        this.workflowCatalogService = workflowCatalogService;
        this.workerContextReader = workerContextReader;
        this.tenantDatabaseContext = tenantDatabaseContext;
    }

    @Transactional(readOnly = true)
    public AiSlotResolution resolve(
            UUID companyId,
            String requiredKnowledgeVersion,
            AiContextRequirement requirement
    ) {
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(requiredKnowledgeVersion, "requiredKnowledgeVersion must not be null");
        Objects.requireNonNull(requirement, "requirement must not be null");
        tenantDatabaseContext.setCompanyIdForCurrentTransaction(companyId);

        var catalog = workflowCatalogService.getActiveCatalog();
        if (!requiredKnowledgeVersion.equals(catalog.bundleVersion())) {
            reject(
                    AiContextResolutionFailureCode.KNOWLEDGE_VERSION_MISMATCH,
                    "The analysis and active Workflow Catalog versions do not match."
            );
        }
        var workflows = catalog.findByIntent(requirement.detectedIntent());
        if (workflows.isEmpty()) {
            reject(
                    AiContextResolutionFailureCode.UNSUPPORTED_INTENT,
                    "The Runtime returned an Intent that is not in the active Workflow Catalog."
            );
        }

        Set<String> resolvableKeys = new LinkedHashSet<>();
        workflows.forEach(workflow -> resolvableKeys.addAll(workflow.resolvableSlotKeys()));
        Set<String> forbiddenKeys = new LinkedHashSet<>(requirement.requiredFieldKeys());
        forbiddenKeys.removeAll(resolvableKeys);
        if (!forbiddenKeys.isEmpty()) {
            reject(
                    AiContextResolutionFailureCode.FORBIDDEN_FIELD,
                    "The Runtime requested a field outside the active Knowledge allow-list."
            );
        }

        List<WorkerAiContextSnapshot> matches = workerContextReader.findByDisplayName(
                companyId,
                requirement.targetDisplayName()
        );
        if (matches.isEmpty()) {
            reject(
                    AiContextResolutionFailureCode.TARGET_NOT_FOUND,
                    "The requested Worker target was not found in the current company."
            );
        }
        if (matches.size() > 1) {
            reject(
                    AiContextResolutionFailureCode.TARGET_AMBIGUOUS,
                    "The requested Worker target is ambiguous in the current company."
            );
        }

        WorkerAiContextSnapshot worker = matches.get(0);
        if (!companyId.equals(worker.companyId())) {
            reject(
                    AiContextResolutionFailureCode.TARGET_NOT_FOUND,
                    "The requested Worker target was not found in the current company."
            );
        }

        Map<String, String> resolvedFields = new LinkedHashMap<>();
        Set<String> missingFieldKeys = new LinkedHashSet<>();
        for (String fieldKey : requirement.requiredFieldKeys()) {
            String value = resolveWorkerField(fieldKey, worker);
            if (value == null) {
                missingFieldKeys.add(fieldKey);
            } else {
                resolvedFields.put(fieldKey, value);
            }
        }

        WorkerContext workerContext = new WorkerContext(
                worker.workerId(),
                worker.displayName(),
                worker.nationalityCode(),
                worker.preferredLanguage(),
                worker.workStatus(),
                worker.stayExpiryDate(),
                worker.contractStartDate(),
                worker.contractEndDate(),
                resolvedFields
        );
        List<WorkflowConstraint> constraints = workflows.stream()
                .sorted(java.util.Comparator.comparing(workflow -> workflow.workflowId()))
                .map(workflow -> new WorkflowConstraint(
                        workflow.workflowId(),
                        workflow.allowedSlotKeys()
                ))
                .toList();
        return new AiSlotResolution(
                workerContext,
                constraints,
                resolvedFields,
                missingFieldKeys
        );
    }

    private String resolveWorkerField(String fieldKey, WorkerAiContextSnapshot worker) {
        return switch (fieldKey) {
            case "worker_id" -> worker.workerId().toString();
            case "stay_expiry_date" -> formatDate(worker.stayExpiryDate());
            case "contract_end_date" -> formatDate(worker.contractEndDate());
            case "passport_status" -> worker.identityDocumentStatuses().passportStatus().name();
            case "arc_status" -> worker.identityDocumentStatuses().arcStatus().name();
            default -> null;
        };
    }

    private String formatDate(java.time.LocalDate date) {
        return date == null ? null : date.toString();
    }

    private void reject(AiContextResolutionFailureCode failureCode, String safeMessage) {
        throw new AiContextResolutionException(failureCode, safeMessage);
    }
}
