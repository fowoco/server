package com.fowoco.server.workerlink.application;

import com.fowoco.server.workerlink.domain.ConversationStatus;
import com.fowoco.server.workerlink.domain.WorkerResponseType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record WorkerResponseQueryResult(
        UUID responseId,
        WorkerResponseType responseType,
        String message,
        Map<String, String> answers,
        List<UUID> uploadIds,
        List<WorkerResponseUploadResult> uploads,
        ConversationStatus conversationStatus,
        boolean unread,
        Instant receivedAt
) {
    public WorkerResponseQueryResult {
        answers = Map.copyOf(answers);
        uploadIds = List.copyOf(uploadIds);
        uploads = List.copyOf(uploads);
    }
}
