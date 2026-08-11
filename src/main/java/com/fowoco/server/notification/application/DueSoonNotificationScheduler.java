package com.fowoco.server.notification.application;

import com.fowoco.server.company.application.port.CompanyRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DueSoonNotificationScheduler {

    private static final int UPCOMING_DAYS = 7;

    private final CompanyRepository companyRepository;
    private final DueSoonCompanyNotifier companyNotifier;
    private final Clock clock;

    public DueSoonNotificationScheduler(
            CompanyRepository companyRepository,
            DueSoonCompanyNotifier companyNotifier,
            Clock clock
    ) {
        this.companyRepository = companyRepository;
        this.companyNotifier = companyNotifier;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Seoul")
    public void notifyDueSoonTasks() {
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of("Asia/Seoul")));
        LocalDate windowEnd = today.plusDays(UPCOMING_DAYS);

        companyRepository.findAllIds().forEach(companyId ->
                companyNotifier.processCompany(companyId, today, windowEnd)
        );
    }
}
