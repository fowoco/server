package com.fowoco.server.workerlink.application;

import com.fowoco.server.workerlink.domain.ConversationStatus;
import com.fowoco.server.workerlink.domain.WorkerResponseType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WorkerResponseQueryResult(
        UUID responseId,
        WorkerResponseType responseType,
        String message,
        List<UUID> uploadIds,
        ConversationStatus conversationStatus,
        boolean unread,
        Instant receivedAt
) {
    public WorkerResponseQueryResult {
        uploadIds = List.copyOf(uploadIds);
    }
}
