package com.fowoco.server.reliability.infrastructure.serialization;

import com.fowoco.server.reliability.domain.SafeEventPayload;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class EventPayloadCodec {

    private final ObjectMapper objectMapper;

    public EventPayloadCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String encode(SafeEventPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload.values());
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Event payload cannot be encoded.", exception);
        }
    }

    @SuppressWarnings("unchecked")
    public SafeEventPayload decode(String payloadJson) {
        try {
            Map<String, Object> values = objectMapper.readValue(payloadJson, Map.class);
            return SafeEventPayload.of(values.keySet(), values);
        } catch (JacksonException | IllegalArgumentException exception) {
            throw new EventPayloadDecodingException(exception);
        }
    }
}
