package com.fowoco.server.auth.application.port;

import com.fowoco.server.auth.domain.UserAccount;
import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepository {

    Optional<UserAccount> findByNormalizedEmail(String normalizedEmail);

    Optional<UserAccount> findByUserIdAndCompanyId(UUID userId, UUID companyId);
}
