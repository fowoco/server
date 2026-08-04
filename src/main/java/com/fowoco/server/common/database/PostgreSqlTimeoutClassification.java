package com.fowoco.server.common.database;

public record PostgreSqlTimeoutClassification(
        DatabaseTimeoutType type,
        String sqlState,
        String exceptionType
) {
}
