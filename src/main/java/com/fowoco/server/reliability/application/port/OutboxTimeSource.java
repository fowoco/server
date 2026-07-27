package com.fowoco.server.reliability.application.port;

import java.time.Instant;

public interface OutboxTimeSource {

    Instant now();
}
