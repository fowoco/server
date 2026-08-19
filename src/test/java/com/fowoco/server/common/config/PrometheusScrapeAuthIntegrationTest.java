package com.fowoco.server.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * app.observability.prometheus-scrape-password가 설정된 prod류 환경에서
 * /actuator/prometheus가 그 계정의 Basic Auth로만 열리는지 검증한다.
 * 값이 비어있을 때(기본값)의 전체 거부는 {@link com.fowoco.server.ServerApplicationTests}에서 이미 검증.
 */
@ActiveProfiles("test")
@TestPropertySource(properties = "app.observability.prometheus-scrape-password=test-scrape-secret")
@AutoConfigureMetrics
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PrometheusScrapeAuthIntegrationTest {

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    void rejectsRequestsWithoutCredentials() throws Exception {
        HttpResponse<String> response = get(null);

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void rejectsWrongPassword() throws Exception {
        HttpResponse<String> response = get(basicAuthHeader("prometheus", "wrong-password"));

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void acceptsTheConfiguredScrapeCredential() throws Exception {
        HttpResponse<String> response = get(basicAuthHeader("prometheus", "test-scrape-secret"));

        assertThat(response.statusCode()).isEqualTo(200);
    }

    private String basicAuthHeader(String username, String password) {
        String raw = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> get(String authorizationHeader) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/actuator/prometheus")
        );
        if (authorizationHeader != null) {
            builder.header("Authorization", authorizationHeader);
        }
        return httpClient.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
    }
}
