package com.fowoco.server.stayverification.infrastructure.scheduling;

import com.fowoco.server.stayverification.application.StayVerificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "app.stay-verification.scheduler-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class StayVerificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(StayVerificationScheduler.class);
    private final StayVerificationService service;

    public StayVerificationScheduler(StayVerificationService service) {
        this.service = service;
    }

    @Scheduled(cron = "${app.stay-verification.scan-cron:0 10 2 * * *}", zone = "Asia/Seoul")
    public void scanExpiredStayDates() {
        int created = service.scanAllCompanies();
        if (created > 0) {
            log.info("stay verification daily scan created {} case(s)", created);
        }
    }
}
