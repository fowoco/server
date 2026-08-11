package com.fowoco.server.airun.application.error;

/**
 * Stable, non-sensitive reasons produced while resolving Runtime-requested context.
 */
public enum AiContextResolutionFailureCode {
    INVALID_CONTEXT_RESPONSE,
    KNOWLEDGE_VERSION_MISMATCH,
    UNSUPPORTED_INTENT,
    UNSUPPORTED_WORKFLOW,
    FORBIDDEN_FIELD,
    TARGET_NOT_FOUND,
    TARGET_AMBIGUOUS,
    TARGET_CHANGED,
    CONTEXT_ROUND_LIMIT
}
