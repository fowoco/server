package com.fowoco.server.auth.application.port;

import com.fowoco.server.auth.domain.UserAccount;
import java.util.Optional;

public interface UserAccountRepository {

    Optional<UserAccount> findByNormalizedEmail(String normalizedEmail);
}
