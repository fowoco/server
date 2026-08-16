package com.fowoco.server.worker.application.port;

import com.fowoco.server.worker.application.WorkerAiContextSnapshot;
import java.util.List;
import java.util.UUID;

/**
 * Tenant-scoped Worker lookup used by the AiRun Slot Resolver.
 */
public interface WorkerAiContextReader {

    /**
     * Returns exact matches first. If no exact match exists, conservatively searches the current
     * company using the normalized display-name key. At most two matches are returned so the caller
     * can distinguish not-found, unique and ambiguous targets.
     */
    List<WorkerAiContextSnapshot> findByDisplayName(UUID companyId, String displayName);
}
