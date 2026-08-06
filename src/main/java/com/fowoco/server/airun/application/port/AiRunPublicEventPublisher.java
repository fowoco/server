package com.fowoco.server.airun.application.port;

import com.fowoco.server.airun.application.AiRunResult;
import java.util.UUID;

public interface AiRunPublicEventPublisher {

    void publish(UUID companyId, AiRunResult run);
}
