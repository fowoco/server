package com.fowoco.server.task.application;

public record UpdateChecklistItemCommand(
        boolean completed,
        long expectedVersion,
        long expectedTaskVersion
) {
}
