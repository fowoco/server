package com.fowoco.server.worker.application.port;

import com.fowoco.server.worker.application.WorkerAiContextSnapshot;
import java.util.List;
import java.util.UUID;

/**
 * Tenant-scoped Worker lookup used by the AiRun Slot Resolver.
 */
public interface WorkerAiContextReader {

    /**
     * Returns at most two matches so the caller can distinguish not-found, unique and ambiguous targets.
     */
    List<WorkerAiContextSnapshot> findByDisplayName(UUID companyId, String displayName);
}
