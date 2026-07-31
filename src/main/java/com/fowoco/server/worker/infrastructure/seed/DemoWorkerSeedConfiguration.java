package com.fowoco.server.worker.infrastructure.seed;

import com.fowoco.server.auth.infrastructure.seed.DemoAuthSeedProperties;
import com.fowoco.server.company.application.port.CompanyRepository;
import com.fowoco.server.worker.application.port.WorkerRepository;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class DemoWorkerSeedConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "app.demo-seed", name = "enabled", havingValue = "true")
    DemoWorkerSeedRunner demoWorkerSeedRunner(
            DemoAuthSeedProperties properties,
            CompanyRepository companyRepository,
            WorkerRepository workerRepository,
            Clock clock
    ) {
        return new DemoWorkerSeedRunner(
                properties,
                companyRepository,
                workerRepository,
                clock
        );
    }
}
