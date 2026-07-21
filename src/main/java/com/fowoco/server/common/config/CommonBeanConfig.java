package com.fowoco.server.common.config;

import com.fowoco.server.common.id.UuidGenerator;
import java.time.Clock;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class CommonBeanConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public UuidGenerator uuidGenerator() {
        return UUID::randomUUID;
    }
}
