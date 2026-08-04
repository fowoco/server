package com.fowoco.server.workerlink.infrastructure.security;

import com.fowoco.server.workerlink.application.port.WorkerLinkGenerator;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public final class SecureWorkerLinkGenerator implements WorkerLinkGenerator {

    private static final int ENTROPY_BYTES = 32;

    private final SecureRandom secureRandom;
    private final WorkerLinkHasher workerLinkHasher;

    public SecureWorkerLinkGenerator(WorkerLinkHasher workerLinkHasher) {
        this.secureRandom = new SecureRandom();
        this.workerLinkHasher = Objects.requireNonNull(workerLinkHasher, "workerLinkHasher must not be null");
    }

    @Override
    public GeneratedWorkerLinkToken generate() {
        byte[] tokenBytes = new byte[ENTROPY_BYTES];
        secureRandom.nextBytes(tokenBytes);
        String rawValue = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        String tokenHash = workerLinkHasher.hash(rawValue);
        return new GeneratedWorkerLinkToken(rawValue, tokenHash);
    }
}
