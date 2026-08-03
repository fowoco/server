package com.fowoco.server.auth.infrastructure.seed;

import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.demo-seed")
public record DemoAuthSeedProperties(
        boolean enabled,
        UUID companyId,
        String companyName,
        UUID testCompanyId,
        String testCompanyName,
        UUID adminUserId,
        String adminDisplayName,
        String adminEmail,
        String adminPassword
) {

    @Override
    public String toString() {
        return "DemoAuthSeedProperties[enabled=" + enabled
                + ", companyId=" + companyId
                + ", companyName=" + companyName
                + ", testCompanyId=" + testCompanyId
                + ", testCompanyName=" + testCompanyName
                + ", adminUserId=" + adminUserId
                + ", adminDisplayName=" + adminDisplayName
                + ", adminEmail=" + adminEmail
                + ", adminPassword=<redacted>]";
    }
}
