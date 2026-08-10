package com.fowoco.server.worker.application.port;

import com.fowoco.server.worker.application.WorkerIdentityDocumentStatuses;
import java.util.UUID;

public interface WorkerIdentityDocumentStatusReader {

    WorkerIdentityDocumentStatuses findCurrentStatuses(UUID companyId, UUID workerId);
}
