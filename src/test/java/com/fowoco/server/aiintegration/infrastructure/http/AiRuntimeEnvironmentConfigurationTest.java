package com.fowoco.server.aiintegration.infrastructure.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class AiRuntimeEnvironmentConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void buildsAllAgentEndpointsFromSharedBaseUrlAndUsesInternalToken() {
        contextRunner
                .withPropertyValues(
                        "FOWOCO_AI_BASE_URL=https://agent-demo.example.com",
                        "FOWOCO_AI_INTERNAL_TOKEN=demo-internal-token"
                )
                .run(context -> {
                    AiRuntimeProperties properties = context.getBean(AiRuntimeProperties.class);

                    assertThat(properties.getEndpoint()).isEqualTo(URI.create(
                            "https://agent-demo.example.com/internal/v1/analyses"
                    ));
                    assertThat(properties.getRenewalEndpoint()).isEqualTo(URI.create(
                            "https://agent-demo.example.com/internal/v1/workflows/renewal/run"
                    ));
                    assertThat(properties.getDocumentGenerationEndpoint()).isEqualTo(URI.create(
                            "https://agent-demo.example.com/api/v1/documents/generate"
                    ));
                    assertThat(properties.authorizationHeader())
                            .isEqualTo("Bearer demo-internal-token");
                });
    }

    @Test
    void keepsLegacyFullEndpointOverrideForExistingDeployments() {
        contextRunner
                .withPropertyValues(
                        "FOWOCO_AI_BASE_URL=https://agent-demo.example.com",
                        "FOWOCO_AI_INTERNAL_TOKEN=demo-internal-token",
                        "AI_RUNTIME_ENDPOINT=https://legacy.example.com/internal/v1/analyses"
                )
                .run(context -> assertThat(context.getBean(AiRuntimeProperties.class).getEndpoint())
                        .isEqualTo(URI.create("https://legacy.example.com/internal/v1/analyses")));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AiRuntimeProperties.class)
    static class PropertiesConfiguration {
    }
}
