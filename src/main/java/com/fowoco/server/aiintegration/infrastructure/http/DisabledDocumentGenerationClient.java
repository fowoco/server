package com.fowoco.server.aiintegration.infrastructure.http;

import com.fowoco.server.aiintegration.application.document.DocumentGenerationClient;
import com.fowoco.server.aiintegration.application.document.DocumentGenerationRequest;
import com.fowoco.server.aiintegration.application.document.GeneratedDocumentFile;
import com.fowoco.server.aiintegration.application.error.AiRuntimeCallException;
import com.fowoco.server.aiintegration.application.error.AiRuntimeFailureCode;

final class DisabledDocumentGenerationClient implements DocumentGenerationClient {

    @Override
    public GeneratedDocumentFile generate(DocumentGenerationRequest request) {
        throw new AiRuntimeCallException(
                AiRuntimeFailureCode.RUNTIME_DISABLED,
                "AI document generation is disabled."
        );
    }
}
