package com.fowoco.server.workerlink.application;

import com.fowoco.server.workerlink.domain.WorkerResponseType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
public final class WorkerResponsePayloadCodec {

    private final ObjectMapper objectMapper;

    public WorkerResponsePayloadCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String encodeAnswers(Map<String, String> answers) {
        try {
            return objectMapper.writeValueAsString(new TreeMap<>(answers));
        } catch (JacksonException exception) {
            throw new IllegalStateException("worker response answers could not be encoded", exception);
        }
    }

    public Map<String, String> decodeAnswers(String answersJson) {
        try {
            Map<String, String> decoded = objectMapper.readValue(
                    answersJson,
                    new TypeReference<Map<String, String>>() { }
            );
            return Map.copyOf(decoded);
        } catch (JacksonException | NullPointerException exception) {
            throw new IllegalStateException("stored worker response answers are invalid", exception);
        }
    }

    public String fingerprint(
            WorkerResponseType responseType,
            String message,
            List<UUID> uploadIds,
            Map<String, String> answers
    ) {
        Map<String, Object> canonical = new TreeMap<>();
        canonical.put("response_type", responseType.name());
        canonical.put("message", message == null ? "" : message.strip());
        canonical.put("upload_ids", uploadIds.stream().map(UUID::toString).sorted().toList());
        canonical.put("answers", new TreeMap<>(answers));
        try {
            byte[] serialized = objectMapper.writeValueAsString(canonical).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(serialized));
        } catch (JacksonException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("worker response fingerprint could not be created", exception);
        }
    }
}
