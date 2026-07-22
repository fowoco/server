package com.fowoco.server.company.application;

import java.util.Optional;
import java.util.UUID;

public interface CompanyAuthenticationReader {

    Optional<CompanyAuthenticationSnapshot> findByCompanyId(UUID companyId);
}
