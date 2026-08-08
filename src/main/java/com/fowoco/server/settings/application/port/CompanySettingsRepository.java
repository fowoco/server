package com.fowoco.server.settings.application.port;

import com.fowoco.server.settings.domain.CompanySettings;
import java.util.Optional;
import java.util.UUID;

public interface CompanySettingsRepository {

    Optional<CompanySettings> findByCompanyId(UUID companyId);

    void insert(CompanySettings companySettings);

    void update(CompanySettings companySettings);
}
