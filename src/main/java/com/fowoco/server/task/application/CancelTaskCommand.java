package com.fowoco.server.task.application;

public record CancelTaskCommand(long expectedVersion, String reason) {
}
