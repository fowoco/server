package com.fowoco.server.aiintegration.application.port;

import com.fowoco.server.aiintegration.application.model.AiAnalysisRequest;
import com.fowoco.server.aiintegration.application.model.AiAnalysisResponse;

/**
 * Server-owned port for one attempt against a separately deployed AI Runtime.
 *
 * <p>Implementations must not call a model Provider directly and must not retry transparently.</p>
 */
@FunctionalInterface
public interface AiRuntimeClient {

    AiAnalysisResponse analyze(AiAnalysisRequest request);
}
