package com.fowoco.server.workerlink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fowoco.server.auth.application.ActorContext;
import com.fowoco.server.auth.domain.UserRole;
import com.fowoco.server.common.web.RequestMetadata;
import com.fowoco.server.file.application.FileCreateCommand;
import com.fowoco.server.file.application.FileService;
import com.fowoco.server.file.domain.StoredFile;
import com.fowoco.server.workerlink.application.port.WorkerLinkRepository;
import com.fowoco.server.workerlink.domain.WorkerLink;
import com.fowoco.server.workerlink.infrastructure.persistence.JpaWorkerLinkRepository;
import com.fowoco.server.workerlink.infrastructure.security.WorkerLinkHasher;
import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

@EnabledIfEnvironmentVariable(named = "POSTGRES_TEST_URL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "POSTGRES_TEST_USERNAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "POSTGRES_TEST_PASSWORD", matches = ".+")
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(WorkerLinkDocumentPostgreSqlIntegrationTest.ConcurrencyConfiguration.class)
class WorkerLinkDocumentPostgreSqlIntegrationTest {

    private static final UUID COMPANY_ID = UUID.fromString("17200000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("17200000-0000-0000-0000-000000000002");
    private static final UUID WORKER_ID = UUID.fromString("17200000-0000-0000-0000-000000000003");
    private static final UUID CASE_ID = UUID.fromString("17200000-0000-0000-0000-000000000004");
    private static final UUID TASK_ID = UUID.fromString("17200000-0000-0000-0000-000000000005");
    private static final UUID WORKER_LINK_ID = UUID.fromString("17200000-0000-0000-0000-000000000006");
    private static final String RAW_WORKER_LINK_TOKEN = "worker-link-postgresql-concurrency-token";
    private static final String BOUNDARY = "FowocoPostgreSqlUploadBoundary172";
    private static final int CONCURRENCY_ATTEMPTS = 5;
    private static final Path STORAGE_ROOT = Path.of(
            "build", "test-file-storage", "worker-link-postgresql-" + UUID.randomUUID()
    ).toAbsolutePath().normalize();

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private WorkerLinkHasher workerLinkHasher;

    @Autowired
    private CoordinatedWorkerLinkRepository coordinatedWorkerLinkRepository;

    @Autowired
    private FileService fileService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @DynamicPropertySource
    static void usePostgreSqlAndIsolatedFileStorage(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> requiredEnvironmentVariable("POSTGRES_TEST_URL"));
        registry.add("spring.datasource.username", () -> requiredEnvironmentVariable("POSTGRES_TEST_USERNAME"));
        registry.add("spring.datasource.password", () -> requiredEnvironmentVariable("POSTGRES_TEST_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add(
                "spring.flyway.locations",
                () -> "classpath:db/migration,classpath:db/migration-postgresql"
        );
        registry.add("app.file-storage.local-path", STORAGE_ROOT::toString);
    }

    @BeforeEach
    void seedWorkerLink() throws IOException {
        assertPostgreSql16();
        clearDatabaseRows();
        clearStorageDirectory();

        jdbcTemplate.update(
                """
                INSERT INTO company (company_id, name, status, created_at, updated_at, version)
                VALUES (?, '파일 보상 동시성 테스트 사업장', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """,
                COMPANY_ID
        );
        jdbcTemplate.update(
                """
                INSERT INTO user_account (
                    user_id, company_id, email, normalized_email, password_hash,
                    role, status, created_at, updated_at, version
                ) VALUES (
                    ?, ?, 'file.rollback.172@example.com', 'file.rollback.172@example.com',
                    'unused-test-password-hash', 'HR', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
                )
                """,
                USER_ID,
                COMPANY_ID
        );
        jdbcTemplate.update(
                """
                INSERT INTO worker (
                    worker_id, company_id, display_name, work_status, created_at, updated_at, version
                ) VALUES (?, ?, 'PostgreSQL 동시성 테스트 근로자', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """,
                WORKER_ID,
                COMPANY_ID
        );
        jdbcTemplate.update(
                """
                INSERT INTO workflow_case (
                    case_id, company_id, worker_id, title, lifecycle_status,
                    priority, workflow_catalog_version, workflow_snapshot_json,
                    created_by, created_at, updated_at, version
                ) VALUES (
                    ?, ?, ?, 'Worker Link 문서 제출', 'ACTIVE', 'NORMAL',
                    '2026.07', '{}', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
                )
                """,
                CASE_ID,
                COMPANY_ID,
                WORKER_ID,
                USER_ID
        );
        jdbcTemplate.update(
                """
                INSERT INTO task (
                    task_id, company_id, worker_id, case_id, task_type,
                    workflow_id, workflow_catalog_version, title, business_data_json,
                    critical_fingerprint, content_revision, source, status,
                    created_by, updated_by, created_at, updated_at, version
                ) VALUES (
                    ?, ?, ?, ?, 'RECONTRACT', 'WF-CON-001', '2026.07',
                    'Worker Link 문서 제출', '{}', ?, 0, 'MANUAL', 'APPROVED',
                    ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
                )
                """,
                TASK_ID,
                COMPANY_ID,
                WORKER_ID,
                CASE_ID,
                "f".repeat(64),
                USER_ID,
                USER_ID
        );
        jdbcTemplate.update(
                """
                INSERT INTO worker_link (
                    worker_link_id, task_id, company_id, token_hash, expires_at,
                    status, conversation_status, issued_by, idempotency_key,
                    created_at, updated_at, version
                ) VALUES (
                    ?, ?, ?, ?, CURRENT_TIMESTAMP + INTERVAL '1 day', 'ACTIVE',
                    'WAITING_WORKER', ?, 'postgresql-concurrency-link',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
                )
                """,
                WORKER_LINK_ID,
                TASK_ID,
                COMPANY_ID,
                workerLinkHasher.hash(RAW_WORKER_LINK_TOKEN),
                USER_ID
        );
    }

    @AfterEach
    void cleanUp() throws IOException {
        clearDatabaseRows();
        clearStorageDirectory();
    }

    @Test
    void concurrentSameKeyUploadsConvergeOnOnePostgreSqlRowAndOneLocalFile() throws Exception {
        String lastIdempotencyKey = null;
        byte[] lastContent = null;

        for (int attempt = 1; attempt <= CONCURRENCY_ATTEMPTS; attempt++) {
            String idempotencyKey = "postgresql-upload-key-" + attempt;
            byte[] content = ("concurrent-upload-content-" + attempt).getBytes(StandardCharsets.UTF_8);

            coordinatedWorkerLinkRepository.coordinateNextTwoTokenLookups();
            List<HttpResponse<String>> responses = concurrentlyUpload(
                    idempotencyKey,
                    "passport-" + attempt + ".pdf",
                    content
            );

            assertThat(responses).extracting(HttpResponse::statusCode).containsExactly(201, 201);
            String firstUploadId = JsonPath.read(responses.get(0).body(), "$.upload_id");
            String secondUploadId = JsonPath.read(responses.get(1).body(), "$.upload_id");
            assertThat(secondUploadId).isEqualTo(firstUploadId);

            assertThat(idempotencyRowCount()).isEqualTo(attempt);
            assertThat(storedFileRowCount()).isEqualTo(attempt);
            assertThat(finalFileCount()).isEqualTo(attempt);
            assertThat(temporaryFileCount()).isZero();

            String storageKey = jdbcTemplate.queryForObject(
                    "SELECT storage_key FROM stored_file WHERE stored_file_id = ?",
                    String.class,
                    UUID.fromString(firstUploadId)
            );
            assertThat(Files.readAllBytes(STORAGE_ROOT.resolve(storageKey))).isEqualTo(content);

            lastIdempotencyKey = idempotencyKey;
            lastContent = content;
        }

        HttpResponse<String> conflict = upload(
                lastIdempotencyKey,
                "passport-" + CONCURRENCY_ATTEMPTS + ".pdf",
                differentBytesWithSameLength(lastContent)
        );

        assertThat(conflict.statusCode()).as(conflict.body()).isEqualTo(409);
        assertThat(JsonPath.<String>read(conflict.body(), "$.code")).isEqualTo("IDEMPOTENCY_CONFLICT");
        assertThat(idempotencyRowCount()).isEqualTo(CONCURRENCY_ATTEMPTS);
        assertThat(storedFileRowCount()).isEqualTo(CONCURRENCY_ATTEMPTS);
        assertThat(finalFileCount()).isEqualTo(CONCURRENCY_ATTEMPTS);
        assertThat(temporaryFileCount()).isZero();
    }

    @Test
    void transactionRollbackDeletesTheActualLocalFileAndDatabaseRows() {
        byte[] content = "rollback-file-content".getBytes(StandardCharsets.UTF_8);
        AtomicReference<StoredFile> uploadedFile = new AtomicReference<>();
        ActorContext actor = new ActorContext(USER_ID, COMPANY_ID, Set.of(UserRole.HR));

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            StoredFile storedFile = fileService.upload(
                    new FileCreateCommand(
                            COMPANY_ID,
                            "rollback.pdf",
                            "application/pdf",
                            content.length,
                            "ROLLBACK_INTEGRATION_TEST",
                            null,
                            null,
                            new ByteArrayInputStream(content)
                    ),
                    actor,
                    new RequestMetadata("file-storage-rollback-postgresql", null)
            );
            uploadedFile.set(storedFile);
            throw new ForcedRollbackException();
        })).isInstanceOf(ForcedRollbackException.class);

        StoredFile rolledBackFile = uploadedFile.get();
        assertThat(rolledBackFile).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM stored_file WHERE stored_file_id = ?",
                Integer.class,
                rolledBackFile.storedFileId()
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE request_id = 'file-storage-rollback-postgresql'",
                Integer.class
        )).isZero();
        assertThat(STORAGE_ROOT.resolve(rolledBackFile.storageKey())).doesNotExist();
        assertThat(finalFileCount()).isZero();
        assertThat(temporaryFileCount()).isZero();
    }

    private List<HttpResponse<String>> concurrentlyUpload(
            String idempotencyKey,
            String filename,
            byte[] content
    ) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch workersReady = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<HttpResponse<String>> first = executor.submit(
                    () -> uploadAfterSignal(idempotencyKey, filename, content, workersReady, start)
            );
            Future<HttpResponse<String>> second = executor.submit(
                    () -> uploadAfterSignal(idempotencyKey, filename, content, workersReady, start)
            );

            assertThat(workersReady.await(5, TimeUnit.SECONDS))
                    .as("both uploads must be ready before they are released")
                    .isTrue();
            start.countDown();
            return List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private HttpResponse<String> uploadAfterSignal(
            String idempotencyKey,
            String filename,
            byte[] content,
            CountDownLatch workersReady,
            CountDownLatch start
    ) throws Exception {
        workersReady.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("upload concurrency start signal timed out");
        }
        return upload(idempotencyKey, filename, content);
    }

    private HttpResponse<String> upload(String idempotencyKey, String filename, byte[] content) throws Exception {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + BOUNDARY + "\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(("Content-Disposition: form-data; name=\"file\"; filename=\""
                + filename + "\"\r\n").getBytes(StandardCharsets.UTF_8));
        body.write("Content-Type: application/pdf\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        body.write(content);
        body.write("\r\n".getBytes(StandardCharsets.UTF_8));
        body.write(("--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port
                                + "/api/v1/public/worker-links/" + RAW_WORKER_LINK_TOKEN + "/documents")
                )
                .header(HttpHeaders.CONTENT_TYPE, "multipart/form-data; boundary=" + BOUNDARY)
                .header("Idempotency-Key", idempotencyKey)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private int idempotencyRowCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM worker_document_upload_idempotency WHERE worker_link_id = ?",
                Integer.class,
                WORKER_LINK_ID
        );
    }

    private int storedFileRowCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM stored_file WHERE company_id = ?",
                Integer.class,
                COMPANY_ID
        );
    }

    private long finalFileCount() {
        return storagePaths().stream()
                .filter(path -> !path.getFileName().toString().startsWith(".fowoco-upload-"))
                .count();
    }

    private long temporaryFileCount() {
        return storagePaths().stream()
                .filter(path -> path.getFileName().toString().startsWith(".fowoco-upload-"))
                .count();
    }

    private List<Path> storagePaths() {
        try (var paths = Files.list(STORAGE_ROOT)) {
            return paths.toList();
        } catch (IOException exception) {
            throw new IllegalStateException("failed to inspect the integration-test storage directory", exception);
        }
    }

    private void clearStorageDirectory() throws IOException {
        Files.createDirectories(STORAGE_ROOT);
        try (var paths = Files.list(STORAGE_ROOT)) {
            for (Path path : paths.toList()) {
                if (!path.toAbsolutePath().normalize().startsWith(STORAGE_ROOT)) {
                    throw new IllegalStateException("test storage path escaped its isolated root");
                }
                Files.deleteIfExists(path);
            }
        }
    }

    private void clearDatabaseRows() {
        jdbcTemplate.update("DELETE FROM worker_document_upload_idempotency WHERE company_id = ?", COMPANY_ID);
        jdbcTemplate.update("DELETE FROM audit_event WHERE company_id = ?", COMPANY_ID);
        jdbcTemplate.update("DELETE FROM stored_file WHERE company_id = ?", COMPANY_ID);
        jdbcTemplate.update("DELETE FROM worker_link WHERE company_id = ?", COMPANY_ID);
        jdbcTemplate.update("DELETE FROM task WHERE company_id = ?", COMPANY_ID);
        jdbcTemplate.update("DELETE FROM workflow_case WHERE company_id = ?", COMPANY_ID);
        jdbcTemplate.update("DELETE FROM worker WHERE company_id = ?", COMPANY_ID);
        jdbcTemplate.update("DELETE FROM user_account WHERE company_id = ?", COMPANY_ID);
        jdbcTemplate.update("DELETE FROM company WHERE company_id = ?", COMPANY_ID);
    }

    private void assertPostgreSql16() {
        Integer versionNumber = jdbcTemplate.queryForObject(
                "SELECT current_setting('server_version_num')::integer",
                Integer.class
        );
        assertThat(versionNumber)
                .as("this concurrency test must run against PostgreSQL 16")
                .isBetween(160000, 169999);
    }

    private static byte[] differentBytesWithSameLength(byte[] original) {
        byte[] different = original.clone();
        different[0] = different[0] == 'x' ? (byte) 'y' : (byte) 'x';
        return different;
    }

    private static String requiredEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " environment variable is required.");
        }
        return value;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ConcurrencyConfiguration {

        @Bean
        @Primary
        CoordinatedWorkerLinkRepository coordinatedWorkerLinkRepository(JpaWorkerLinkRepository delegate) {
            return new CoordinatedWorkerLinkRepository(delegate);
        }
    }

    static final class CoordinatedWorkerLinkRepository implements WorkerLinkRepository {

        private final WorkerLinkRepository delegate;
        private final AtomicInteger coordinatedLookupCount = new AtomicInteger();
        private volatile CyclicBarrier lookupBarrier;

        CoordinatedWorkerLinkRepository(WorkerLinkRepository delegate) {
            this.delegate = delegate;
        }

        void coordinateNextTwoTokenLookups() {
            coordinatedLookupCount.set(0);
            lookupBarrier = new CyclicBarrier(2);
        }

        @Override
        public void insert(WorkerLink workerLink) {
            delegate.insert(workerLink);
        }

        @Override
        public WorkerLink update(WorkerLink workerLink) {
            return delegate.update(workerLink);
        }

        @Override
        public Optional<WorkerLink> findByTokenHash(String tokenHash) {
            Optional<WorkerLink> result = delegate.findByTokenHash(tokenHash);
            awaitCoordinatedLookupIfRequired();
            return result;
        }

        @Override
        public Optional<WorkerLink> findByIdAndCompanyId(UUID workerLinkId, UUID companyId) {
            return delegate.findByIdAndCompanyId(workerLinkId, companyId);
        }

        @Override
        public Optional<WorkerLink> findByIdAndCompanyIdForUpdate(UUID workerLinkId, UUID companyId) {
            return delegate.findByIdAndCompanyIdForUpdate(workerLinkId, companyId);
        }

        @Override
        public Optional<WorkerLink> findActiveByTaskIdAndCompanyId(UUID taskId, UUID companyId) {
            return delegate.findActiveByTaskIdAndCompanyId(taskId, companyId);
        }

        @Override
        public Optional<WorkerLink> findByTaskIdAndIdempotencyKey(UUID taskId, String idempotencyKey) {
            return delegate.findByTaskIdAndIdempotencyKey(taskId, idempotencyKey);
        }

        @Override
        public List<WorkerLink> findAllByTaskIdAndCompanyId(UUID taskId, UUID companyId) {
            return delegate.findAllByTaskIdAndCompanyId(taskId, companyId);
        }

        private void awaitCoordinatedLookupIfRequired() {
            CyclicBarrier currentBarrier = lookupBarrier;
            int lookupNumber = coordinatedLookupCount.incrementAndGet();
            if (currentBarrier == null || lookupNumber > 2) {
                return;
            }
            try {
                currentBarrier.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("worker-link lookup coordination was interrupted", exception);
            } catch (BrokenBarrierException | TimeoutException exception) {
                throw new IllegalStateException("two worker-link lookups did not overlap", exception);
            }
        }
    }

    private static final class ForcedRollbackException extends RuntimeException {
    }
}
