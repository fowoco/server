package com.fowoco.server.airun.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AiRunOpenApiContractTest {

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private JsonNode openApi;

    @BeforeAll
    void loadOpenApi() throws Exception {
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/v3/api-docs"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );

        assertThat(response.statusCode()).isEqualTo(200);
        openApi = objectMapper.readTree(response.body());
    }

    @Test
    void publishesAiRunEventStreamContract() {
        JsonNode operation = openApi.at(
                "/paths/~1api~1v1~1ai-runs~1{aiRunId}~1events/get"
        );

        assertThat(operation.path("operationId").asText()).isEqualTo("subscribeAiRunEvents");
        assertThat(operation.at("/security/0/bearerAuth").isArray()).isTrue();
        assertThat(operation.at("/responses/200/content/text~1event-stream").isMissingNode())
                .isFalse();
        assertThat(operation.at("/responses/400/$ref").asText())
                .isEqualTo("#/components/responses/BadRequest");
        assertThat(operation.at("/responses/404/$ref").asText())
                .isEqualTo("#/components/responses/NotFound");
        assertThat(operation.path("responses").has("429")).isTrue();
        assertThat(operation.path("parameters"))
                .anySatisfy(parameter -> {
                    assertThat(parameter.path("name").asText()).isEqualTo("Last-Event-ID");
                    assertThat(parameter.path("in").asText()).isEqualTo("header");
                    assertThat(parameter.path("required").asBoolean()).isFalse();
                });
    }
}
