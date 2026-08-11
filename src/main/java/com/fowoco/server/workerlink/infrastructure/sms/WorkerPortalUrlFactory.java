package com.fowoco.server.workerlink.infrastructure.sms;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

@Component
public final class WorkerPortalUrlFactory {

    private final String portalBaseUrl;

    public WorkerPortalUrlFactory(WorkerLinkDeliveryProperties properties) {
        String normalized = properties.portalBaseUrl().toString();
        this.portalBaseUrl = normalized.endsWith("/")
                ? normalized.substring(0, normalized.length() - 1)
                : normalized;
    }

    public URI create(String rawToken) {
        String token = WorkerLinkDeliveryProperties.requireText(rawToken, "rawToken");
        return URI.create(portalBaseUrl + "/worker-portal/"
                + URLEncoder.encode(token, StandardCharsets.UTF_8));
    }
}
