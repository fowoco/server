package com.fowoco.server.worker.infrastructure.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.fowoco.server.auth.infrastructure.seed.DemoAuthSeedProperties;
import com.fowoco.server.company.application.port.CompanyRepository;
import com.fowoco.server.company.domain.Company;
import com.fowoco.server.worker.application.WorkerSearchQuery;
import com.fowoco.server.worker.application.port.WorkerRepository;
import com.fowoco.server.worker.domain.Worker;
import com.fowoco.server.worker.domain.WorkerStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class DemoWorkerSeedRunnerTest {

    private static final Set<String> AI_SUPPORTED_LANGUAGES = Set.of(
            "en", "zh-Hans", "vi", "th", "fil", "id", "mn", "si",
            "ru", "uz", "ky", "bn", "ur", "km", "tet"
    );
    private static final Map<String, Set<String>> NATURAL_NATIONALITIES_BY_LANGUAGE = Map.ofEntries(
            Map.entry("en", Set.of("MM", "NP", "PH")),
            Map.entry("zh-Hans", Set.of("CN")),
            Map.entry("vi", Set.of("VN")),
            Map.entry("th", Set.of("TH")),
            Map.entry("fil", Set.of("PH")),
            Map.entry("id", Set.of("ID")),
            Map.entry("mn", Set.of("MN")),
            Map.entry("si", Set.of("LK")),
            Map.entry("ru", Set.of("RU")),
            Map.entry("uz", Set.of("UZ")),
            Map.entry("ky", Set.of("KG")),
            Map.entry("bn", Set.of("BD")),
            Map.entry("ur", Set.of("PK")),
            Map.entry("km", Set.of("KH")),
            Map.entry("tet", Set.of("TL"))
    );

    private static final UUID DEMO_COMPANY_ID =
            UUID.fromString("90000000-0000-0000-0000-000000000001");
    private static final UUID TEST_COMPANY_ID =
            UUID.fromString("91000000-0000-0000-0000-000000000001");
    private static final UUID ADMIN_USER_ID =
            UUID.fromString("90000000-0000-0000-0000-000000000002");
    private static final Instant NOW = Instant.parse("2026-07-31T00:00:00Z");

    @Test
    void createsOperationalDemoWorkersAndKeepsTestCompanySmall() throws Exception {
        InMemoryCompanyRepository companyRepository = new InMemoryCompanyRepository();
        companyRepository.insert(Company.create(DEMO_COMPANY_ID, "FOWOCO Demo Company", NOW));
        companyRepository.insert(Company.create(TEST_COMPANY_ID, "FOWOCO Test Company", NOW));
        InMemoryWorkerRepository workerRepository = new InMemoryWorkerRepository();
        MutableClock clock = new MutableClock(NOW, ZoneOffset.UTC);
        DemoWorkerSeedRunner runner = new DemoWorkerSeedRunner(
                properties(),
                companyRepository,
                workerRepository,
                clock
        );

        runner.run(new DefaultApplicationArguments(new String[0]));
        Map<UUID, Worker> initialWorkers = Map.copyOf(workerRepository.workers);
        runner.run(new DefaultApplicationArguments(new String[0]));
        clock.advance(Duration.ofDays(1));
        runner.run(new DefaultApplicationArguments(new String[0]));

        assertThat(workerRepository.workers).containsExactlyInAnyOrderEntriesOf(initialWorkers);

        assertThat(workerRepository.workers.values())
                .filteredOn(worker -> worker.companyId().equals(DEMO_COMPANY_ID))
                .hasSize(28);
        List<Worker> demoWorkers = workerRepository.workers.values().stream()
                .filter(worker -> worker.companyId().equals(DEMO_COMPANY_ID))
                .toList();
        assertThat(demoWorkers.stream().map(Worker::preferredLanguage).collect(java.util.stream.Collectors.toSet()))
                .containsExactlyInAnyOrderElementsOf(AI_SUPPORTED_LANGUAGES);
        assertThat(demoWorkers).allMatch(worker -> NATURAL_NATIONALITIES_BY_LANGUAGE
                .getOrDefault(worker.preferredLanguage(), Set.of())
                .contains(worker.nationalityCode()));
        assertThat(workerRepository.workers.values())
                .filteredOn(worker -> worker.companyId().equals(TEST_COMPANY_ID))
                .hasSize(5);
        assertThat(workerRepository.workers.values())
                .allMatch(Worker::isCurrentlyEmployed);
        assertThat(workerRepository.workers.get(
                UUID.fromString("92000000-0000-0000-0000-000000000001")
        ).stayExpiryDate()).isEqualTo(LocalDate.of(2026, 8, 30));
        assertThat(workerRepository.workers.get(
                UUID.fromString("92000000-0000-0000-0000-000000000006")
        )).satisfies(worker -> {
            assertThat(worker.displayName()).isEqualTo("응웬반A");
            assertThat(worker.nationalityCode()).isEqualTo("VN");
            assertThat(worker.preferredLanguage()).isEqualTo("vi");
            assertThat(worker.stayExpiryDate()).isEqualTo(LocalDate.of(2026, 9, 14));
        });
        assertThat(workerRepository.workers.values())
                .filteredOn(worker -> worker.companyId().equals(DEMO_COMPANY_ID))
                .filteredOn(worker -> worker.workStatus() == WorkerStatus.ON_LEAVE)
                .hasSize(3);
        assertThat(workerRepository.workers.get(
                UUID.fromString("92000000-0000-0000-0000-000000000025")
        ).stayExpiryDate()).isNull();
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
            throw new AssertionError("immutable demo worker snapshot must not be updated");
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

    private static final class MutableClock extends Clock {

        private final AtomicReference<Instant> instant;
        private final ZoneId zone;

        private MutableClock(Instant instant, ZoneId zone) {
            this.instant = new AtomicReference<>(instant);
            this.zone = zone;
        }

        void advance(Duration duration) {
            instant.updateAndGet(value -> value.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(instant(), zone);
        }

        @Override
        public Instant instant() {
            return instant.get();
        }
    }
}
