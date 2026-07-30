package com.fowoco.server.aiintegration.application.model;

import static com.fowoco.server.aiintegration.support.AiRuntimeContractFixture.validRequest;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class AiRuntimeOutboundContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void outboundJsonContainsOriginalDemoDataWithoutServiceCredentials() throws Exception {
        JsonNode json = objectMapper.valueToTree(validRequest());
        JsonNode input = json.get("analysisInput");
        JsonNode worker = input.get("workers").get(0);

        assertThat(json.properties().stream().map(java.util.Map.Entry::getKey).toList())
                .containsExactlyInAnyOrder(
                        "requestId",
                        "attemptId",
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
