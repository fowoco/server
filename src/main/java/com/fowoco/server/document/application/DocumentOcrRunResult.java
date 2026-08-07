package com.fowoco.server.document.application;

import com.fowoco.server.document.domain.DocumentOcrRun;

public record DocumentOcrRunResult(
        DocumentOcrRun run,
        DocumentOcrResultPayload result,
        boolean alreadyRequested
) {
}
