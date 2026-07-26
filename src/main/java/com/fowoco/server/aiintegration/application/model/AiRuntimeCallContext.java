package com.fowoco.server.aiintegration.application.model;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Transport metadata propagated to the AI Runtime but excluded from the JSON request body.
 */
public record AiRuntimeCallContext(String traceParent) {

    private static final Pattern TRACEPARENT = Pattern.compile(
            "^00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}$"
    );

    public AiRuntimeCallContext {
        if (traceParent != null) {
            traceParent = traceParent.trim().toLowerCase(Locale.ROOT);
            if (!TRACEPARENT.matcher(traceParent).matches()) {
                throw new IllegalArgumentException("traceParent must be a valid W3C traceparent value");
            }
        }
    }

    public static AiRuntimeCallContext withoutTrace() {
        return new AiRuntimeCallContext(null);
    }
}
