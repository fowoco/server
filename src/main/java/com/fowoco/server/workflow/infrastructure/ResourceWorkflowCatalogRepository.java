package com.fowoco.server.workflow.infrastructure;

import com.fowoco.server.workflow.application.port.WorkflowCatalogRepository;
import com.fowoco.server.workflow.domain.WorkflowCatalog;
import com.fowoco.server.workflow.domain.WorkflowChecklistTemplate;
import com.fowoco.server.workflow.domain.WorkflowDefinition;
import com.fowoco.server.task.domain.TaskType;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

@Repository
public class ResourceWorkflowCatalogRepository implements WorkflowCatalogRepository {

    private static final String KNOWLEDGE_REPOSITORY = "fowoco/knowledge";
    private static final String RELEASED = "RELEASED";

    private final ObjectMapper objectMapper;
    private final Resource catalogResource;
    private final boolean allowUnreleased;
    private WorkflowCatalog catalog;

    public ResourceWorkflowCatalogRepository(
            ObjectMapper objectMapper,
            @Value("${app.workflow.catalog.location}") Resource catalogResource,
            @Value("${app.workflow.catalog.allow-unreleased:false}") boolean allowUnreleased
    ) {
        this.objectMapper = objectMapper;
        this.catalogResource = catalogResource;
        this.allowUnreleased = allowUnreleased;
    }

    @PostConstruct
    void loadAndValidate() {
        try {
            CatalogProjection projection = objectMapper.readValue(
                    catalogResource.getInputStream(),
                    CatalogProjection.class
            );
            validate(projection);
            this.catalog = projection.toDomain();
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Workflow Catalog projection을 읽을 수 없습니다: " + catalogResource,
                    exception
            );
        }
    }

    @Override
    public WorkflowCatalog getActiveCatalog() {
        return catalog;
    }

    private void validate(CatalogProjection projection) {
        requireText(projection.bundleId(), "bundle_id");
        requireText(projection.bundleVersion(), "bundle_version");
        requireText(projection.bundleStatus(), "bundle_status");
        if (!KNOWLEDGE_REPOSITORY.equals(projection.sourceRepository())) {
            throw new IllegalStateException("Workflow Catalog source_repository는 fowoco/knowledge여야 합니다.");
        }
        if (!allowUnreleased && !RELEASED.equals(projection.bundleStatus().toUpperCase(Locale.ROOT))) {
            throw new IllegalStateException("운영 환경은 RELEASED Workflow Catalog만 사용할 수 있습니다.");
        }
        if (projection.generatedAt() == null || projection.workflows() == null
                || projection.workflows().isEmpty()) {
            throw new IllegalStateException("Workflow Catalog 생성시각과 workflow가 필요합니다.");
        }
        Set<String> workflowIds = new HashSet<>();
        projection.workflows().forEach(workflow -> {
            requireText(workflow.workflowId(), "workflow_id");
            requireText(workflow.name(), "workflow name");
            requireText(workflow.intent(), "workflow intent");
            if (!workflowIds.add(workflow.workflowId())) {
                throw new IllegalStateException("중복 workflow_id: " + workflow.workflowId());
            }
            if (workflow.requiredSlots() == null
                    || workflow.supportedTaskTypes() == null
                    || workflow.supportedTaskTypes().isEmpty()
                    || workflow.checklistItems() == null
                    || workflow.completionEvidence() == null
                    || workflow.sourceIds() == null) {
                throw new IllegalStateException("Workflow projection collection은 null일 수 없습니다.");
            }
            Set<String> itemCodes = new HashSet<>();
            workflow.checklistItems().forEach(item -> {
                requireText(item.itemCode(), "checklist item_code");
                requireText(item.label(), "checklist label");
                if (!itemCodes.add(item.itemCode())) {
                    throw new IllegalStateException(
                            "중복 checklist item_code: " + item.itemCode()
                    );
                }
            });
        });
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(field + " 값이 필요합니다.");
        }
    }

    private record CatalogProjection(
            String bundleId,
            String bundleVersion,
            String bundleStatus,
            String sourceRepository,
            Instant generatedAt,
            List<WorkflowProjection> workflows
    ) {

        WorkflowCatalog toDomain() {
            return new WorkflowCatalog(
                    bundleId,
                    bundleVersion,
                    bundleStatus,
                    sourceRepository,
                    generatedAt,
                    workflows.stream().map(WorkflowProjection::toDomain).toList()
            );
        }
    }

    private record WorkflowProjection(
            String workflowId,
            String name,
            String intent,
            String sensitivity,
            Set<TaskType> supportedTaskTypes,
            Set<String> requiredSlots,
            List<ChecklistProjection> checklistItems,
            List<String> completionEvidence,
            List<String> sourceIds
    ) {

        WorkflowDefinition toDomain() {
            return new WorkflowDefinition(
                    workflowId,
                    name,
                    intent,
                    sensitivity,
                    supportedTaskTypes,
                    requiredSlots,
                    checklistItems.stream().map(ChecklistProjection::toDomain).toList(),
                    completionEvidence,
                    sourceIds
            );
        }
    }

    private record ChecklistProjection(String itemCode, String label, boolean required) {

        WorkflowChecklistTemplate toDomain() {
            return new WorkflowChecklistTemplate(itemCode, label, required);
        }
    }
}
