package com.fowoco.server.aiintegration.application.document;

public interface DocumentGenerationClient {

    GeneratedDocumentFile generate(DocumentGenerationRequest request);
}
