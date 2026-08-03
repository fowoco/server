package com.fowoco.server.aiintegration.infrastructure.http;

import com.fowoco.server.aiintegration.application.error.AiRuntimeCallException;
import com.fowoco.server.aiintegration.application.error.AiRuntimeFailureCode;
import com.fowoco.server.aiintegration.application.model.AiAnalysisRequest;
import com.fowoco.server.aiintegration.application.model.AiAnalysisResponse;
import com.fowoco.server.aiintegration.application.model.AiRuntimeCallContext;
import com.fowoco.server.aiintegration.application.port.AiRuntimeClient;

final class DisabledAiRuntimeClient implements AiRuntimeClient {

    @Override
    public AiAnalysisResponse analyze(AiAnalysisRequest request, AiRuntimeCallContext context) {
        throw new AiRuntimeCallException(
                AiRuntimeFailureCode.RUNTIME_DISABLED,
                "AI Runtime integration is disabled."
        );
    }
}
