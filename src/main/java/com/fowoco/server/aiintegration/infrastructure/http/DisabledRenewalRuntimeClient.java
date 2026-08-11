package com.fowoco.server.aiintegration.infrastructure.http;

import com.fowoco.server.aiintegration.application.error.AiRuntimeCallException;
import com.fowoco.server.aiintegration.application.error.AiRuntimeFailureCode;
import com.fowoco.server.aiintegration.application.model.AiRuntimeCallContext;
import com.fowoco.server.aiintegration.application.port.RenewalRuntimeClient;
import com.fowoco.server.aiintegration.application.renewal.RenewalRunRequest;
import com.fowoco.server.aiintegration.application.renewal.RenewalRunResponse;

final class DisabledRenewalRuntimeClient implements RenewalRuntimeClient {

    @Override
    public RenewalRunResponse run(RenewalRunRequest request, AiRuntimeCallContext context) {
        throw new AiRuntimeCallException(
                AiRuntimeFailureCode.RUNTIME_DISABLED,
                "AI Renewal Runtime integration is disabled."
        );
    }
}
