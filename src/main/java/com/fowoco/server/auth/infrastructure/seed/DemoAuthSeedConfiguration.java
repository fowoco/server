package com.fowoco.server.auth.infrastructure.seed;

import com.fowoco.server.auth.application.port.UserAccountRepository;
import com.fowoco.server.company.application.port.CompanyRepository;
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
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder,
            Clock clock
    ) {
        return new DemoAuthSeedRunner(
                properties,
                companyRepository,
                userAccountRepository,
                passwordEncoder,
                clock
        );
    }
}
