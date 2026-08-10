package com.fowoco.server.workerlink.infrastructure.sms;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(WorkerLinkDeliveryProperties.class)
public class WorkerLinkSmsConfiguration {
}
