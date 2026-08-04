package com.fowoco.server.workerlink.application;

import java.time.Instant;
import java.util.UUID;

public record WorkerResponseSubmitResult(UUID responseId, Instant receivedAt) {
}
