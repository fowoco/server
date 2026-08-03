package com.fowoco.server.aiintegration.application.model;

import static com.fowoco.server.aiintegration.support.AiRuntimeContractFixture.validRequest;
import static com.fowoco.server.aiintegration.support.AiRuntimeContractFixture.validPlanRequest;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class AiRuntimeOutboundContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void planJsonContainsCombinedInstructionAsTheOnlyBusinessInput() {
        JsonNode json = objectMapper.valueToTree(validPlanRequest());
        JsonNode input = json.get("analysisInput");

        assertThat(json.get("phase").textValue()).isEqualTo("PLAN");
        assertThat(input.get("instruction").textValue())
                .isEqualTo("응웬반안 체류연장 준비해줘, EXPIRY_RENEWAL");
        assertThat(input.has("intentHint")).isFalse();
        assertThat(input.get("extractedSlots").isEmpty()).isTrue();
        assertThat(input.get("requestedFieldKeys").isEmpty()).isTrue();
        assertThat(input.get("workers").isEmpty()).isTrue();
        assertThat(input.get("workflowConstraints").isEmpty()).isTrue();
        assertThat(input.properties().stream().map(java.util.Map.Entry::getKey).toList())
                .containsExactlyInAnyOrder(
                        "instruction",
                        "extractedSlots",
                        "requestedFieldKeys",
                        "workers",
                        "workflowConstraints"
                );
    }

    @Test
    void outboundJsonContainsOriginalDemoDataWithoutServiceCredentials() throws Exception {
        JsonNode json = objectMapper.valueToTree(validRequest());
        JsonNode input = json.get("analysisInput");
        JsonNode worker = input.get("workers").get(0);

        assertThat(json.properties().stream().map(java.util.Map.Entry::getKey).toList())
                .containsExactlyInAnyOrder(
                        "requestId",
                        "attemptId",
                        "phase",
                        "contractVersion",
                        "requiredKnowledgeVersion",
                        "deadlineMs",
                        "analysisInput"
                );
        assertThat(worker.properties().stream().map(java.util.Map.Entry::getKey).toList())
                .containsExactlyInAnyOrder(
                        "workerRef",
                        "displayName",
                        "nationalityCode",
                        "preferredLanguage",
                        "workStatus",
                        "stayExpiryDate",
                        "contractStartDate",
                        "contractEndDate",
                        "requestedFields"
                );
        assertThat(input.get("instruction").textValue())
                .contains("응웬반안", "010-1234-5678");
        assertThat(input.get("instruction").textValue()).endsWith(", EXPIRY_RENEWAL");
        assertThat(input.has("intentHint")).isFalse();
        assertThat(input.get("extractedSlots").get("document_type").textValue())
                .isEqualTo("STAY_EXTENSION");
        assertThat(input.get("requestedFieldKeys")).hasSize(4);
        assertThat(worker.get("requestedFields").get("legal_name").textValue())
                .isEqualTo("NGUYEN VAN AN");
        assertThat(worker.get("requestedFields").get("passport_number").textValue())
                .isEqualTo("M12345678");
        assertThat(worker.get("requestedFields").get("email").textValue())
                .isEqualTo("worker@example.com");
        assertThat(json.toString().toLowerCase())
                .doesNotContain(
                        "token",
                        "authorization",
                        "password",
                        "api_key"
                );
    }
}
