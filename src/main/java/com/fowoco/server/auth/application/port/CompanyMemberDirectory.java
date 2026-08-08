package com.fowoco.server.auth.application.port;

import com.fowoco.server.auth.application.CompanyMemberAccount;
import com.fowoco.server.auth.domain.UserRole;
import java.util.List;
import java.util.UUID;

public interface CompanyMemberDirectory {

    List<CompanyMemberAccount> findByCompanyId(
            UUID companyId,
            UserRole role,
            boolean activeOnly
    );
}
