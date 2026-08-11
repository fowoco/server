package com.fowoco.server.aiintegration.application.port;

/**
 * Supplies the Server-side deadline budget for one AI Runtime attempt.
 */
public interface AiRuntimeDeadlinePolicy {

    long attemptDeadlineMs();
}
