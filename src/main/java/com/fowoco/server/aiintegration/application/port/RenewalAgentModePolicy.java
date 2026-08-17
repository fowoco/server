package com.fowoco.server.aiintegration.application.port;

import com.fowoco.server.aiintegration.application.renewal.RenewalAgentMode;

@FunctionalInterface
public interface RenewalAgentModePolicy {

    RenewalAgentMode currentMode();
}
