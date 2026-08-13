package com.fowoco.server.workerlink.api;

import com.fowoco.server.workerlink.application.WorkerResponseQueryResult;
import com.fowoco.server.workerlink.domain.ConversationStatus;
import com.fowoco.server.workerlink.domain.WorkerResponseType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WorkerResponseItemResponse(
        UUID responseId,
        WorkerResponseType responseType,
        String message,
        List<UUID> uploadIds,
        List<WorkerResponseUploadItemResponse> uploads,
        ConversationStatus conversationStatus,
        boolean unread,
        Instant receivedAt
) {
    static WorkerResponseItemResponse from(WorkerResponseQueryResult result) {
        return new WorkerResponseItemResponse(
                result.responseId(),
                result.responseType(),
                result.message(),
                result.uploadIds(),
                result.uploads().stream().map(WorkerResponseUploadItemResponse::from).toList(),
                result.conversationStatus(),
                result.unread(),
                result.receivedAt()
        );
    }
}
