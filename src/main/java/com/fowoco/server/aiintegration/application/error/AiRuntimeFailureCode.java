package com.fowoco.server.aiintegration.application.error;

/**
 * Stable, non-sensitive reason stored by a future AiAttempt.
 */
public enum AiRuntimeFailureCode {
    INVALID_REQUEST_CONTRACT,
    SENSITIVE_DATA_REJECTED,
    INVALID_RESPONSE_CONTRACT,
    REQUEST_ID_MISMATCH,
    CONTRACT_VERSION_MISMATCH,
    KNOWLEDGE_VERSION_MISMATCH,
    UNEXPECTED_WORKER_REFERENCE,
    UNEXPECTED_WORKFLOW,
    UNEXPECTED_SLOT
}
