package com.fowoco.server.aiintegration.infrastructure.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpClient;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class AiRuntimeHttpConfigurationTest {

    @Test
    void createsHttp11ClientWithoutRedirects() {
        Duration connectTimeout = Duration.ofSeconds(2);

        HttpClient client = AiRuntimeHttpConfiguration.createHttpClient(connectTimeout);

        assertThat(client.version()).isEqualTo(HttpClient.Version.HTTP_1_1);
        assertThat(client.connectTimeout()).contains(connectTimeout);
        assertThat(client.followRedirects()).isEqualTo(HttpClient.Redirect.NEVER);
    }
}
