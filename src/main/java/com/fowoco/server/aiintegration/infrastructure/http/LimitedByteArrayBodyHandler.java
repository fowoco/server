package com.fowoco.server.aiintegration.infrastructure.http;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/**
 * Cancels the HTTP body subscription before an oversized Runtime response is fully buffered.
 */
final class LimitedByteArrayBodyHandler implements HttpResponse.BodyHandler<byte[]> {

    private final int maxBytes;

    LimitedByteArrayBodyHandler(int maxBytes) {
        if (maxBytes < 1) {
            throw new IllegalArgumentException("maxBytes must be positive");
        }
        this.maxBytes = maxBytes;
    }

    @Override
    public HttpResponse.BodySubscriber<byte[]> apply(HttpResponse.ResponseInfo responseInfo) {
        return new LimitedByteArrayBodySubscriber(maxBytes);
    }

    static final class ResponseTooLargeException extends RuntimeException {
        ResponseTooLargeException() {
            super("AI Runtime response exceeded the configured size limit.");
        }
    }

    private static final class LimitedByteArrayBodySubscriber
            implements HttpResponse.BodySubscriber<byte[]> {

        private final int maxBytes;
        private final ByteArrayOutputStream output;
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private Flow.Subscription subscription;
        private int receivedBytes;

        private LimitedByteArrayBodySubscriber(int maxBytes) {
            this.maxBytes = maxBytes;
            this.output = new ByteArrayOutputStream(Math.min(maxBytes, 8_192));
        }

        @Override
        public CompletionStage<byte[]> getBody() {
            return body;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            if (this.subscription != null) {
                subscription.cancel();
                return;
            }
            this.subscription = subscription;
            subscription.request(1);
        }

        @Override
        public void onNext(List<ByteBuffer> item) {
            try {
                for (ByteBuffer buffer : item) {
                    int nextBytes = buffer.remaining();
                    if (nextBytes > maxBytes - receivedBytes) {
                        subscription.cancel();
                        body.completeExceptionally(new ResponseTooLargeException());
                        return;
                    }
                    byte[] chunk = new byte[nextBytes];
                    buffer.get(chunk);
                    output.writeBytes(chunk);
                    receivedBytes += nextBytes;
                }
                subscription.request(1);
            } catch (RuntimeException exception) {
                subscription.cancel();
                body.completeExceptionally(exception);
            }
        }

        @Override
        public void onError(Throwable throwable) {
            body.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            body.complete(output.toByteArray());
        }
    }
}
