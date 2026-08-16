package com.fowoco.server.auth.application.port;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface UserLoginEventRepository {

    void insert(UUID loginEventId, UUID userId, UUID companyId, String deviceSummary, Instant loggedInAt);

    /** Most recent events first, at most {@code limit} rows. */
    List<LoginEventRecord> findRecent(UUID userId, UUID companyId, int limit);

    record LoginEventRecord(String deviceSummary, Instant loggedInAt) {
    }
}
