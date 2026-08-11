package com.fowoco.server.aiintegration.application.renewal;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record RenewalGeneratedDocument(
        @JsonProperty("template_id") String templateId,
        String name,
        String format,
        String status,
        String path,
        String error,
        @JsonProperty("mapped_fields") List<String> mappedFields,
        @JsonProperty("changed_fields") List<String> changedFields,
        Map<String, Object> values
) {
    public RenewalGeneratedDocument {
        mappedFields = mappedFields == null ? List.of() : List.copyOf(mappedFields);
        changedFields = changedFields == null ? List.of() : List.copyOf(changedFields);
        values = values == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
