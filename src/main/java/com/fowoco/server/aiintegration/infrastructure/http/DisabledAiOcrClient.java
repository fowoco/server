package com.fowoco.server.aiintegration.infrastructure.http;

import com.fowoco.server.aiintegration.application.error.AiRuntimeCallException;
import com.fowoco.server.aiintegration.application.error.AiRuntimeFailureCode;
import com.fowoco.server.aiintegration.application.model.AiRuntimeCallContext;
import com.fowoco.server.aiintegration.application.ocr.AiOcrRequest;
import com.fowoco.server.aiintegration.application.ocr.AiOcrResponse;
import com.fowoco.server.aiintegration.application.port.AiOcrClient;

final class DisabledAiOcrClient implements AiOcrClient {

    @Override
    public AiOcrResponse recognize(AiOcrRequest request, AiRuntimeCallContext context) {
        throw new AiRuntimeCallException(
                AiRuntimeFailureCode.RUNTIME_DISABLED,
                "AI OCR integration is disabled."
        );
    }
}
