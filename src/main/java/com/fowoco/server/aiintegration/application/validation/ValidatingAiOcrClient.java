package com.fowoco.server.aiintegration.application.validation;

import com.fowoco.server.aiintegration.application.model.AiRuntimeCallContext;
import com.fowoco.server.aiintegration.application.ocr.AiOcrRequest;
import com.fowoco.server.aiintegration.application.ocr.AiOcrResponse;
import com.fowoco.server.aiintegration.application.port.AiOcrClient;
import java.util.Objects;

public final class ValidatingAiOcrClient implements AiOcrClient {

    private final AiOcrClient delegate;
    private final AiOcrContractValidator validator;

    public ValidatingAiOcrClient(AiOcrClient delegate, AiOcrContractValidator validator) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.validator = Objects.requireNonNull(validator, "validator must not be null");
    }

    @Override
    public AiOcrResponse recognize(AiOcrRequest request, AiRuntimeCallContext context) {
        validator.validateRequest(request);
        AiOcrResponse response = delegate.recognize(request, context);
        validator.validateResponse(request, response);
        return response;
    }
}
