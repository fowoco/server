package com.fowoco.server.aiintegration.application.validation;

import com.fowoco.server.aiintegration.application.model.AiRuntimeCallContext;
import com.fowoco.server.aiintegration.application.port.RenewalRuntimeClient;
import com.fowoco.server.aiintegration.application.renewal.RenewalRunRequest;
import com.fowoco.server.aiintegration.application.renewal.RenewalRunResponse;
import java.util.Objects;

public final class ValidatingRenewalRuntimeClient implements RenewalRuntimeClient {

    private final RenewalRuntimeClient delegate;
    private final RenewalRuntimeContractValidator validator;

    public ValidatingRenewalRuntimeClient(
            RenewalRuntimeClient delegate,
            RenewalRuntimeContractValidator validator
    ) {
        this.delegate = Objects.requireNonNull(delegate);
        this.validator = Objects.requireNonNull(validator);
    }

    @Override
    public RenewalRunResponse run(RenewalRunRequest request, AiRuntimeCallContext context) {
        validator.validateRequest(request);
        RenewalRunResponse response = delegate.run(request, context);
        validator.validateResponse(request, response);
        return response;
    }
}
