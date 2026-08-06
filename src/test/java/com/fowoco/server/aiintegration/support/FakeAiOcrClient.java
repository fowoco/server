package com.fowoco.server.aiintegration.support;

import com.fowoco.server.aiintegration.application.model.AiRuntimeCallContext;
import com.fowoco.server.aiintegration.application.ocr.AiOcrRequest;
import com.fowoco.server.aiintegration.application.ocr.AiOcrResponse;
import com.fowoco.server.aiintegration.application.port.AiOcrClient;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

public final class FakeAiOcrClient implements AiOcrClient {

    private final Deque<Object> scripts = new ArrayDeque<>();
    private final List<AiOcrRequest> receivedRequests = new ArrayList<>();

    public void enqueueResponse(AiOcrResponse response) {
        scripts.addLast(Objects.requireNonNull(response, "response must not be null"));
    }

    public void enqueueFailure(RuntimeException exception) {
        scripts.addLast(Objects.requireNonNull(exception, "exception must not be null"));
    }

    @Override
    public AiOcrResponse recognize(AiOcrRequest request, AiRuntimeCallContext context) {
        receivedRequests.add(request);
        Object script = scripts.pollFirst();
        if (script == null) {
            throw new AssertionError("FakeAiOcrClient has no scripted result.");
        }
        if (script instanceof RuntimeException exception) {
            throw exception;
        }
        return (AiOcrResponse) script;
    }

    public List<AiOcrRequest> receivedRequests() {
        return List.copyOf(receivedRequests);
    }
}
