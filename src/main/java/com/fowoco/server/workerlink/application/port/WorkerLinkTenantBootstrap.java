package com.fowoco.server.workerlink.application.port;

import java.util.Optional;
import java.util.UUID;

public interface WorkerLinkTenantBootstrap {
    Optional<UUID> findCompanyIdByWorkerLinkTokenHash(String tokenHash);
}
