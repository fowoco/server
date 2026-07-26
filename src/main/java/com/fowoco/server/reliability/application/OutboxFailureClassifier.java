package com.fowoco.server.reliability.application;

import com.fowoco.server.reliability.infrastructure.serialization.EventPayloadDecodingException;
import org.springframework.stereotype.Component;

@Component
public class OutboxFailureClassifier {

    public FailureClassification classify(Throwable failure) {
        Throwable cause = unwrap(failure);
        if (cause instanceof NonRetryableEventHandlingException exception) {
            return new FailureClassification(exception.errorCode(), false);
        }
        if (cause instanceof EventPayloadDecodingException) {
            return new FailureClassification("EVENT_PAYLOAD_INVALID", false);
        }
        if (cause instanceof RetryableEventHandlingException exception) {
            return new FailureClassification(exception.errorCode(), true);
        }
        return new FailureClassification("EVENT_HANDLER_FAILED", true);
    }

    private Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null
                && (current instanceof java.util.concurrent.CompletionException
                || current instanceof java.lang.reflect.UndeclaredThrowableException)) {
            current = current.getCause();
        }
        return current;
    }

    public record FailureClassification(String errorCode, boolean retryable) {
    }
}
