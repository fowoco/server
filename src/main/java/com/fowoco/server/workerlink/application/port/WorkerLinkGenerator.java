package com.fowoco.server.workerlink.application.port;

public interface WorkerLinkGenerator {

    GeneratedWorkerLinkToken generate();

    record GeneratedWorkerLinkToken(String rawValue, String tokenHash) {
    }
}
