package com.fowoco.server.workerlink.application.port;

import com.fowoco.server.workerlink.domain.WorkerResponse;
import com.fowoco.server.workerlink.domain.ConversationStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkerResponseRepository {

    void insert(WorkerResponse workerResponse);

    Optional<WorkerResponse> findByWorkerLinkIdAndIdempotencyKey(UUID workerLinkId, String idempotencyKey);

    Optional<WorkerResponseItem> findByResponseIdAndTaskIdAndCompanyId(
            UUID responseId,
            UUID taskId,
            UUID companyId
    );

    void linkUpload(UUID responseId, UUID storedFileId, UUID companyId);

    boolean isUploadAlreadyLinked(UUID storedFileId, UUID companyId);

    WorkerResponsePage findAllByTaskIdAndCompanyId(
            UUID taskId,
            UUID companyId,
            int page,
            int size
    );

    record WorkerResponseItem(
            WorkerResponse response,
            ConversationStatus conversationStatus,
            List<UUID> uploadIds
    ) {
        public WorkerResponseItem {
            uploadIds = List.copyOf(uploadIds);
        }
    }

    record WorkerResponsePage(
            List<WorkerResponseItem> items,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
        public WorkerResponsePage {
            items = List.copyOf(items);
        }
    }
}
