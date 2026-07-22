package com.fowoco.server.company.application.port;

import com.fowoco.server.company.domain.Company;
import java.util.Optional;
import java.util.UUID;

public interface CompanyRepository {

    Optional<Company> findById(UUID companyId);

    void insert(Company company);
}
