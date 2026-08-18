package com.fowoco.server.workflow.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

class WorkflowCatalogProjectionTest {

    @Test
    void loadsKnowledge031StayRenewalAndExpiredStayExceptionContracts() {
        ResourceWorkflowCatalogRepository repository = new ResourceWorkflowCatalogRepository(
                JsonMapper.builder()
                        .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                        .build(),
                new ClassPathResource("workflow/catalog-projection.local.json"),
                true
        );

        repository.loadAndValidate();

        var catalog = repository.getActiveCatalog();
        var stayRenewal = catalog.findWorkflow("WF-STY-001").orElseThrow();
        var expiredStayException = catalog.findWorkflow("WF-STY-EXC-001").orElseThrow();

        assertThat(catalog.bundleVersion()).isEqualTo("0.3.1");
        assertThat(stayRenewal.requiredSlots())
                .containsExactlyInAnyOrder("worker_id", "due_at");
        assertThat(stayRenewal.allowedSlotKeys()).containsAll(Set.of(
                "worker_id",
                "due_at",
                "stay_expiry_date",
                "passport_status",
                "arc_status"
        ));
        assertThat(stayRenewal.resolvableSlotKeys()).containsAll(Set.of(
                "worker_id",
                "due_at",
                "stay_expiry_date",
                "passport_status",
                "arc_status"
        ));
        assertThat(expiredStayException.requiredSlots()).containsExactlyInAnyOrder(
                "worker_id",
                "stay_expiry_date",
                "stay_verification_status"
        );
        var renewalCase = catalog.findCaseTemplatesByIntent("EXPIRY_RENEWAL")
                .stream()
                .filter(template -> template.caseTemplateId().equals("CASE-EXPIRY-RENEWAL-001"))
                .findFirst()
                .orElseThrow();
        assertThat(renewalCase.tasks()).extracting(task -> task.taskType().name())
                .containsExactly(
                        "RECONTRACT",
                        "DOCUMENT_REQUEST",
                        "EMPLOYMENT_PERIOD_EXTENSION",
                        "STAY_PERIOD_EXTENSION"
                );
        assertThat(renewalCase.tasks().get(0).checklistItems()).hasSize(6);
        assertThat(renewalCase.tasks().get(1).checklistItems())
                .filteredOn(item -> item.required())
                .extracting(item -> item.itemCode())
                .containsExactly("WORKER_DOCUMENT_REQUEST_APPROVED");
        assertThat(renewalCase.tasks().get(1).checklistItems())
                .filteredOn(item -> !item.required())
                .extracting(item -> item.itemCode())
                .containsExactly(
                        "SECURE_LINK_DELIVERY_RECORDED",
                        "IDENTITY_DOCUMENTS_SUBMITTED",
                        "OCR_RESULT_HR_REVIEWED"
                );
        assertThat(renewalCase.tasks().get(2).dependsOn()).containsExactly("recontract");
        assertThat(renewalCase.tasks().get(2).checklistItems())
                .filteredOn(item -> item.required())
                .extracting(item -> item.itemCode())
                .containsExactly(
                        "SIGNED_CONTRACT_READY_FOR_EXTENSION",
                        "EMPLOYMENT_EXTENSION_REQUIREMENTS_REVIEWED"
                );
        assertThat(renewalCase.tasks().get(2).checklistItems())
                .filteredOn(item -> !item.required())
                .extracting(item -> item.itemCode())
                .containsExactly(
                        "EMPLOYMENT_EXTENSION_MANUALLY_SUBMITTED",
                        "EMPLOYMENT_EXTENSION_RESULT_RECORDED"
                );
        assertThat(renewalCase.tasks().get(3).dependsOn())
                .containsExactly("employment_period_extension");
        assertThat(renewalCase.tasks().get(3).checklistItems())
                .filteredOn(item -> item.required())
                .extracting(item -> item.itemCode())
                .containsExactly(
                        "PASSPORT_AND_ARC_CURRENT_VERIFIED",
                        "EMPLOYMENT_EXTENSION_RESULT_AVAILABLE",
                        "INTEGRATED_APPLICATION_DRAFT_REVIEWED"
                );
        assertThat(renewalCase.tasks().get(3).checklistItems())
                .filteredOn(item -> !item.required())
                .extracting(item -> item.itemCode())
                .containsExactly(
                        "STAY_EXTENSION_MANUALLY_SUBMITTED",
                        "STAY_EXTENSION_RESULT_AND_NEXT_REVIEW_RECORDED"
                );
    }
}
