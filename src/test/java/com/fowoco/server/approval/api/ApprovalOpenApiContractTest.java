package com.fowoco.server.approval.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApprovalOpenApiContractTest {

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private JsonNode openApi;

    @BeforeAll
    void loadOpenApi() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/v3/api-docs"))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );
        assertThat(response.statusCode()).isEqualTo(200);
        openApi = objectMapper.readTree(response.body());
    }

    @Test
    void allApprovalAndAuditOperationsArePublished() {
        List<String> operationIds = List.of(
                operationId("/paths/~1api~1v1~1tasks~1{taskId}~1approval-requests/post"),
                operationId("/paths/~1api~1v1~1tasks~1{taskId}~1approve/post"),
                operationId("/paths/~1api~1v1~1tasks~1{taskId}~1reject/post"),
                operationId("/paths/~1api~1v1~1tasks~1{taskId}~1external-submissions/post"),
                operationId("/paths/~1api~1v1~1tasks~1{taskId}~1evidence/post"),
                operationId("/paths/~1api~1v1~1tasks~1{taskId}~1complete/post"),
                operationId("/paths/~1api~1v1~1tasks~1{taskId}~1activities/get"),
                operationId("/paths/~1api~1v1~1audit-events/get")
        );

        assertThat(operationIds).containsExactly(
                "requestTaskApproval",
                "approveTask",
                "rejectTask",
                "recordExternalSubmission",
                "recordTaskEvidence",
                "completeTask",
                "getTaskActivities",
                "searchAuditEvents"
        );
    }

    @Test
    void protectedOperationsDocumentBearerAndCommonErrors() {
        JsonNode approve = openApi.at("/paths/~1api~1v1~1tasks~1{taskId}~1approve/post");

        assertThat(approve.at("/security/0/bearerAuth").isArray()).isTrue();
        assertThat(approve.at("/responses/401/$ref").asText())
                .isEqualTo("#/components/responses/Unauthorized");
        assertThat(approve.at("/responses/403/$ref").asText())
                .isEqualTo("#/components/responses/Forbidden");
        assertThat(approve.at("/responses/409/$ref").asText())
                .isEqualTo("#/components/responses/Conflict");
        assertThat(approve.at("/responses/422/$ref").asText())
                .isEqualTo("#/components/responses/UnprocessableEntity");
    }

    @Test
    void approvalSnapshotRequestAndResponseUseSnakeCase() {
        JsonNode request = openApi.at("/components/schemas/ApprovalRequestBody/properties");
        JsonNode response = openApi.at("/components/schemas/ApprovalResponse/properties");

        assertThat(request.has("expected_version")).isTrue();
        assertThat(request.has("requirements_satisfied")).isFalse();
        assertThat(request.has("ai_snapshot")).isTrue();
        assertThat(request.has("hr_snapshot")).isTrue();
        assertThat(request.has("source_versions")).isTrue();
        assertThat(response.has("content_revision")).isTrue();
        assertThat(response.has("task_version")).isTrue();
    }

    private String operationId(String pointer) {
        return openApi.at(pointer).path("operationId").asText();
    }
}
