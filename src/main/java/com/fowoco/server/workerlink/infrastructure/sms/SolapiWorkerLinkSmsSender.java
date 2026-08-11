package com.fowoco.server.workerlink.infrastructure.sms;

import com.fowoco.server.workerlink.application.port.WorkerLinkSmsMessage;
import com.fowoco.server.workerlink.application.port.WorkerLinkSmsProviderException;
import com.fowoco.server.workerlink.application.port.WorkerLinkSmsSender;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(prefix = "app.worker-link.sms", name = "provider", havingValue = "solapi")
public final class SolapiWorkerLinkSmsSender implements WorkerLinkSmsSender {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final WorkerLinkDeliveryProperties.Sms properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Clock clock;
    private final Supplier<String> saltSupplier;

    @Autowired
    public SolapiWorkerLinkSmsSender(
            WorkerLinkDeliveryProperties properties,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this(
                properties.sms(),
                objectMapper,
                HttpClient.newBuilder().connectTimeout(properties.sms().connectTimeout()).build(),
                clock,
                () -> UUID.randomUUID().toString().replace("-", "")
        );
    }

    SolapiWorkerLinkSmsSender(
            WorkerLinkDeliveryProperties.Sms properties,
            ObjectMapper objectMapper,
            HttpClient httpClient,
            Clock clock,
            Supplier<String> saltSupplier
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.clock = clock;
        this.saltSupplier = saltSupplier;
        requireSolapiCredential(properties.apiKey(), "apiKey");
        requireSolapiCredential(properties.apiSecret(), "apiSecret");
        requireSolapiCredential(properties.senderNumber(), "senderNumber");
        if (!properties.senderNumber().matches("[0-9]{8,11}")) {
            throw new IllegalStateException("SOLAPI senderNumber must contain only 8 to 11 digits");
        }
    }

    @Override
    public void send(WorkerLinkSmsMessage message) {
        try {
            String body = requestBody(message);
            HttpRequest request = HttpRequest.newBuilder(properties.endpoint())
                    .timeout(properties.overallTimeout())
                    .header(HttpHeaders.AUTHORIZATION, authorization())
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<InputStream> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofInputStream()
            );
            try (InputStream responseBody = response.body()) {
                byte[] bytes = responseBody.readNBytes(properties.maxResponseBytes() + 1);
                if (response.statusCode() / 100 != 2) {
                    throw WorkerLinkSmsProviderException.rejected(null);
                }
                if (bytes.length > properties.maxResponseBytes()) {
                    throw WorkerLinkSmsProviderException.unknown(null);
                }
                verifyAcceptedResponse(bytes);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw WorkerLinkSmsProviderException.unknown(exception);
        } catch (IOException exception) {
            throw WorkerLinkSmsProviderException.unknown(exception);
        } catch (GeneralSecurityException exception) {
            throw WorkerLinkSmsProviderException.rejected(exception);
        }
    }

    private void verifyAcceptedResponse(byte[] responseBody) throws JacksonException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode failedMessages = root.get("failedMessageList");
        if (failedMessages != null && failedMessages.size() > 0) {
            throw WorkerLinkSmsProviderException.rejected(null);
        }
        String groupId = root.path("groupInfo").path("groupId").asString("");
        if (groupId.isBlank()) {
            throw WorkerLinkSmsProviderException.unknown(null);
        }
    }

    private String requestBody(WorkerLinkSmsMessage message) throws JacksonException {
        Map<String, Object> payload = Map.of(
                "messages", List.of(Map.of(
                        "to", message.recipientPhone(),
                        "from", properties.senderNumber(),
                        "text", message.content()
                )),
                "showMessageList", false
        );
        return objectMapper.writeValueAsString(payload);
    }

    private String authorization() throws GeneralSecurityException {
        String date = Instant.now(clock).toString();
        String salt = saltSupplier.get();
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(
                properties.apiSecret().getBytes(StandardCharsets.UTF_8),
                HMAC_ALGORITHM
        ));
        String signature = HexFormat.of().formatHex(
                mac.doFinal((date + salt).getBytes(StandardCharsets.UTF_8))
        );
        return "HMAC-SHA256 Apikey=" + properties.apiKey()
                + ", Date=" + date
                + ", salt=" + salt
                + ", signature=" + signature;
    }

    private static void requireSolapiCredential(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("SOLAPI " + fieldName + " must be configured");
        }
    }
}
