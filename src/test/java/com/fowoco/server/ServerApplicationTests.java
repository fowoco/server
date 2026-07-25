package com.fowoco.server;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ServerApplicationTests {

	@LocalServerPort
	private int port;

	@Autowired
	private Flyway flyway;

	private final HttpClient httpClient = HttpClient.newHttpClient();

	@Test
	void healthApiIsPublic() throws Exception {
		HttpResponse<String> response = get("/health", "test-request-001");

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).isEqualTo("OK");
		assertThat(response.headers().firstValue("X-Request-Id")).contains("test-request-001");
	}

	@Test
	void openApiIncludesHealthApi() throws Exception {
		HttpResponse<String> response = get("/v3/api-docs");
		String successRequestIdHeader = JsonPath.read(
				response.body(),
				"$.paths['/health'].get.responses['200'].headers['X-Request-Id']['$ref']"
		);

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).contains("/health", "ApiErrorResponse", "X-Request-Id");
		assertThat(successRequestIdHeader).isEqualTo("#/components/headers/RequestId");
	}

	@Test
	void swaggerUiIsPublic() throws Exception {
		HttpResponse<String> response = get("/swagger-ui/index.html");

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).contains("Swagger UI");
	}

	@Test
	void openApiRequestSchemasUseCanonicalSnakeCaseProperties() throws Exception {
		HttpResponse<String> response = get("/v3/api-docs");
		Map<String, Object> workerCreateProperties = JsonPath.read(
				response.body(),
				"$.components.schemas.WorkerCreateRequest.properties"
		);
		Map<String, Object> workerPatchProperties = JsonPath.read(
				response.body(),
				"$.components.schemas.WorkerPatchRequest.properties"
		);
		Map<String, Object> documentCreateProperties = JsonPath.read(
				response.body(),
				"$.components.schemas.WorkerDocumentCreateRequest.properties"
		);
		Map<String, Object> documentPatchProperties = JsonPath.read(
				response.body(),
				"$.components.schemas.WorkerDocumentPatchRequest.properties"
		);

		assertThat(workerCreateProperties.keySet())
				.contains("display_name", "nationality_code", "preferred_language")
				.noneMatch(property -> property.matches(".*[A-Z].*"));
		assertThat(workerPatchProperties.keySet())
				.contains("work_status", "stay_expiry_date", "expected_version")
				.noneMatch(property -> property.matches(".*[A-Z].*"));
		assertThat(documentCreateProperties.keySet())
				.contains("document_type", "submission_status", "expiry_date")
				.noneMatch(property -> property.matches(".*[A-Z].*"));
		assertThat(documentPatchProperties.keySet())
				.contains("document_type", "submission_status", "expected_version")
				.noneMatch(property -> property.matches(".*[A-Z].*"));
	}

	@Test
	void reactDevelopmentOriginCanSendPreflightRequest() throws Exception {
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create("http://localhost:" + port + "/health"))
				.header("Origin", "http://localhost:5173")
				.header("Access-Control-Request-Method", "GET")
				.method("OPTIONS", HttpRequest.BodyPublishers.noBody())
				.build();

		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.headers().firstValue("Access-Control-Allow-Origin"))
				.contains("http://localhost:5173");
	}

	@Test
	void unfinishedApiIsProtected() throws Exception {
		HttpResponse<String> response = get("/api/v1/workers");
		String requestId = response.headers().firstValue("X-Request-Id").orElseThrow();

		assertThat(response.statusCode()).isEqualTo(401);
		assertThat(response.body()).contains(
				"AUTHENTICATION_REQUIRED",
				"\"request_id\":\"" + requestId + "\""
		);
	}

	@Test
	void flywayAppliesAndValidatesAvailableMigrations() {
		flyway.validate();
		assertThat(flyway.info().current()).isNotNull();
		assertThat(flyway.info().pending()).isEmpty();
	}

	private HttpResponse<String> get(String path) throws Exception {
		return send(HttpRequest.newBuilder().uri(URI.create("http://localhost:" + port + path)));
	}

	private HttpResponse<String> get(String path, String requestId) throws Exception {
		return send(HttpRequest.newBuilder()
				.uri(URI.create("http://localhost:" + port + path))
				.header("X-Request-Id", requestId));
	}

	private HttpResponse<String> send(HttpRequest.Builder requestBuilder) throws Exception {
		HttpRequest request = requestBuilder.GET().build();
		return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
	}
}
