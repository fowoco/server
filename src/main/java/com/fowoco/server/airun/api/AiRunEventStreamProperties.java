package com.fowoco.server.airun.api;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai-run.sse")
public final class AiRunEventStreamProperties {

    private final Duration timeout;
    private final Duration historyRetention;
    private final int historySize;
    private final int maxConnectionsPerUserRun;

    public AiRunEventStreamProperties(
            Duration timeout,
            Duration historyRetention,
            int historySize,
            int maxConnectionsPerUserRun
    ) {
        this.timeout = requireDuration(timeout, "timeout", Duration.ofSeconds(1), Duration.ofMinutes(30));
        this.historyRetention = requireDuration(
                historyRetention,
                "historyRetention",
                Duration.ofMinutes(1),
                Duration.ofHours(24)
        );
        if (historySize < 1 || historySize > 100) {
            throw new IllegalArgumentException("AI Run SSE historySize must be between 1 and 100");
        }
        if (maxConnectionsPerUserRun < 1 || maxConnectionsPerUserRun > 10) {
            throw new IllegalArgumentException("AI Run SSE connection limit must be between 1 and 10");
        }
        this.historySize = historySize;
        this.maxConnectionsPerUserRun = maxConnectionsPerUserRun;
    }

    public Duration timeout() {
        return timeout;
    }

    public Duration historyRetention() {
        return historyRetention;
    }

    public int historySize() {
        return historySize;
    }

    public int maxConnectionsPerUserRun() {
        return maxConnectionsPerUserRun;
    }

    private Duration requireDuration(Duration value, String name, Duration minimum, Duration maximum) {
        if (value == null || value.compareTo(minimum) < 0 || value.compareTo(maximum) > 0) {
            throw new IllegalArgumentException("AI Run SSE " + name + " is outside the allowed range");
        }
        return value;
    }
}
