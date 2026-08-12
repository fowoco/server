package com.fowoco.server.notification.application;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fowoco.server.company.application.port.CompanyRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DueSoonNotificationSchedulerTest {

    private static final UUID COMPANY_A = UUID.fromString("50000000-0000-0000-0000-000000000001");
    private static final UUID COMPANY_B = UUID.fromString("50000000-0000-0000-0000-000000000002");
    // KST 기준 2026-08-10 정오 (경계값 문제를 피하기 위해 자정 근처는 피함)
    private static final Instant NOW = Instant.parse("2026-08-10T03:00:00Z");

    private final CompanyRepository companyRepository = mock(CompanyRepository.class);
    private final DueSoonCompanyNotifier companyNotifier = mock(DueSoonCompanyNotifier.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private final DueSoonNotificationScheduler scheduler = new DueSoonNotificationScheduler(
            companyRepository, companyNotifier, clock
    );

    @Test
    void processesEachCompanySeparately() {
        when(companyRepository.findAllIds()).thenReturn(List.of(COMPANY_A, COMPANY_B));

        scheduler.notifyDueSoonTasks();

        verify(companyNotifier, times(1)).processCompany(
                org.mockito.ArgumentMatchers.eq(COMPANY_A),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()
        );
        verify(companyNotifier, times(1)).processCompany(
                org.mockito.ArgumentMatchers.eq(COMPANY_B),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void usesKoreaDateNotUtcDateForWindow() {
        when(companyRepository.findAllIds()).thenReturn(List.of(COMPANY_A));
        ArgumentCaptor<LocalDate> fromCaptor = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> toCaptor = ArgumentCaptor.forClass(LocalDate.class);

        scheduler.notifyDueSoonTasks();

        verify(companyNotifier).processCompany(
                org.mockito.ArgumentMatchers.eq(COMPANY_A), fromCaptor.capture(), toCaptor.capture()
        );
        // NOW = 2026-08-10T03:00:00Z = KST 2026-08-10 정오이므로,
        // from은 오늘(8/10), to는 7일 후(8/17)여야 한다.
        org.assertj.core.api.Assertions.assertThat(fromCaptor.getValue()).isEqualTo(LocalDate.of(2026, 8, 10));
        org.assertj.core.api.Assertions.assertThat(toCaptor.getValue()).isEqualTo(LocalDate.of(2026, 8, 17));
    }
}
