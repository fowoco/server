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
        assertThat(renewalCase.tasks().get(2).dependsOn()).containsExactly("recontract");
        assertThat(renewalCase.tasks().get(3).dependsOn())
                .containsExactly("employment_period_extension");
    }
}
