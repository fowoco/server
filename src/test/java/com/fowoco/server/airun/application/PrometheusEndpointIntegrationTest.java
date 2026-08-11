package com.fowoco.server.airun.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles({"test", "observability"})
@AutoConfigureMetrics
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PrometheusEndpointIntegrationTest {

    private static final UUID REQUEST_ID = UUID.fromString(
            "10000000-0000-0000-0000-000000000001"
    );
    private static final UUID ATTEMPT_ID = UUID.fromString(
            "20000000-0000-0000-0000-000000000001"
    );

    @LocalServerPort
    private int port;

    @Autowired
    private AiRunExecutionTelemetry telemetry;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    void observabilityProfileExposesPrometheusMetricsWithoutBusinessAuthentication() throws Exception {
        telemetry.measure(
                REQUEST_ID,
                ATTEMPT_ID,
                AiRunExecutionTelemetry.Phase.PLAN,
                AiRunExecutionTelemetry.Stage.PLAN_RUNTIME_CALL,
                () -> "measured"
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/actuator/prometheus"))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains(
                        "fowoco_ai_pipeline_stage_seconds_count",
                        "phase=\"PLAN\"",
                        "stage=\"PLAN_RUNTIME_CALL\"",
                        "status=\"SUCCESS\""
                );
    }
}
