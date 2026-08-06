package com.fowoco.server.aiintegration.application.port;

import com.fowoco.server.aiintegration.application.model.AiRuntimeCallContext;
import com.fowoco.server.aiintegration.application.ocr.AiOcrRequest;
import com.fowoco.server.aiintegration.application.ocr.AiOcrResponse;

/**
 * Server-owned boundary for stateless OCR. Implementations must never access the Server database.
 */
public interface AiOcrClient {

    AiOcrResponse recognize(AiOcrRequest request, AiRuntimeCallContext context);
}
