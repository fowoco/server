package com.fowoco.server.aiintegration.application.port;

import com.fowoco.server.aiintegration.application.model.AiRuntimeCallContext;
import com.fowoco.server.aiintegration.application.renewal.RenewalRunRequest;
import com.fowoco.server.aiintegration.application.renewal.RenewalRunResponse;

@FunctionalInterface
public interface RenewalRuntimeClient {

    RenewalRunResponse run(RenewalRunRequest request, AiRuntimeCallContext context);
}
