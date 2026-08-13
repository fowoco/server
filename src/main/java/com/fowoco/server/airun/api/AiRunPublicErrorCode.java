package com.fowoco.server.airun.api;

import java.util.Set;

/**
 * Keeps provider and transport diagnostics internal while exposing one actionable Client code.
 */
final class AiRunPublicErrorCode {

    private static final String AI_UNAVAILABLE = "AI_UNAVAILABLE";
    private static final Set<String> AVAILABILITY_FAILURES = Set.of(
            "RUNTIME_DISABLED",
            "BULKHEAD_FULL",
            "CIRCUIT_OPEN",
            "DEADLINE_EXCEEDED",
            "AUTHENTICATION_FAILED",
            "RATE_LIMITED",
            "RUNTIME_UNAVAILABLE",
            "RESPONSE_TOO_LARGE",
            "RESPONSE_PARSING_FAILED",
            "TRANSPORT_FAILURE"
    );

    private AiRunPublicErrorCode() {
    }

    static String fromInternal(String internalCode) {
        if (internalCode == null) {
            return null;
        }
        return AVAILABILITY_FAILURES.contains(internalCode) ? AI_UNAVAILABLE : internalCode;
    }
}
