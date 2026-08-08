package com.fowoco.server.settings.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CompanySettingsOpenApiContractTest {

    @LocalServerPort
    private int port;

    private JsonNode openApi;

    @BeforeAll
    void loadOpenApi() throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/v3/api-docs"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertThat(response.statusCode()).isEqualTo(200);
        openApi = new ObjectMapper().readTree(response.body());
    }

    @Test
    void settingsGetPublishesBearerSecurityAndErrorResponses() {
        JsonNode operation = openApi.at("/paths/~1api~1v1~1settings/get");

        assertThat(operation.path("operationId").asText()).isEqualTo("getCompanySettings");
        assertThat(operation.at("/security/0/bearerAuth").isArray()).isTrue();
        assertThat(operation.at("/responses/401/$ref").asText())
                .isEqualTo("#/components/responses/Unauthorized");
        assertThat(operation.at("/responses/403/$ref").asText())
                .isEqualTo("#/components/responses/Forbidden");
        assertThat(operation.at("/responses/500/$ref").asText())
                .isEqualTo("#/components/responses/InternalServerError");
        assertThat(operation.path("requestBody").isMissingNode()).isTrue();
        JsonNode parameters = operation.path("parameters");
        assertThat(parameters).hasSize(1);
        JsonNode requestIdHeader = parameters.path(0);
        assertThat(requestIdHeader.path("name").asText()).isEqualTo("X-Request-Id");
        assertThat(requestIdHeader.path("in").asText()).isEqualTo("header");
        assertThat(requestIdHeader.path("required").asBoolean()).isFalse();
    }

    @Test
    void responseSchemaIsTheFrozenSnakeCasePublicDto() {
        JsonNode schema = openApi.at("/components/schemas/CompanySettingsResponse");
        JsonNode properties = schema.path("properties");
        Set<String> propertyNames = StreamSupport.stream(
                        ((Iterable<String>) properties::fieldNames).spliterator(),
                        false
                )
                .collect(Collectors.toSet());

        assertThat(propertyNames).containsExactlyInAnyOrder(
                "approval_policy",
                "link_expiry_hours",
                "evidence_rules",
                "file_retention_days",
                "ai_log_retention_days",
                "audit_visibility",
                "version"
        );
        assertThat(schema.path("required")).hasSize(7);
        assertThat(properties.path("approval_policy").path("enum"))
                .extracting(JsonNode::asText)
                .containsExactly("ADMIN_ONLY", "ADMIN_OR_HR");
        assertThat(properties.path("audit_visibility").path("enum"))
                .extracting(JsonNode::asText)
                .containsExactly("ADMIN_ONLY", "ADMIN_AND_HR");
        assertThat(properties.path("link_expiry_hours").path("minimum").asLong()).isEqualTo(1);
        assertThat(properties.path("link_expiry_hours").path("maximum").asLong()).isEqualTo(168);
        assertThat(properties.has("company_id")).isFalse();
        assertThat(properties.has("email")).isFalse();
        assertThat(properties.has("secret")).isFalse();
    }

    @Test
    void successResponseIncludesTheDocumentedExample() {
        JsonNode example = openApi.at(
                "/paths/~1api~1v1~1settings/get/responses/200/content/application~1json/examples/settings/value"
        );

        assertThat(example.path("approval_policy").asText()).isEqualTo("ADMIN_OR_HR");
        assertThat(example.path("link_expiry_hours").asLong()).isEqualTo(72L);
        assertThat(example.at("/evidence_rules/RECONTRACT/0").asText()).isEqualTo("DOCUMENT");
        assertThat(example.path("audit_visibility").asText()).isEqualTo("ADMIN_ONLY");
    }
}
