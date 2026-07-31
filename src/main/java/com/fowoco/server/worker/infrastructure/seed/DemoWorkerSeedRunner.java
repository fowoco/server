package com.fowoco.server.worker.infrastructure.seed;

import com.fowoco.server.auth.infrastructure.seed.DemoAuthSeedProperties;
import com.fowoco.server.company.application.port.CompanyRepository;
import com.fowoco.server.company.domain.Company;
import com.fowoco.server.worker.application.port.WorkerRepository;
import com.fowoco.server.worker.domain.Worker;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.annotation.Transactional;

@Order(1)
class DemoWorkerSeedRunner implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(DemoWorkerSeedRunner.class);
    private static final List<DemoWorker> DEMO_WORKERS = List.of(
            demoWorker("92000000-0000-0000-0000-000000000001", "데모 근로자 01", "VN", "vi", 30, 180),
            demoWorker("92000000-0000-0000-0000-000000000002", "데모 근로자 02", "KH", "km", 60, 210),
            demoWorker("92000000-0000-0000-0000-000000000003", "데모 근로자 03", "NP", "ne", 90, 240),
            demoWorker("92000000-0000-0000-0000-000000000004", "데모 근로자 04", "ID", "id", 120, 270),
            demoWorker("92000000-0000-0000-0000-000000000005", "데모 근로자 05", "PH", "en", 180, 365)
    );
    private static final List<DemoWorker> TEST_WORKERS = List.of(
            demoWorker("93000000-0000-0000-0000-000000000001", "테스트 근로자 01", "TH", "th", 45, 190),
            demoWorker("93000000-0000-0000-0000-000000000002", "테스트 근로자 02", "MN", "mn", 75, 220),
            demoWorker("93000000-0000-0000-0000-000000000003", "테스트 근로자 03", "BD", "bn", 105, 250),
            demoWorker("93000000-0000-0000-0000-000000000004", "테스트 근로자 04", "UZ", "uz", 135, 280),
            demoWorker("93000000-0000-0000-0000-000000000005", "테스트 근로자 05", "LK", "si", 195, 370)
    );

    private final DemoAuthSeedProperties properties;
    private final CompanyRepository companyRepository;
    private final WorkerRepository workerRepository;
    private final Clock clock;

    DemoWorkerSeedRunner(
            DemoAuthSeedProperties properties,
            CompanyRepository companyRepository,
            WorkerRepository workerRepository,
            Clock clock
    ) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.companyRepository = Objects.requireNonNull(
                companyRepository,
                "companyRepository must not be null"
        );
        this.workerRepository = Objects.requireNonNull(workerRepository, "workerRepository must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        Instant now = clock.instant();
        LocalDate today = LocalDate.now(clock);
        seedCompanyWorkers(properties.companyId(), DEMO_WORKERS, today, now);
        seedCompanyWorkers(properties.testCompanyId(), TEST_WORKERS, today, now);
        LOGGER.info("demo_worker_seed ready company_count={} worker_count={}", 2, 10);
    }

    private void seedCompanyWorkers(
            UUID companyId,
            List<DemoWorker> workers,
            LocalDate today,
            Instant now
    ) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalStateException("demo worker seed company does not exist"));
        if (!company.isActive()) {
            throw new IllegalStateException("demo worker seed company is not active");
        }
        workers.forEach(worker -> seedWorker(companyId, worker, today, now));
    }

    private void seedWorker(UUID companyId, DemoWorker demoWorker, LocalDate today, Instant now) {
        Optional<Worker> existing =
                workerRepository.findByWorkerIdAndCompanyId(demoWorker.workerId(), companyId);
        if (existing.isPresent()) {
            verifyExistingWorker(existing.orElseThrow(), companyId, demoWorker);
            return;
        }
        workerRepository.insert(Worker.create(
                demoWorker.workerId(),
                companyId,
                demoWorker.displayName(),
                demoWorker.nationalityCode(),
                demoWorker.preferredLanguage(),
                today.plusDays(demoWorker.stayExpiryDays()),
                today.minusYears(1),
                today.plusDays(demoWorker.contractEndDays()),
                now
        ));
    }

    private void verifyExistingWorker(Worker worker, UUID companyId, DemoWorker demoWorker) {
        if (!demoWorker.workerId().equals(worker.workerId())
                || !companyId.equals(worker.companyId())
                || !demoWorker.displayName().equals(worker.displayName())
                || !demoWorker.nationalityCode().equals(worker.nationalityCode())
                || !demoWorker.preferredLanguage().equals(worker.preferredLanguage())
                || !worker.isCurrentlyEmployed()) {
            throw new IllegalStateException(
                    "a reserved demo worker id already belongs to different or inactive worker data"
            );
        }
    }

    private static DemoWorker demoWorker(
            String workerId,
            String displayName,
            String nationalityCode,
            String preferredLanguage,
            int stayExpiryDays,
            int contractEndDays
    ) {
        return new DemoWorker(
                UUID.fromString(workerId),
                displayName,
                nationalityCode,
                preferredLanguage,
                stayExpiryDays,
                contractEndDays
        );
    }

    private record DemoWorker(
            UUID workerId,
            String displayName,
            String nationalityCode,
            String preferredLanguage,
            int stayExpiryDays,
            int contractEndDays
    ) {
    }
}
