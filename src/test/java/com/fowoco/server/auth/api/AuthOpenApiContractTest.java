package com.fowoco.server.auth.api;

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
class AuthOpenApiContractTest {

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
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        openApi = objectMapper.readTree(response.body());
    }

    @Test
    void bearerJwtSecuritySchemeIsDocumented() {
        JsonNode bearerAuth = openApi.at("/components/securitySchemes/bearerAuth");

        assertThat(bearerAuth.path("type").asText()).isEqualTo("http");
        assertThat(bearerAuth.path("scheme").asText()).isEqualTo("bearer");
        assertThat(bearerAuth.path("bearerFormat").asText()).isEqualTo("JWT");
        assertThat(bearerAuth.path("description").asText())
                .contains("Authorization: Bearer", "company_id", "roles");
    }

    @Test
    void loginDocumentsPublicRequestResponseCookieAndErrors() {
        JsonNode login = openApi.at("/paths/~1api~1v1~1auth~1login/post");

        assertThat(login.path("operationId").asText()).isEqualTo("login");
        assertThat(login.has("security") && !login.path("security").isEmpty()).isFalse();
        assertThat(login.at("/requestBody/content/application~1json/schema/$ref").asText())
                .isEqualTo("#/components/schemas/LoginRequest");
        assertThat(login.at("/responses/200/content/application~1json/schema/$ref").asText())
                .isEqualTo("#/components/schemas/LoginResponse");
        assertThat(login.at("/responses/200/headers/Set-Cookie/$ref").asText())
                .isEqualTo("#/components/headers/RefreshTokenCookie");
        assertThat(login.at("/responses/401/$ref").asText())
                .isEqualTo("#/components/responses/InvalidCredentials");
        assertThat(login.at("/responses/400/$ref").asText())
                .isEqualTo("#/components/responses/BadRequest");
        assertThat(login.has("403")).isFalse();
        assertThat(login.has("404")).isFalse();
    }

    @Test
    void loginSchemasUseSnakeCaseAndDoNotExposeRefreshToken() {
        JsonNode requestSchema = openApi.at("/components/schemas/LoginRequest");
        JsonNode responseProperties = openApi.at("/components/schemas/LoginResponse/properties");

        assertThat(requestSchema.path("required").toString()).contains("email", "password");
        assertThat(requestSchema.at("/properties/email/format").asText()).isEqualTo("email");
        assertThat(requestSchema.at("/properties/email/maxLength").asInt()).isEqualTo(254);
        assertThat(requestSchema.at("/properties/password/format").asText()).isEqualTo("password");
        assertThat(requestSchema.at("/properties/password/writeOnly").asBoolean()).isTrue();
        assertThat(responseProperties.properties())
                .extracting(java.util.Map.Entry::getKey)
                .containsExactlyInAnyOrder(
                        "user_id",
                        "company_id",
                        "company_name",
                        "role",
                        "access_token",
                        "token_type",
                        "expires_in_seconds",
                        "expires_at"
                );
        assertThat(responseProperties.has("refresh_token")).isFalse();
    }

    @Test
    void currentActorEndpointRequiresBearerAuthentication() {
        JsonNode me = openApi.at("/paths/~1api~1v1~1auth~1me/get");

        assertThat(me.path("operationId").asText()).isEqualTo("getCurrentActor");
        assertThat(me.at("/security/0/bearerAuth").isArray()).isTrue();
        assertThat(me.at("/responses/200/content/application~1json/schema/$ref").asText())
                .isEqualTo("#/components/schemas/CurrentActorResponse");
        assertThat(me.at("/responses/401/$ref").asText())
                .isEqualTo("#/components/responses/Unauthorized");
    }
}
