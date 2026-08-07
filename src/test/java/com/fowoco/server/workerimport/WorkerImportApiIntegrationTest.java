package com.fowoco.server.workerimport;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WorkerImportApiIntegrationTest {

    private static final UUID COMPANY_A = UUID.fromString("da000000-0000-0000-0000-000000000001");
    private static final UUID COMPANY_B = UUID.fromString("db000000-0000-0000-0000-000000000002");
    private static final UUID HR_A = UUID.fromString("dc000000-0000-0000-0000-000000000001");
    private static final UUID HR_B = UUID.fromString("dd000000-0000-0000-0000-000000000002");
    private static final String HR_A_EMAIL = "hr.import.a@example.com";
    private static final String HR_B_EMAIL = "hr.import.b@example.com";
    private static final String PASSWORD = "Test-password-1!";
    private static final String BOUNDARY = "FowocoWorkerImportBoundary";

    private static final Path FILE_STORAGE_PATH = createFileStoragePath();

    @DynamicPropertySource
    static void fileStorage(DynamicPropertyRegistry registry) {
        registry.add("app.file-storage.local-path", FILE_STORAGE_PATH::toString);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeAll
    void seedAccounts() {
        deleteFixtures();
        insertCompany(COMPANY_A, "Import 사업장 A");
        insertCompany(COMPANY_B, "Import 사업장 B");
        String hash = passwordEncoder.encode(PASSWORD);
        insertUser(HR_A, COMPANY_A, HR_A_EMAIL, hash);
        insertUser(HR_B, COMPANY_B, HR_B_EMAIL, hash);
    }

    @BeforeEach
    void resetImports() {
        jdbcTemplate.update("DELETE FROM audit_event WHERE target_type = 'WORKER_IMPORT'");
        jdbcTemplate.update("DELETE FROM worker_import_row");
        jdbcTemplate.update("DELETE FROM worker_import_job");
        jdbcTemplate.update("DELETE FROM worker WHERE company_id IN (?, ?)", COMPANY_A, COMPANY_B);
        jdbcTemplate.update("DELETE FROM stored_file WHERE company_id IN (?, ?)", COMPANY_A, COMPANY_B);
    }

    @AfterAll
    void cleanup() {
        deleteFixtures();
    }

    @Test
    void csvRowsAreReviewedFixedAndCommittedWithoutDuplicateReplay() throws Exception {
        String tokenA = accessToken(login(HR_A_EMAIL));
        String csv = "이름,국적,언어,체류만료일\n"
                + "응웬반안,VN,vi,2027-01-01\n"
                + "쩐티비,VN,vi,잘못된날짜\n";
        HttpResponse<String> created = upload(tokenA, "worker-import-create-0001", "workers.csv", csv);
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        UUID importId = UUID.fromString(JsonPath.read(created.body(), "$.import_id"));
        assertThat(JsonPath.<String>read(created.body(), "$.status")).isEqualTo("UPLOADED");

        HttpResponse<String> mapped = json(
                "PUT", "/api/v1/imports/" + importId + "/mappings", tokenA, null,
                """
                {"expected_version":0,"mappings":{
                  "이름":"display_name","국적":"nationality_code",
                  "언어":"preferred_language","체류만료일":"stay_expiry_date"
                }}
                """
        );
        assertThat(mapped.statusCode()).as(mapped.body()).isEqualTo(200);
        assertThat(JsonPath.<String>read(mapped.body(), "$.status")).isEqualTo("MAPPED");

        HttpResponse<String> validated = json(
                "POST", "/api/v1/imports/" + importId + "/validate", tokenA, null,
                "{\"expected_version\":1}"
        );
        assertThat(validated.statusCode()).as(validated.body()).isEqualTo(200);
        assertThat(JsonPath.<String>read(validated.body(), "$.status")).isEqualTo("REVIEW_REQUIRED");
        assertThat(JsonPath.<Integer>read(validated.body(), "$.valid_rows")).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(validated.body(), "$.invalid_rows")).isEqualTo(1);

        HttpResponse<String> patched = json(
                "PATCH", "/api/v1/imports/" + importId + "/rows", tokenA, null,
                """
                {"expected_version":2,"rows":[{
                  "row_number":3,"excluded":false,
                  "values":{"stay_expiry_date":"2027-02-01"}
                }]}
                """
        );
        assertThat(patched.statusCode()).as(patched.body()).isEqualTo(200);

        HttpResponse<String> retried = json(
                "POST", "/api/v1/imports/" + importId + "/retry", tokenA, null,
                "{\"expected_version\":3}"
        );
        assertThat(retried.statusCode()).as(retried.body()).isEqualTo(200);
        assertThat(JsonPath.<String>read(retried.body(), "$.status")).isEqualTo("READY");
        assertThat(JsonPath.<Integer>read(retried.body(), "$.valid_rows")).isEqualTo(2);

        HttpResponse<String> firstCommit = json(
                "POST", "/api/v1/imports/" + importId + "/commit", tokenA, "worker-import-commit-0001",
                "{\"expected_version\":4,\"selected_row_numbers\":[2]}"
        );
        assertThat(firstCommit.statusCode()).as(firstCommit.body()).isEqualTo(200);
        assertThat(JsonPath.<Integer>read(firstCommit.body(), "$.committed_rows")).isEqualTo(1);
        assertThat(JsonPath.<String>read(firstCommit.body(), "$.status")).isEqualTo("READY");

        HttpResponse<String> replay = json(
                "POST", "/api/v1/imports/" + importId + "/commit", tokenA, "worker-import-commit-0001",
                "{\"expected_version\":4,\"selected_row_numbers\":[2]}"
        );
        assertThat(replay.statusCode()).as(replay.body()).isEqualTo(200);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM worker WHERE company_id = ?", Integer.class, COMPANY_A
        )).isEqualTo(1);

        HttpResponse<String> finalCommit = json(
                "POST", "/api/v1/imports/" + importId + "/commit", tokenA, "worker-import-commit-0002",
                "{\"expected_version\":5,\"selected_row_numbers\":[3]}"
        );
        assertThat(finalCommit.statusCode()).as(finalCommit.body()).isEqualTo(200);
        assertThat(JsonPath.<String>read(finalCommit.body(), "$.status")).isEqualTo("COMMITTED");
        assertThat(JsonPath.<Integer>read(finalCommit.body(), "$.committed_rows")).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM worker WHERE company_id = ?", Integer.class, COMPANY_A
        )).isEqualTo(2);

        String tokenB = accessToken(login(HR_B_EMAIL));
        HttpResponse<String> otherTenant = get("/api/v1/imports/" + importId, tokenB);
        assertThat(otherTenant.statusCode()).isEqualTo(404);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE target_id = ? AND action = 'WORKER_IMPORT_COMMITTED'",
                Integer.class,
                importId
        )).isEqualTo(2);
    }

    @Test
    void uploadRejectsSensitiveColumnBeforePersistingFile() throws Exception {
        String token = accessToken(login(HR_A_EMAIL));
        HttpResponse<String> response = upload(
                token,
                "worker-import-create-0002",
                "workers.csv",
                "이름,외국인등록번호\n응웬반안,000000-0000000\n"
        );

        assertThat(response.statusCode()).as(response.body()).isEqualTo(422);
        assertThat(JsonPath.<String>read(response.body(), "$.code"))
                .isEqualTo("IMPORT_SENSITIVE_COLUMN_NOT_ALLOWED");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM worker_import_job", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM stored_file WHERE company_id = ?", Integer.class, COMPANY_A
        )).isZero();
    }

    @Test
    void uploadRejectsEmptyFileAndRowsBeyondTheLimit() throws Exception {
        String token = accessToken(login(HR_A_EMAIL));

        HttpResponse<String> empty = upload(
                token,
                "worker-import-empty-0001",
                "empty.csv",
                "이름,국적\n"
        );
        assertThat(empty.statusCode()).as(empty.body()).isEqualTo(422);
        assertThat(JsonPath.<String>read(empty.body(), "$.code")).isEqualTo("IMPORT_FILE_EMPTY");

        StringBuilder tooManyRows = new StringBuilder("이름,국적\n");
        for (int row = 1; row <= 1_001; row++) {
            tooManyRows.append("근로자").append(row).append(",VN\n");
        }
        HttpResponse<String> overLimit = upload(
                token,
                "worker-import-limit-0001",
                "too-many-workers.csv",
                tooManyRows.toString()
        );
        assertThat(overLimit.statusCode()).as(overLimit.body()).isEqualTo(422);
        assertThat(JsonPath.<String>read(overLimit.body(), "$.code"))
                .isEqualTo("IMPORT_FILE_LIMIT_EXCEEDED");

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM worker_import_job", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM stored_file WHERE company_id = ?", Integer.class, COMPANY_A
        )).isZero();
    }

    private HttpResponse<String> upload(String token, String key, String fileName, String csv) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(("--" + BOUNDARY + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n")
                .getBytes(StandardCharsets.UTF_8));
        out.write("Content-Type: text/csv\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        out.write(csv.getBytes(StandardCharsets.UTF_8));
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
        out.write(("--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(uri("/api/v1/imports"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header("Idempotency-Key", key)
                .header(HttpHeaders.CONTENT_TYPE, "multipart/form-data; boundary=" + BOUNDARY)
                .POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray()))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> json(
            String method,
            String path,
            String token,
            String idempotencyKey,
            String body
    ) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri(path))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header(HttpHeaders.CONTENT_TYPE, MediaTypeValue.JSON)
                .method(method, HttpRequest.BodyPublishers.ofString(body));
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        return httpClient.send(
                HttpRequest.newBuilder(uri(path))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private HttpResponse<String> login(String email) throws Exception {
        return httpClient.send(
                HttpRequest.newBuilder(uri("/api/v1/auth/login"))
                        .header(HttpHeaders.CONTENT_TYPE, MediaTypeValue.JSON)
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"
                        )).build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private String accessToken(HttpResponse<String> response) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return JsonPath.read(response.body(), "$.access_token");
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private void insertCompany(UUID id, String name) {
        jdbcTemplate.update(
                "INSERT INTO company (company_id, name, status) VALUES (?, ?, 'ACTIVE')",
                id,
                name
        );
    }

    private void insertUser(UUID id, UUID companyId, String email, String passwordHash) {
        jdbcTemplate.update(
                """
                INSERT INTO user_account (
                    user_id, company_id, email, normalized_email, password_hash, role, status
                ) VALUES (?, ?, ?, ?, ?, 'HR', 'ACTIVE')
                """,
                id, companyId, email, email, passwordHash
        );
    }

    private void deleteFixtures() {
        jdbcTemplate.update("DELETE FROM audit_event WHERE company_id IN (?, ?)", COMPANY_A, COMPANY_B);
        jdbcTemplate.update("DELETE FROM worker_import_row WHERE company_id IN (?, ?)", COMPANY_A, COMPANY_B);
        jdbcTemplate.update("DELETE FROM worker_import_job WHERE company_id IN (?, ?)", COMPANY_A, COMPANY_B);
        jdbcTemplate.update("DELETE FROM worker WHERE company_id IN (?, ?)", COMPANY_A, COMPANY_B);
        jdbcTemplate.update("DELETE FROM stored_file WHERE company_id IN (?, ?)", COMPANY_A, COMPANY_B);
        jdbcTemplate.update("DELETE FROM refresh_token WHERE company_id IN (?, ?)", COMPANY_A, COMPANY_B);
        jdbcTemplate.update("DELETE FROM user_account WHERE company_id IN (?, ?)", COMPANY_A, COMPANY_B);
        jdbcTemplate.update("DELETE FROM company WHERE company_id IN (?, ?)", COMPANY_A, COMPANY_B);
    }

    private static final class MediaTypeValue {
        private static final String JSON = "application/json";
    }

    private static Path createFileStoragePath() {
        try {
            return Files.createTempDirectory("fowoco-worker-import-test-");
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
