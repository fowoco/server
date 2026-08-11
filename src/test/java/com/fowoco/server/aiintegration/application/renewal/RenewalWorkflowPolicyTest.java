package com.fowoco.server.aiintegration.application.renewal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RenewalWorkflowPolicyTest {

    @ParameterizedTest
    @CsvSource({
            "RECONTRACT, WF-CON-001",
            "EMPLOYMENT_PERIOD_EXTENSION, WF-CON-001",
            "STAY_PERIOD_EXTENSION, WF-STY-001"
    })
    void acceptsCanonicalTaskWorkflowPairs(String taskType, String workflowId) {
        assertThat(RenewalWorkflowPolicy.supports(taskType, workflowId)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "RECONTRACT, WF-STY-001",
            "EMPLOYMENT_PERIOD_EXTENSION, WF-STY-001",
            "STAY_PERIOD_EXTENSION, WF-CON-001",
            "DOCUMENT_REQUEST, WF-DOC-001"
    })
    void rejectsCrossedOrUnsupportedTaskWorkflowPairs(String taskType, String workflowId) {
        assertThat(RenewalWorkflowPolicy.supports(taskType, workflowId)).isFalse();
    }

    @Test
    void rejectsMissingTaskOrWorkflowWithoutThrowing() {
        assertThat(RenewalWorkflowPolicy.supports(null, "WF-CON-001")).isFalse();
        assertThat(RenewalWorkflowPolicy.supports("RECONTRACT", null)).isFalse();
    }
}
