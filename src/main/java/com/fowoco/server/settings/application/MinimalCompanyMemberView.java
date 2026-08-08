package com.fowoco.server.settings.application;

import java.util.UUID;

public record MinimalCompanyMemberView(
        UUID userId,
        String displayName
) implements CompanyMemberView {
}
