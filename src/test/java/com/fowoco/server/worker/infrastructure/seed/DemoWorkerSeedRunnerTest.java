package com.fowoco.server.worker.infrastructure.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.fowoco.server.auth.infrastructure.seed.DemoAuthSeedProperties;
import com.fowoco.server.company.application.port.CompanyRepository;
import com.fowoco.server.company.domain.Company;
import com.fowoco.server.worker.application.WorkerSearchQuery;
import com.fowoco.server.worker.application.port.WorkerRepository;
import com.fowoco.server.worker.domain.Worker;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class DemoWorkerSeedRunnerTest {

    private static final UUID DEMO_COMPANY_ID =
            UUID.fromString("90000000-0000-0000-0000-000000000001");
    private static final UUID TEST_COMPANY_ID =
            UUID.fromString("91000000-0000-0000-0000-000000000001");
    private static final UUID ADMIN_USER_ID =
            UUID.fromString("90000000-0000-0000-0000-000000000002");
    private static final Instant NOW = Instant.parse("2026-07-31T00:00:00Z");

    @Test
    void createsFiveIdempotentWorkersForEachDemoCompany() throws Exception {
        InMemoryCompanyRepository companyRepository = new InMemoryCompanyRepository();
        companyRepository.insert(Company.create(DEMO_COMPANY_ID, "FOWOCO Demo Company", NOW));
        companyRepository.insert(Company.create(TEST_COMPANY_ID, "FOWOCO Test Company", NOW));
        InMemoryWorkerRepository workerRepository = new InMemoryWorkerRepository();
        DemoWorkerSeedRunner runner = new DemoWorkerSeedRunner(
                properties(),
                companyRepository,
                workerRepository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        runner.run(new DefaultApplicationArguments(new String[0]));
        runner.run(new DefaultApplicationArguments(new String[0]));

        assertThat(workerRepository.workers.values())
                .filteredOn(worker -> worker.companyId().equals(DEMO_COMPANY_ID))
                .hasSize(5);
        assertThat(workerRepository.workers.values())
                .filteredOn(worker -> worker.companyId().equals(TEST_COMPANY_ID))
                .hasSize(5);
        assertThat(workerRepository.workers.values())
                .allMatch(Worker::isCurrentlyEmployed);
        assertThat(workerRepository.workers.get(
                UUID.fromString("92000000-0000-0000-0000-000000000001")
        ).stayExpiryDate()).isEqualTo(LocalDate.of(2026, 8, 30));
    }

    private DemoAuthSeedProperties properties() {
        return new DemoAuthSeedProperties(
                true,
                DEMO_COMPANY_ID,
                "FOWOCO Demo Company",
                TEST_COMPANY_ID,
                "FOWOCO Test Company",
                ADMIN_USER_ID,
                "데모 관리자",
                "demo.admin@example.com",
                "Demo-password-1!"
        );
    }

    private static final class InMemoryCompanyRepository implements CompanyRepository {

        private final Map<UUID, Company> companies = new LinkedHashMap<>();

        @Override
        public Optional<Company> findById(UUID companyId) {
            return Optional.ofNullable(companies.get(companyId));
        }

        @Override
        public void insert(Company company) {
            if (companies.putIfAbsent(company.companyId(), company) != null) {
                throw new IllegalStateException("duplicate company");
            }
        }
    }

    private static final class InMemoryWorkerRepository implements WorkerRepository {

        private final Map<UUID, Worker> workers = new LinkedHashMap<>();

        @Override
        public void insert(Worker worker) {
            if (workers.putIfAbsent(worker.workerId(), worker) != null) {
                throw new IllegalStateException("duplicate worker");
            }
        }

        @Override
        public Optional<Worker> findByWorkerIdAndCompanyId(UUID workerId, UUID companyId) {
            return Optional.ofNullable(workers.get(workerId))
                    .filter(worker -> worker.companyId().equals(companyId));
        }

        @Override
        public Worker update(Worker worker) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Worker> findPage(UUID companyId, WorkerSearchQuery query) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long countPage(UUID companyId, WorkerSearchQuery query) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Worker> findAllByWorkerIdsAndCompanyId(Set<UUID> workerIds, UUID companyId) {
            throw new UnsupportedOperationException();
        }
    }
}
