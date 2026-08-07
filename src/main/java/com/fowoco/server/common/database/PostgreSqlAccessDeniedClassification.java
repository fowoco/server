package com.fowoco.server.common.database;

public record PostgreSqlAccessDeniedClassification(
        DatabaseAccessDeniedType type,
        String sqlState,
        String exceptionType
) {

    public boolean confirmed() {
        return type == DatabaseAccessDeniedType.CONFIRMED_ACCESS_DENIED;
    }
}
