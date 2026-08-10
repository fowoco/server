package com.fowoco.server.settings.application;

import com.fowoco.server.auth.domain.UserRole;
import java.util.List;
import java.util.UUID;

public record DetailedCompanyMemberView(
        UUID userId,
        String displayName,
        List<UserRole> roles,
        boolean active,
        boolean approvalPermission
) implements CompanyMemberView {

    public DetailedCompanyMemberView {
        roles = List.copyOf(roles);
    }
}
