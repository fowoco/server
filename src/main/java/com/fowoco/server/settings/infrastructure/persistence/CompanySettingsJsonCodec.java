package com.fowoco.server.settings.infrastructure.persistence;

import com.fowoco.server.approval.domain.EvidenceType;
import com.fowoco.server.task.domain.TaskType;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
class CompanySettingsJsonCodec {

    private final ObjectMapper objectMapper;

    CompanySettingsJsonCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    String encodeEvidenceRules(Map<TaskType, Set<EvidenceType>> evidenceRules) {
        Map<String, List<String>> encoded = new LinkedHashMap<>();
        evidenceRules.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> encoded.put(
                        entry.getKey().name(),
                        entry.getValue().stream().sorted().map(Enum::name).toList()
                ));
        try {
            return objectMapper.writeValueAsString(encoded);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Company evidence rules cannot be encoded.", exception);
        }
    }

    @SuppressWarnings("unchecked")
    Map<TaskType, Set<EvidenceType>> decodeEvidenceRules(String evidenceRulesJson) {
        try {
            Map<String, Object> decoded = objectMapper.readValue(evidenceRulesJson, Map.class);
            if (decoded == null) {
                throw new IllegalArgumentException("Evidence rules must be a JSON object.");
            }
            EnumMap<TaskType, Set<EvidenceType>> evidenceRules = new EnumMap<>(TaskType.class);
            decoded.forEach((rawTaskType, rawEvidenceTypes) -> evidenceRules.put(
                    TaskType.valueOf(rawTaskType),
                    decodeEvidenceTypes(rawEvidenceTypes)
            ));
            return evidenceRules;
        } catch (JacksonException | IllegalArgumentException exception) {
            throw new IllegalStateException("Stored company evidence rules are invalid.", exception);
        }
    }

    private Set<EvidenceType> decodeEvidenceTypes(Object rawEvidenceTypes) {
        if (!(rawEvidenceTypes instanceof List<?> values)) {
            throw new IllegalArgumentException("Evidence rule value must be an array.");
        }
        List<EvidenceType> evidenceTypes = new ArrayList<>();
        for (Object value : values) {
            if (!(value instanceof String evidenceType)) {
                throw new IllegalArgumentException("Evidence type must be a string.");
            }
            evidenceTypes.add(EvidenceType.valueOf(evidenceType));
        }
        if (evidenceTypes.size() != evidenceTypes.stream().distinct().count()) {
            throw new IllegalArgumentException("Evidence rule must not contain duplicates.");
        }
        return evidenceTypes.isEmpty()
                ? EnumSet.noneOf(EvidenceType.class)
                : EnumSet.copyOf(evidenceTypes);
    }
}
