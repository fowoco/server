package com.fowoco.server.aiintegration.application.validation;

import com.fowoco.server.aiintegration.application.model.AiAnalysisRequest;
import com.fowoco.server.aiintegration.application.model.AiAnalysisResponse;
import com.fowoco.server.aiintegration.application.port.AiRuntimeClient;
import java.util.Objects;

/**
 * Mandatory defensive decorator around a Fake or remote AI Runtime transport.
 */
public final class ValidatingAiRuntimeClient implements AiRuntimeClient {

    private final AiRuntimeClient delegate;
    private final AiRuntimeContractValidator validator;

    public ValidatingAiRuntimeClient(AiRuntimeClient delegate, AiRuntimeContractValidator validator) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.validator = Objects.requireNonNull(validator, "validator must not be null");
    }

    @Override
    public AiAnalysisResponse analyze(AiAnalysisRequest request) {
        validator.validateRequest(request);
        AiAnalysisResponse response = delegate.analyze(request);
        validator.validateResponse(request, response);
        return response;
    }
}
