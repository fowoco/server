package com.fowoco.server.approval.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fowoco.server.approval.application.error.ApprovalErrorCode;
import com.fowoco.server.common.error.ApiException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class SafeJsonServiceTest {

    private final SafeJsonService safeJsonService = new SafeJsonService(new ObjectMapper());

    @Test
    void fingerprintIsStableEvenWhenMapInsertionOrderDiffers() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("wage", 2_500_000);
        first.put("contract_end_date", "2027-08-31");
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("contract_end_date", "2027-08-31");
        second.put("wage", 2_500_000);

        assertThat(safeJsonService.fingerprint(first))
                .isEqualTo(safeJsonService.fingerprint(second))
                .matches("[0-9a-f]{64}");
    }

    @Test
    void snapshotRejectsSensitiveKeysAndValues() {
        assertRejected(Map.of("passport_number", "M12345678"));
        assertRejected(Map.of("note", "연락처 010-1234-5678"));
        assertRejected(Map.of("authorization", "Bearer secret-value"));
    }

    private void assertRejected(Map<String, Object> snapshot) {
        assertThatThrownBy(() -> safeJsonService.write(snapshot, true))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(ApprovalErrorCode.SENSITIVE_SNAPSHOT_REJECTED)
                );
    }
}
