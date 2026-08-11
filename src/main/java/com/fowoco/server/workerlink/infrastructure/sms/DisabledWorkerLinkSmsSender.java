package com.fowoco.server.workerlink.infrastructure.sms;

import com.fowoco.server.workerlink.application.port.WorkerLinkSmsMessage;
import com.fowoco.server.workerlink.application.port.WorkerLinkSmsProviderException;
import com.fowoco.server.workerlink.application.port.WorkerLinkSmsSender;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "app.worker-link.sms",
        name = "provider",
        havingValue = "none",
        matchIfMissing = true
)
public final class DisabledWorkerLinkSmsSender implements WorkerLinkSmsSender {

    @Override
    public void send(WorkerLinkSmsMessage message) {
        throw WorkerLinkSmsProviderException.disabled();
    }
}
