package com.fowoco.server.auth.application.port;

import com.fowoco.server.auth.domain.UserAgreementConsent;
import java.util.List;

public interface UserAgreementConsentRepository {

    void insertAll(List<UserAgreementConsent> consents);
}
