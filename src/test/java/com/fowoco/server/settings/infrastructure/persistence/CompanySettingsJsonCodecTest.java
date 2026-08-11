package com.fowoco.server.settings.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fowoco.server.approval.domain.EvidenceType;
import com.fowoco.server.task.domain.TaskType;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class CompanySettingsJsonCodecTest {

    private final CompanySettingsJsonCodec codec =
            new CompanySettingsJsonCodec(JsonMapper.builder().build());

    @Test
    void evidenceRulesRoundTripWithStableEnumNames() {
        Map<TaskType, Set<EvidenceType>> rules = Map.of(
                TaskType.WORKER_ONBOARDING,
                Set.of(EvidenceType.HR_CONFIRMATION, EvidenceType.DOCUMENT),
                TaskType.DOCUMENT_REQUEST,
                Set.of(EvidenceType.RECEIPT)
        );

        String json = codec.encodeEvidenceRules(rules);

        assertThat(json).isEqualTo(
                "{\"DOCUMENT_REQUEST\":[\"RECEIPT\"],"
                        + "\"WORKER_ONBOARDING\":[\"DOCUMENT\",\"HR_CONFIRMATION\"]}"
        );
        assertThat(codec.decodeEvidenceRules(json)).isEqualTo(rules);
    }

    @Test
    void rejectsUnknownStoredEnumValues() {
        assertThatThrownBy(() -> codec.decodeEvidenceRules("{\"UNKNOWN\":[\"DOCUMENT\"]}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("invalid");
    }
}
