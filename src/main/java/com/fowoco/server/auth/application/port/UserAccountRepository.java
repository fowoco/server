package com.fowoco.server.auth.application.port;

import com.fowoco.server.auth.domain.UserAccount;
import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepository {

    void insert(UserAccount userAccount);

    void update(UserAccount userAccount);

    boolean existsByNormalizedEmail(String normalizedEmail);

    Optional<UserAccount> findByNormalizedEmail(String normalizedEmail);

    Optional<UserAccount> findByNormalizedEmailWithLock(String normalizedEmail);

    Optional<UserAccount> findByUserIdAndCompanyId(UUID userId, UUID companyId);
}
