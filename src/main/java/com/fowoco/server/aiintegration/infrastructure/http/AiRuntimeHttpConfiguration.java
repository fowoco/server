package com.fowoco.server.aiintegration.infrastructure.http;

import com.fowoco.server.aiintegration.application.document.DocumentGenerationClient;
import com.fowoco.server.aiintegration.application.port.AiRuntimeClient;
import com.fowoco.server.aiintegration.application.port.RenewalAgentModePolicy;
import com.fowoco.server.aiintegration.application.port.RenewalRuntimeClient;
import com.fowoco.server.aiintegration.application.validation.AiRuntimeContractValidator;
import com.fowoco.server.aiintegration.application.validation.RenewalRuntimeContractValidator;
import com.fowoco.server.aiintegration.application.validation.ValidatingAiRuntimeClient;
import com.fowoco.server.aiintegration.application.validation.ValidatingRenewalRuntimeClient;
import com.fowoco.server.file.application.port.DocumentPreviewConverter;
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
    public RenewalAgentModePolicy renewalAgentModePolicy(AiRuntimeProperties properties) {
        return properties::getRenewalAgentMode;
    }

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

    @Bean
    public RenewalRuntimeClient renewalRuntimeClient(
            AiRuntimeProperties properties,
            RenewalRuntimeContractValidator validator,
            ObjectMapper applicationObjectMapper,
            Clock clock
    ) {
        if (!properties.isEnabled()) {
            return new DisabledRenewalRuntimeClient();
        }
        properties.validateEnabledConfiguration();
        ObjectMapper contractObjectMapper = applicationObjectMapper.rebuild()
                .propertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .build();
        RenewalRuntimeClient remote = new RemoteRenewalRuntimeClient(
                properties.getRenewalEndpoint(),
                properties.authorizationHeader(),
                properties.getOverallTimeout(),
                properties.getMaxResponseBytes(),
                properties.getMaxConcurrentCalls(),
                createHttpClient(properties.getConnectTimeout()),
                contractObjectMapper,
                new AiRuntimeCircuitBreaker(
                        properties.getCircuitBreakerFailureThreshold(),
                        properties.getCircuitBreakerOpenDuration(),
                        clock
                )
        );
        return new ValidatingRenewalRuntimeClient(remote, validator);
    }

    @Bean
    public DocumentGenerationClient documentGenerationClient(
            AiRuntimeProperties properties,
            ObjectMapper applicationObjectMapper
    ) {
        if (!properties.isEnabled()) {
            return new DisabledDocumentGenerationClient();
        }
        properties.validateEnabledConfiguration();
        ObjectMapper contractObjectMapper = applicationObjectMapper.rebuild()
                .propertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE)
                .build();
        return new RemoteDocumentGenerationClient(
                properties.getDocumentGenerationEndpoint(),
                properties.authorizationHeader(),
                properties.getOverallTimeout(),
                properties.getMaxDocumentResponseBytes(),
                createHttpClient(properties.getConnectTimeout()),
                contractObjectMapper
        );
    }

    @Bean
    public DocumentPreviewConverter documentPreviewConverter(AiRuntimeProperties properties) {
        if (!properties.isEnabled()) {
            return new DisabledDocumentPreviewConverter();
        }
        properties.validateEnabledConfiguration();
        return new RemoteDocumentPreviewConverter(
                properties.getDocumentConversionEndpoint(),
                properties.authorizationHeader(),
                properties.getDocumentConversionTimeout(),
                properties.getMaxDocumentResponseBytes(),
                createHttpClient(properties.getConnectTimeout())
        );
    }

    static HttpClient createHttpClient(Duration connectTimeout) {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }
}
