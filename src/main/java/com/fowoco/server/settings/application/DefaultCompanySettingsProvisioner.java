package com.fowoco.server.settings.application;

import com.fowoco.server.company.application.port.CompanySettingsProvisioner;
import com.fowoco.server.settings.application.port.CompanySettingsRepository;
import com.fowoco.server.settings.domain.CompanySettings;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DefaultCompanySettingsProvisioner implements CompanySettingsProvisioner {

    private final CompanySettingsRepository companySettingsRepository;

    public DefaultCompanySettingsProvisioner(CompanySettingsRepository companySettingsRepository) {
        this.companySettingsRepository = companySettingsRepository;
    }

    @Override
    public void provisionDefaults(UUID companyId, Instant now) {
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(now, "now must not be null");
        companySettingsRepository.insert(CompanySettings.defaults(companyId, now));
    }
}
