package com.fowoco.server.reliability.infrastructure.serialization;

public class EventPayloadDecodingException extends RuntimeException {

    public EventPayloadDecodingException(Throwable cause) {
        super("Stored event payload is invalid.", cause);
    }
}
