package com.fowoco.server.aiintegration.application.port;

import com.fowoco.server.aiintegration.application.model.AiAnalysisRequest;
import com.fowoco.server.aiintegration.application.model.AiAnalysisResponse;
import com.fowoco.server.aiintegration.application.model.AiRuntimeCallContext;

/**
 * Server-owned port for one attempt against a separately deployed AI Runtime.
 *
 * <p>Implementations must not call a model Provider directly and must not retry transparently.</p>
 */
@FunctionalInterface
public interface AiRuntimeClient {

    AiAnalysisResponse analyze(AiAnalysisRequest request, AiRuntimeCallContext context);

    default AiAnalysisResponse analyze(AiAnalysisRequest request) {
        return analyze(request, AiRuntimeCallContext.withoutTrace());
    }
}
