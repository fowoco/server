package com.fowoco.server.task.api;

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
class TaskOpenApiContractTest {

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
    void allTaskWorkflowOperationsArePublished() {
        List<String> operationIds = List.of(
                operationId("/paths/~1api~1v1~1workflow-catalogs/get"),
                operationId("/paths/~1api~1v1~1tasks/get"),
                operationId("/paths/~1api~1v1~1tasks/post"),
                operationId("/paths/~1api~1v1~1tasks~1{taskId}/get"),
                operationId("/paths/~1api~1v1~1tasks~1{taskId}/patch"),
                operationId(
                        "/paths/~1api~1v1~1tasks~1{taskId}~1checklist-items~1{itemId}/patch"
                ),
                operationId("/paths/~1api~1v1~1tasks~1{taskId}~1cancel/post")
        );

        assertThat(operationIds).containsExactly(
                "getActiveWorkflowCatalog",
                "listTasks",
                "createTask",
                "getTask",
                "updateTask",
                "updateTaskChecklistItem",
                "cancelTask"
        );
    }

    @Test
    void writeContractsRequireVersionAndNeverAcceptTenantOrStatus() {
        JsonNode update = openApi.at("/components/schemas/UpdateTaskRequest");
        JsonNode checklist = openApi.at("/components/schemas/UpdateChecklistItemRequest");
        JsonNode cancel = openApi.at("/components/schemas/CancelTaskRequest");
        JsonNode create = openApi.at("/components/schemas/CreateTaskRequest/properties");

        assertThat(update.path("required").toString())
                .contains("expected_version", "business_data", "title");
        assertThat(checklist.path("required").toString())
                .contains("completed", "expected_version", "expected_task_version");
        assertThat(cancel.path("required").toString()).contains("expected_version", "reason");
        assertThat(create.has("company_id")).isFalse();
        assertThat(create.has("status")).isFalse();
    }

    @Test
    void mutationOperationsDocumentBearerConflictAndBusinessRuleErrors() {
        JsonNode update = openApi.at("/paths/~1api~1v1~1tasks~1{taskId}/patch");

        assertThat(update.at("/security/0/bearerAuth").isArray()).isTrue();
        assertThat(update.at("/responses/401/$ref").asText())
                .isEqualTo("#/components/responses/Unauthorized");
        assertThat(update.at("/responses/403/$ref").asText())
                .isEqualTo("#/components/responses/Forbidden");
        assertThat(update.at("/responses/409/$ref").asText())
                .isEqualTo("#/components/responses/Conflict");
        assertThat(update.at("/responses/422/$ref").asText())
                .isEqualTo("#/components/responses/UnprocessableEntity");
    }

    @Test
    void responseAndCatalogSchemasExposePinnedVersionsInSnakeCase() {
        JsonNode task = openApi.at("/components/schemas/TaskDetailResponse/properties");
        JsonNode workflow = openApi.at(
                "/components/schemas/WorkflowDefinitionResponse/properties"
        );

        assertThat(task.has("workflow_catalog_version")).isTrue();
        assertThat(task.has("content_revision")).isTrue();
        assertThat(task.has("missing_required_slots")).isTrue();
        assertThat(workflow.has("supported_task_types")).isTrue();
        assertThat(workflow.has("source_ids")).isTrue();
    }

    private String operationId(String pointer) {
        return openApi.at(pointer).path("operationId").asText();
    }
}
