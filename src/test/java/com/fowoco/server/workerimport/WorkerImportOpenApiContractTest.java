package com.fowoco.server.workerimport;

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
class WorkerImportOpenApiContractTest {

    @LocalServerPort
    private int port;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private JsonNode openApi;

    @BeforeAll
    void loadOpenApi() throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v3/api-docs")).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertThat(response.statusCode()).isEqualTo(200);
        openApi = objectMapper.readTree(response.body());
    }

    @Test
    void documentsAllSevenCanonicalImportOperations() {
        List<String> operations = List.of(
                "/paths/~1api~1v1~1imports/post",
                "/paths/~1api~1v1~1imports~1{importId}/get",
                "/paths/~1api~1v1~1imports~1{importId}~1mappings/put",
                "/paths/~1api~1v1~1imports~1{importId}~1validate/post",
                "/paths/~1api~1v1~1imports~1{importId}~1rows/patch",
                "/paths/~1api~1v1~1imports~1{importId}~1commit/post",
                "/paths/~1api~1v1~1imports~1{importId}~1retry/post"
        );
        operations.forEach(pointer -> {
            JsonNode operation = openApi.at(pointer);
            assertThat(operation.isMissingNode()).as(pointer).isFalse();
            assertThat(operation.path("security").toString()).contains("bearerAuth");
            assertThat(operation.path("responses").has("401")).isTrue();
            assertThat(operation.path("responses").has("403")).isTrue();
        });
    }

    @Test
    void uploadAndCommitExposeMultipartAndIdempotencyContracts() {
        JsonNode upload = openApi.at("/paths/~1api~1v1~1imports/post");
        JsonNode commit = openApi.at("/paths/~1api~1v1~1imports~1{importId}~1commit/post");

        assertThat(upload.at("/requestBody/content/multipart~1form-data").isMissingNode()).isFalse();
        assertThat(hasRequiredHeader(upload, "Idempotency-Key")).isTrue();
        assertThat(upload.path("responses").has("201")).isTrue();
        assertThat(upload.path("responses").has("413")).isTrue();
        assertThat(upload.path("responses").has("415")).isTrue();
        assertThat(hasRequiredHeader(commit, "Idempotency-Key")).isTrue();
    }

    @Test
    void requestAndResponseSchemasUseSnakeCase() {
        JsonNode mapping = openApi.at("/components/schemas/WorkerImportMappingRequest/properties");
        JsonNode response = openApi.at("/components/schemas/WorkerImportResponse/properties");

        assertThat(mapping.has("expected_version")).isTrue();
        assertThat(response.has("import_id")).isTrue();
        assertThat(response.has("source_file_expires_at")).isTrue();
        assertThat(response.has("committed_rows")).isTrue();
    }

    private boolean hasRequiredHeader(JsonNode operation, String name) {
        for (JsonNode parameter : operation.path("parameters")) {
            if (name.equals(parameter.path("name").asText())
                    && "header".equals(parameter.path("in").asText())
                    && parameter.path("required").asBoolean()) {
                return true;
            }
        }
        return false;
    }
}
