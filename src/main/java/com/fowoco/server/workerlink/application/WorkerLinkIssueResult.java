package com.fowoco.server.workerlink.application;

import java.time.Instant;

public record WorkerLinkIssueResult(String rawToken, Instant expiresAt) {
}
