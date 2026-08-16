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
    }
}
