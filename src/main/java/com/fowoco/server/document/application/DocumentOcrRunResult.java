package com.fowoco.server.document.application;

import com.fowoco.server.document.domain.DocumentOcrRun;
import java.util.Map;

public record DocumentOcrRunResult(
        DocumentOcrRun run,
        DocumentOcrResultPayload result,
        Map<String, String> correctedFields,
        boolean alreadyRequested
) {

    public DocumentOcrRunResult {
        correctedFields = Map.copyOf(correctedFields);
    }
}
