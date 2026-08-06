package com.fowoco.server.task.application.port;

import com.fowoco.server.auth.application.ActorContext;
import com.fowoco.server.common.web.RequestMetadata;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface AiCandidateTaskCreator {

    CreationResult create(CreationCommand command, ActorContext actor, RequestMetadata metadata);

    record CreationCommand(
            UUID aiRunId,
            UUID candidateId,
            UUID workerId,
            String detectedIntent,
            String candidateWorkflowId,
            Map<String, String> extractedSlots
    ) {
        public CreationCommand {
            extractedSlots = Map.copyOf(extractedSlots);
        }
    }

    record CreationResult(UUID caseId, List<UUID> taskIds) {
        public CreationResult {
            taskIds = List.copyOf(taskIds);
        }
    }
}
