package com.fowoco.server.auth.application;

import com.fowoco.server.auth.application.port.PasswordResetTokenHashPort;
import com.fowoco.server.auth.infrastructure.security.PasswordResetRateLimitProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * MVP용 단일 instance IP rate limiter입니다. 원본 IP는 저장하지 않고 hash key만 메모리에 보관합니다.
 */
@Component
public class PasswordResetRequestRateLimiter {

    private static final int CLEANUP_INTERVAL = 256;

    private final PasswordResetRateLimitProperties properties;
    private final PasswordResetTokenHashPort hashPort;
    private final Clock clock;
    private final Map<String, WindowCounter> counters = new HashMap<>();
    private int accessCount;

    public PasswordResetRequestRateLimiter(
            PasswordResetRateLimitProperties properties,
            PasswordResetTokenHashPort hashPort,
            Clock clock
    ) {
        this.properties = properties;
        this.hashPort = hashPort;
        this.clock = clock;
    }

    public synchronized boolean tryAcquire(String remoteAddress) {
        Instant now = clock.instant();
        if (++accessCount % CLEANUP_INTERVAL == 0) {
            removeExpired(now);
        }

        String key = hashPort.hash(normalizeAddress(remoteAddress));
        WindowCounter current = counters.get(key);
        if (current == null || !now.isBefore(current.windowStartedAt().plus(properties.window()))) {
            counters.put(key, new WindowCounter(now, 1));
            return true;
        }
        if (current.count() >= properties.maxRequests()) {
            return false;
        }
        counters.put(key, new WindowCounter(current.windowStartedAt(), current.count() + 1));
        return true;
    }

    private void removeExpired(Instant now) {
        Iterator<WindowCounter> iterator = counters.values().iterator();
        while (iterator.hasNext()) {
            WindowCounter counter = iterator.next();
            if (!now.isBefore(counter.windowStartedAt().plus(properties.window()))) {
                iterator.remove();
            }
        }
    }

    private String normalizeAddress(String remoteAddress) {
        return remoteAddress == null || remoteAddress.isBlank() ? "unknown" : remoteAddress.strip();
    }

    private record WindowCounter(Instant windowStartedAt, int count) {
    }
}
