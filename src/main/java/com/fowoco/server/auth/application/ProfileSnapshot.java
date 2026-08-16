package com.fowoco.server.auth.application;

import com.fowoco.server.auth.domain.UserAccount;
import java.time.Instant;

/**
 * {@link UserAccount} plus login-history facts the auth module tracks separately
 * (see {@link com.fowoco.server.auth.application.port.UserLoginEventRepository}).
 * Both fields are null only if the account has never completed a login, which cannot happen for
 * an authenticated caller (a Bearer token can only be issued by a successful login).
 */
public record ProfileSnapshot(
        UserAccount account,
        Instant lastLoginAt,
        String lastLoginDevice,
        int recentDeviceCount
) {
}
