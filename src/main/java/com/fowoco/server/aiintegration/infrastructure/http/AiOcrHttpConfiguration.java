package com.fowoco.server.aiintegration.infrastructure.http;

import com.fowoco.server.aiintegration.application.port.AiOcrClient;
import com.fowoco.server.aiintegration.application.validation.AiOcrContractValidator;
import com.fowoco.server.aiintegration.application.validation.ValidatingAiOcrClient;
import java.net.http.HttpClient;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.type.LogicalType;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiOcrProperties.class)
public class AiOcrHttpConfiguration {

    @Bean
    public AiOcrClient aiOcrClient(
            AiOcrProperties properties,
            AiOcrContractValidator validator,
            ObjectMapper applicationObjectMapper,
            Clock clock
    ) {
        if (!properties.isEnabled()) {
            return new DisabledAiOcrClient();
        }
        properties.validateEnabledConfiguration();
        ObjectMapper contractObjectMapper = createContractObjectMapper(applicationObjectMapper);
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        AiRuntimeCircuitBreaker circuitBreaker = new AiRuntimeCircuitBreaker(
                properties.getCircuitBreakerFailureThreshold(),
                properties.getCircuitBreakerOpenDuration(),
                clock
        );
        AiOcrClient remote = new RemoteAiOcrClient(
                properties.getEndpoint(),
                properties.authorizationHeader(),
                properties.getOverallTimeout(),
                properties.getMaxResponseBytes(),
                properties.getMaxConcurrentCalls(),
                httpClient,
                contractObjectMapper,
                circuitBreaker
        );
        return new ValidatingAiOcrClient(remote, validator);
    }

    static ObjectMapper createContractObjectMapper(ObjectMapper applicationObjectMapper) {
        return applicationObjectMapper.rebuild()
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .withCoercionConfig(LogicalType.Textual, config -> {
                    config.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail);
                    config.setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
                    config.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
                })
                .withCoercionConfig(LogicalType.Float, config ->
                        config.setCoercion(CoercionInputShape.String, CoercionAction.Fail))
                .build();
    }
}
