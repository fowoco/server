package com.fowoco.server.auth.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth.agreements")
public final class AgreementPolicyProperties {

    private final String serviceTermsVersion;
    private final String privacyPolicyVersion;
    private final String marketingVersion;

    public AgreementPolicyProperties(
            String serviceTermsVersion,
            String privacyPolicyVersion,
            String marketingVersion
    ) {
        this.serviceTermsVersion = requireVersion(serviceTermsVersion, "serviceTermsVersion");
        this.privacyPolicyVersion = requireVersion(privacyPolicyVersion, "privacyPolicyVersion");
        this.marketingVersion = requireVersion(marketingVersion, "marketingVersion");
    }

    public String serviceTermsVersion() {
        return serviceTermsVersion;
    }

    public String privacyPolicyVersion() {
        return privacyPolicyVersion;
    }

    public String marketingVersion() {
        return marketingVersion;
    }

    private static String requireVersion(String value, String name) {
        if (value == null || value.isBlank() || value.strip().length() > 40) {
            throw new IllegalArgumentException(name + " must be 1 to 40 characters");
        }
        return value.strip();
    }
}
