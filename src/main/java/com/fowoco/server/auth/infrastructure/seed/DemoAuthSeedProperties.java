package com.fowoco.server.auth.infrastructure.seed;

import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.demo-seed")
public record DemoAuthSeedProperties(
        boolean enabled,
        UUID companyId,
        String companyName,
        UUID adminUserId,
        String adminEmail,
        String adminPassword
) {

    @Override
    public String toString() {
        return "DemoAuthSeedProperties[enabled=" + enabled
                + ", companyId=" + companyId
                + ", companyName=" + companyName
                + ", adminUserId=" + adminUserId
                + ", adminEmail=" + adminEmail
                + ", adminPassword=<redacted>]";
    }
}
