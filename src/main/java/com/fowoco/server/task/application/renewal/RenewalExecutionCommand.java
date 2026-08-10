package com.fowoco.server.task.application.renewal;

public record RenewalExecutionCommand(String instruction, long expectedVersion) {
}
