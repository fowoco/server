package com.fowoco.server.aiintegration.support;

import com.fowoco.server.aiintegration.application.model.AiAnalysisRequest;
import com.fowoco.server.aiintegration.application.model.AiAnalysisResponse;
import com.fowoco.server.aiintegration.application.port.AiRuntimeClient;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Test-only scripted Runtime that makes no network request.
 */
public final class FakeAiRuntimeClient implements AiRuntimeClient {

    private final Deque<Function<AiAnalysisRequest, AiAnalysisResponse>> scripts = new ArrayDeque<>();
    private final List<AiAnalysisRequest> receivedRequests = new ArrayList<>();

    public void enqueueResponse(AiAnalysisResponse response) {
        Objects.requireNonNull(response, "response must not be null");
        scripts.addLast(request -> response);
    }

    public void enqueueFailure(RuntimeException exception) {
        Objects.requireNonNull(exception, "exception must not be null");
        scripts.addLast(request -> {
            throw exception;
        });
    }

    @Override
    public AiAnalysisResponse analyze(AiAnalysisRequest request) {
        receivedRequests.add(request);
        Function<AiAnalysisRequest, AiAnalysisResponse> script = scripts.pollFirst();
        if (script == null) {
            throw new AssertionError("FakeAiRuntimeClient has no scripted result.");
        }
        return script.apply(request);
    }

    public List<AiAnalysisRequest> receivedRequests() {
        return List.copyOf(receivedRequests);
    }
}
