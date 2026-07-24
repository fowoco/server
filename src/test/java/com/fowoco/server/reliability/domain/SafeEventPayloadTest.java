package com.fowoco.server.reliability.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SafeEventPayloadTest {

    @Test
    void acceptsOnlyAllowListedScalarBusinessFields() {
        UUID workflowId = UUID.randomUUID();

        SafeEventPayload payload = SafeEventPayload.of(
                Set.of("status", "due_date", "workflow_ids"),
                Map.of(
                        "status", EventPublicationStatus.PENDING,
                        "due_date", LocalDate.of(2027, 3, 1),
                        "workflow_ids", List.of(workflowId)
                )
        );

        assertThat(payload.values())
                .containsEntry("status", "PENDING")
                .containsEntry("due_date", "2027-03-01")
                .containsEntry("workflow_ids", List.of(workflowId.toString()));
    }

    @Test
    void rejectsAFieldThatWasNotExplicitlyAllowed() {
        assertThatThrownBy(() -> SafeEventPayload.of(
                Set.of("status"),
                Map.of("status", "READY", "task_title", "체류기간 연장")
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not allow-listed");
    }

    @Test
    void rejectsSensitiveFieldNamesEvenWhenAllowListed() {
        assertThatThrownBy(() -> SafeEventPayload.of(
                Set.of("passport_number"),
                Map.of("passport_number", "M12345678")
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Sensitive");
    }

    @Test
    void rejectsSensitiveValuesHiddenUnderAnOtherwiseSafeField() {
        assertThatThrownBy(() -> SafeEventPayload.of(
                Set.of("result"),
                Map.of("result", "연락처 010-1234-5678")
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Sensitive");
    }

    @Test
    void rejectsNestedObjectsAndOversizedCollections() {
        assertThatThrownBy(() -> SafeEventPayload.of(
                Set.of("metadata"),
                Map.of("metadata", Map.of("status", "READY"))
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> SafeEventPayload.of(
                Set.of("workflow_ids"),
                Map.of("workflow_ids", java.util.stream.IntStream.range(0, 51).boxed().toList())
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too large");
    }
}
