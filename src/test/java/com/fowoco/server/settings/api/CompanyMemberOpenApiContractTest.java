package com.fowoco.server.settings.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
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
class CompanyMemberOpenApiContractTest {

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
    void operationPublishesFrozenQuerySecurityAndErrors() {
        JsonNode operation = openApi.at("/paths/~1api~1v1~1company-members/get");
        Map<String, JsonNode> parameters = StreamSupport.stream(
                        operation.path("parameters").spliterator(),
                        false
                )
                .collect(Collectors.toMap(
                        parameter -> parameter.path("name").asText(),
                        parameter -> parameter
                ));

        assertThat(operation.path("operationId").asText()).isEqualTo("listCompanyMembers");
        assertThat(operation.at("/security/0/bearerAuth").isArray()).isTrue();
        assertThat(parameters.keySet()).containsExactlyInAnyOrder(
                "X-Request-Id",
                "role",
                "approval_capable",
                "active_only"
        );
        assertThat(parameters.get("role").path("required").asBoolean()).isFalse();
        assertThat(parameters.get("approval_capable").path("required").asBoolean()).isFalse();
        assertThat(parameters.get("active_only").at("/schema/default").asBoolean()).isTrue();
        assertThat(operation.at("/responses/400/$ref").asText())
                .isEqualTo("#/components/responses/BadRequest");
        assertThat(operation.at("/responses/401/$ref").asText())
                .isEqualTo("#/components/responses/Unauthorized");
        assertThat(operation.at("/responses/403/$ref").asText())
                .isEqualTo("#/components/responses/Forbidden");
    }

    @Test
    void responseSchemasExposeDetailedAndMinimalProjectionsWithoutSensitiveFields() {
        JsonNode envelope = openApi.at("/components/schemas/CompanyMemberListResponse");
        JsonNode item = openApi.at("/components/schemas/CompanyMemberItemResponse");
        JsonNode detailed = openApi.at("/components/schemas/DetailedCompanyMemberResponse");
        JsonNode minimal = openApi.at("/components/schemas/MinimalCompanyMemberResponse");

        assertThat(envelope.path("required"))
                .extracting(JsonNode::asText)
                .containsExactly("items");
        assertThat(item.path("oneOf")).hasSize(2);
        assertThat(propertyNames(detailed)).containsExactlyInAnyOrder(
                "user_id",
                "display_name",
                "roles",
                "active",
                "approval_permission"
        );
        assertThat(propertyNames(minimal)).containsExactlyInAnyOrder("user_id", "display_name");
        assertThat(detailed.path("required")).hasSize(5);
        assertThat(minimal.path("required")).hasSize(2);
        assertThat(detailed.toString()).doesNotContain(
                "email",
                "password",
                "status",
                "version",
                "secret"
        );
    }

    @Test
    void successResponsePublishesBothRoleExamples() {
        JsonNode examples = openApi.at(
                "/paths/~1api~1v1~1company-members/get/responses/200/content/application~1json/examples"
        );

        assertThat(examples.at("/adminOrHr/value/items/0/approval_permission").asBoolean()).isTrue();
        assertThat(examples.at("/viewer/value/items/0/display_name").asText()).isEqualTo("김인사");
        assertThat(examples.at("/viewer/value/items/0").has("roles")).isFalse();
    }

    private Set<String> propertyNames(JsonNode schema) {
        JsonNode properties = schema.path("properties");
        return StreamSupport.stream(
                        ((Iterable<String>) properties::fieldNames).spliterator(),
                        false
                )
                .collect(Collectors.toSet());
    }
}
