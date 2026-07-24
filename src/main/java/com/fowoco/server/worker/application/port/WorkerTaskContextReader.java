package com.fowoco.server.worker.application.port;

import com.fowoco.server.worker.application.WorkerTaskContext;
import java.util.Optional;
import java.util.UUID;

public interface WorkerTaskContextReader {

    Optional<WorkerTaskContext> findByIdAndCompanyId(UUID workerId, UUID companyId);
}
