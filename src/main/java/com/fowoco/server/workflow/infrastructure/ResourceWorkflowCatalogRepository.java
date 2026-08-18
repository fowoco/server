package com.fowoco.server.workflow.infrastructure;

import com.fowoco.server.workflow.application.port.WorkflowCatalogRepository;
import com.fowoco.server.workflow.domain.WorkflowCatalog;
import com.fowoco.server.workflow.domain.WorkflowChecklistTemplate;
import com.fowoco.server.workflow.domain.WorkflowDefinition;
import com.fowoco.server.workflow.domain.WorkflowCaseTemplate;
import com.fowoco.server.workflow.domain.WorkflowCaseTemplate.ActivationMode;
import com.fowoco.server.task.domain.TaskType;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
                || projection.workflows().isEmpty()
                || projection.caseTemplates() == null
                || projection.caseTemplates().isEmpty()) {
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
                    || workflow.allowedSlotKeys() == null
                    || workflow.resolvableSlotKeys() == null
                    || workflow.supportedTaskTypes() == null
                    || workflow.supportedTaskTypes().isEmpty()
                    || workflow.checklistItems() == null
                    || workflow.completionEvidence() == null
                    || workflow.sourceIds() == null) {
                throw new IllegalStateException("Workflow projection collection은 null일 수 없습니다.");
            }
            if (!workflow.allowedSlotKeys().containsAll(workflow.requiredSlots())
                    || !workflow.allowedSlotKeys().containsAll(workflow.resolvableSlotKeys())) {
                throw new IllegalStateException(
                        "required_slots와 resolvable_slot_keys는 allowed_slot_keys에 포함되어야 합니다."
                );
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
        validateCaseTemplates(projection, workflowIds);
    }

    private void validateCaseTemplates(CatalogProjection projection, Set<String> workflowIds) {
        Set<String> templateIds = new HashSet<>();
        projection.caseTemplates().forEach(template -> {
            requireText(template.caseTemplateId(), "case_template_id");
            requireText(template.name(), "case template name");
            requireText(template.intent(), "case template intent");
            if (!templateIds.add(template.caseTemplateId())) {
                throw new IllegalStateException("중복 case_template_id: " + template.caseTemplateId());
            }
            if (template.workflowIds() == null || template.workflowIds().isEmpty()
                    || !workflowIds.containsAll(template.workflowIds())
                    || template.tasks() == null || template.tasks().isEmpty()) {
                throw new IllegalStateException("Case template의 workflow와 task 구성이 올바르지 않습니다.");
            }
            Set<String> taskKeys = new HashSet<>();
            Set<Integer> orders = new HashSet<>();
            template.tasks().forEach(task -> {
                requireText(task.key(), "case task key");
                requireText(task.title(), "case task title");
                requireText(task.description(), "case task description");
                if (!taskKeys.add(task.key()) || !orders.add(task.order()) || task.order() < 1) {
                    throw new IllegalStateException("Case template task key와 order는 양수이며 고유해야 합니다.");
                }
                if (!template.workflowIds().contains(task.workflowId())) {
                    throw new IllegalStateException("Case task workflow_id가 template에 선언되지 않았습니다.");
                }
                if (task.taskType() == null || task.activation() == null
                        || task.activation().mode() == null
                        || task.dependsOn() == null || task.dependsOnIfPresent() == null
                        || task.checklistItems() == null || task.checklistItems().isEmpty()
                        || task.completionEvidence() == null || task.completionEvidence().isEmpty()) {
                    throw new IllegalStateException("Case template task 계약 값이 누락되었습니다.");
                }
                if (task.activation().mode() == ActivationMode.MISSING_ANY
                        && (task.activation().fieldKeys() == null
                        || task.activation().fieldKeys().isEmpty())) {
                    throw new IllegalStateException("MISSING_ANY task에는 field_keys가 필요합니다.");
                }
            });
            Map<String, Integer> taskOrders = template.tasks().stream()
                    .collect(java.util.stream.Collectors.toMap(
                            CaseTaskProjection::key,
                            CaseTaskProjection::order
                    ));
            template.tasks().forEach(task -> {
                if (!taskKeys.containsAll(task.dependsOn())
                        || !taskKeys.containsAll(task.dependsOnIfPresent())
                        || task.dependsOn().contains(task.key())
                        || task.dependsOnIfPresent().contains(task.key())) {
                    throw new IllegalStateException("Case template task 의존성이 올바르지 않습니다.");
                }
                java.util.stream.Stream.concat(
                                task.dependsOn().stream(),
                                task.dependsOnIfPresent().stream()
                        )
                        .filter(dependency -> taskOrders.get(dependency) >= task.order())
                        .findFirst()
                        .ifPresent(dependency -> {
                            throw new IllegalStateException(
                                    "Case template task는 앞 순서 task에만 의존할 수 있습니다."
                            );
                        });
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
            List<WorkflowProjection> workflows,
            List<CaseTemplateProjection> caseTemplates
    ) {

        WorkflowCatalog toDomain() {
            return new WorkflowCatalog(
                    bundleId,
                    bundleVersion,
                    bundleStatus,
                    sourceRepository,
                    generatedAt,
                    workflows.stream().map(WorkflowProjection::toDomain).toList(),
                    caseTemplates.stream().map(CaseTemplateProjection::toDomain).toList()
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
            Set<String> allowedSlotKeys,
            Set<String> resolvableSlotKeys,
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
                    allowedSlotKeys,
                    resolvableSlotKeys,
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

    private record CaseTemplateProjection(
            String caseTemplateId,
            String name,
            String intent,
            Set<String> workflowIds,
            List<CaseTaskProjection> tasks
    ) {

        WorkflowCaseTemplate toDomain() {
            return new WorkflowCaseTemplate(
                    caseTemplateId,
                    name,
                    intent,
                    workflowIds,
                    tasks.stream().map(CaseTaskProjection::toDomain).toList()
            );
        }
    }

    private record CaseTaskProjection(
            String key,
            int order,
            TaskType taskType,
            String workflowId,
            String title,
            String description,
            ActivationProjection activation,
            List<String> dependsOn,
            List<String> dependsOnIfPresent,
            List<ChecklistProjection> checklistItems,
            List<String> completionEvidence
    ) {

        WorkflowCaseTemplate.TaskTemplate toDomain() {
            return new WorkflowCaseTemplate.TaskTemplate(
                    key,
                    order,
                    taskType,
                    workflowId,
                    title,
                    description,
                    activation.toDomain(),
                    dependsOn,
                    dependsOnIfPresent,
                    checklistItems.stream().map(ChecklistProjection::toDomain).toList(),
                    completionEvidence
            );
        }
    }

    private record ActivationProjection(ActivationMode mode, Set<String> fieldKeys) {

        WorkflowCaseTemplate.Activation toDomain() {
            return new WorkflowCaseTemplate.Activation(
                    mode,
                    fieldKeys == null ? Set.of() : fieldKeys
            );
        }
    }
}
