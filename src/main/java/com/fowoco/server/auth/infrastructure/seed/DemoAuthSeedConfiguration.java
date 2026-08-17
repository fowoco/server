package com.fowoco.server.auth.infrastructure.seed;

import com.fowoco.server.auth.application.port.UserAccountRepository;
import com.fowoco.server.company.application.port.CompanyRepository;
import com.fowoco.server.company.application.port.CompanySettingsProvisioner;
import com.fowoco.server.common.security.TenantTransactionExecutor;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DemoAuthSeedProperties.class)
public class DemoAuthSeedConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "app.demo-seed", name = "enabled", havingValue = "true")
    DemoAuthSeedRunner demoAuthSeedRunner(
            DemoAuthSeedProperties properties,
            CompanyRepository companyRepository,
            CompanySettingsProvisioner companySettingsProvisioner,
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder,
            Clock clock,
            TenantTransactionExecutor tenantTransactionExecutor
    ) {
        return new DemoAuthSeedRunner(
                properties,
                companyRepository,
                companySettingsProvisioner,
                userAccountRepository,
                passwordEncoder,
                clock,
                tenantTransactionExecutor
        );
    }
}
