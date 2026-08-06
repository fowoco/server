package com.fowoco.server.aiintegration.infrastructure.http;

import com.fowoco.server.aiintegration.application.port.AiRuntimeClient;
import com.fowoco.server.aiintegration.application.validation.AiRuntimeContractValidator;
import com.fowoco.server.aiintegration.application.validation.ValidatingAiRuntimeClient;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiRuntimeProperties.class)
public class AiRuntimeHttpConfiguration {

    @Bean
    public AiRuntimeClient aiRuntimeClient(
            AiRuntimeProperties properties,
            AiRuntimeContractValidator validator,
            ObjectMapper applicationObjectMapper,
            Clock clock
    ) {
        if (!properties.isEnabled()) {
            return new DisabledAiRuntimeClient();
        }
        properties.validateEnabledConfiguration();

        ObjectMapper contractObjectMapper = applicationObjectMapper.rebuild()
                .propertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .build();
        HttpClient httpClient = createHttpClient(properties.getConnectTimeout());
        AiRuntimeCircuitBreaker circuitBreaker = new AiRuntimeCircuitBreaker(
                properties.getCircuitBreakerFailureThreshold(),
                properties.getCircuitBreakerOpenDuration(),
                clock
        );
        AiRuntimeClient remote = new RemoteAiRuntimeClient(
                properties.getEndpoint(),
                properties.authorizationHeader(),
                properties.getOverallTimeout(),
                properties.getMaxResponseBytes(),
                properties.getMaxConcurrentCalls(),
                httpClient,
                contractObjectMapper,
                circuitBreaker
        );
        return new ValidatingAiRuntimeClient(remote, validator);
    }

    static HttpClient createHttpClient(Duration connectTimeout) {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }
}
