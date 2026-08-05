package com.fowoco.server.worker.infrastructure.seed;

import com.fowoco.server.auth.infrastructure.seed.DemoAuthSeedProperties;
import com.fowoco.server.company.application.port.CompanyRepository;
import com.fowoco.server.company.domain.Company;
import com.fowoco.server.worker.application.port.WorkerRepository;
import com.fowoco.server.worker.domain.Worker;
import com.fowoco.server.worker.infrastructure.seed.DemoWorkerSeedCatalog.WorkerSeed;
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
    private final DemoAuthSeedProperties properties;
    private final CompanyRepository companyRepository;
    private final WorkerRepository workerRepository;
    private final Clock clock;
    private final DemoWorkerSeedCatalog catalog;

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
        this.catalog = new DemoWorkerSeedCatalog();
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        Instant now = clock.instant();
        LocalDate today = LocalDate.now(clock);
        seedCompanyWorkers(properties.companyId(), catalog.demoWorkers(), today, now);
        seedCompanyWorkers(properties.testCompanyId(), catalog.testWorkers(), today, now);
        LOGGER.info(
                "demo_worker_seed ready company_count={} worker_count={}",
                2,
                catalog.demoWorkers().size() + catalog.testWorkers().size()
        );
    }

    private void seedCompanyWorkers(
            UUID companyId,
            List<WorkerSeed> workers,
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

    private void seedWorker(UUID companyId, WorkerSeed workerSeed, LocalDate today, Instant now) {
        Optional<Worker> existing =
                workerRepository.findByWorkerIdAndCompanyId(workerSeed.workerId(), companyId);
        if (existing.isPresent()) {
            verifyExistingWorker(existing.get(), companyId, workerSeed);
            return;
        }
        Worker worker = new Worker(
                workerSeed.workerId(),
                companyId,
                workerSeed.displayName(),
                workerSeed.nationalityCode(),
                workerSeed.preferredLanguage(),
                workerSeed.workStatus(),
                relativeDate(today, workerSeed.stayExpiryDays()),
                today.minusYears(1),
                today.plusDays(workerSeed.contractEndDays()),
                now,
                now,
                0L
        );
        workerRepository.insert(worker);
    }

    private void verifyExistingWorker(Worker worker, UUID companyId, WorkerSeed workerSeed) {
        if (!workerSeed.workerId().equals(worker.workerId())
                || !companyId.equals(worker.companyId())
                || !workerSeed.displayName().equals(worker.displayName())
                || !workerSeed.nationalityCode().equals(worker.nationalityCode())
                || !workerSeed.preferredLanguage().equals(worker.preferredLanguage())
                || workerSeed.workStatus() != worker.workStatus()) {
            throw new IllegalStateException(
                    "a reserved demo worker id already belongs to different worker data"
            );
        }
    }

    private LocalDate relativeDate(LocalDate today, Integer days) {
        return days == null ? null : today.plusDays(days);
    }
}
