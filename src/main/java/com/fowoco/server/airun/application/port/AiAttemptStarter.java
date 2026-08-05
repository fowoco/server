package com.fowoco.server.airun.application.port;

import com.fowoco.server.aiintegration.application.model.AiAnalysisPhase;
import com.fowoco.server.aiintegration.application.model.AnalysisInput;
import java.util.UUID;

/**
 * Starts a durable Attempt before one Runtime transport call.
 * The PostgreSQL implementation is owned by #24.
 */
public interface AiAttemptStarter {

    UUID startAttempt(
            UUID companyId,
            UUID requestId,
            AiAnalysisPhase phase,
            int contextRound,
            AnalysisInput analysisInput
    );
}
