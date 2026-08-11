package com.fowoco.server.aiintegration.application.renewal;

import java.util.Map;

/**
 * Canonical task type and Workflow pairs supported by the renewal Runtime contract.
 */
public final class RenewalWorkflowPolicy {

    public static final String CONTRACT_WORKFLOW_ID = "WF-CON-001";
    public static final String STAY_WORKFLOW_ID = "WF-STY-001";

    private static final Map<String, String> WORKFLOW_BY_TASK_TYPE = Map.of(
            "RECONTRACT", CONTRACT_WORKFLOW_ID,
            "EMPLOYMENT_PERIOD_EXTENSION", CONTRACT_WORKFLOW_ID,
            "STAY_PERIOD_EXTENSION", STAY_WORKFLOW_ID
    );

    private RenewalWorkflowPolicy() {
    }

    public static boolean supports(String taskType, String workflowId) {
        return taskType != null
                && workflowId != null
                && workflowId.equals(WORKFLOW_BY_TASK_TYPE.get(taskType));
    }
}
