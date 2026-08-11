package com.fowoco.server.task.application.renewal;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record RenewalExecutionCommand(
        String instruction,
        long expectedVersion,
        Map<String, String> slotAnswers
) {
    public RenewalExecutionCommand {
        slotAnswers = slotAnswers == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(slotAnswers));
    }
}
