package com.fowoco.server.company.application.port;

import java.time.Instant;
import java.util.UUID;

public interface CompanySettingsProvisioner {

    void provisionDefaults(UUID companyId, Instant now);
}
