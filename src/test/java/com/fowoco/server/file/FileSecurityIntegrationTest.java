package com.fowoco.server.file;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
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

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FileSecurityIntegrationTest {

    private static final UUID COMPANY_A = UUID.fromString("70000000-0000-0000-0000-000000000001");
    private static final UUID HR_A = UUID.fromString("71000000-0000-0000-0000-000000000001");
    private static final String HR_A_EMAIL = "hr.file.a@example.com";
    private static final String PASSWORD = "Test-password-1!";
    private static final String BOUNDARY = "FowocoTestBoundary1234";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeAll
    void seedCompanyAndUser() {
        jdbcTemplate.update("DELETE FROM document_request_draft_type");
        jdbcTemplate.update("DELETE FROM document_request_draft");
        jdbcTemplate.update("DELETE FROM stored_file");
        jdbcTemplate.update("DELETE FROM event_consumption");
        jdbcTemplate.update("DELETE FROM event_publication");
        jdbcTemplate.update("DELETE FROM audit_event");
        jdbcTemplate.update("DELETE FROM task_evidence");
        jdbcTemplate.update("DELETE FROM external_submission");
        jdbcTemplate.update("DELETE FROM approval_request");
        jdbcTemplate.update("DELETE FROM task_transition_history");
        jdbcTemplate.update("DELETE FROM task_checklist_item");
        jdbcTemplate.update("DELETE FROM task");
        jdbcTemplate.update("DELETE FROM worker_document");
        jdbcTemplate.update("DELETE FROM worker");
        jdbcTemplate.update(
                """
                INSERT INTO company (company_id, name, status, created_at, updated_at, version)
                VALUES (?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """,
                COMPANY_A,
                "사업장 A"
        );
        String passwordHash = passwordEncoder.encode(PASSWORD);
        jdbcTemplate.update(
                """
                INSERT INTO user_account (
                    user_id, company_id, email, normalized_email, password_hash,
                    role, status, created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, ?, 'HR', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """,
                HR_A,
                COMPANY_A,
                HR_A_EMAIL,
                HR_A_EMAIL,
                passwordHash
        );
    }

    @BeforeEach
    void resetFileState() {
        jdbcTemplate.update("DELETE FROM stored_file");
    }

    @Test
    void uploadSucceedsAndAppendsAuditEvent() throws Exception {
        String token = accessToken(login(HR_A_EMAIL));

        HttpResponse<String> response = uploadFile(token, "note.pdf", "application/pdf", "test file content".getBytes(StandardCharsets.UTF_8), "GENERAL");

        assertThat(response.statusCode()).isEqualTo(201);
        String fileId = JsonPath.read(response.body(), "$.file_id");
        assertThat(JsonPath.<String>read(response.body(), "$.name")).isEqualTo("note.pdf");
        assertThat(JsonPath.<String>read(response.body(), "$.scan_status")).isEqualTo("NOT_SCANNED");

        java.util.List<java.util.Map<String, Object>> allAuditRows = jdbcTemplate.queryForList("SELECT * FROM audit_event");
        System.out.println("DEBUG audit_event rows: " + allAuditRows);
        System.out.println("DEBUG looking for fileId: " + fileId);
        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE target_id = ? AND action = 'FILE_UPLOADED'",
                Integer.class,
                UUID.fromString(fileId)
        );
        assertThat(auditCount).isEqualTo(1);
    }

    @Test
    void uploadRejectsUnsupportedMimeType() throws Exception {
        String token = accessToken(login(HR_A_EMAIL));

        HttpResponse<String> response = uploadFile(
                token, "script.exe", "application/x-msdownload", "malicious".getBytes(StandardCharsets.UTF_8), "GENERAL"
        );

        assertThat(response.statusCode()).as("body: %s", response.body()).isEqualTo(415);
    }

    @Test
    void uploadRejectsNonExistentTaskId() throws Exception {
        String token = accessToken(login(HR_A_EMAIL));
        UUID nonExistentTaskId = UUID.randomUUID();

        HttpResponse<String> response = uploadFileWithTask(
                token, "note.pdf", "application/pdf", "content".getBytes(StandardCharsets.UTF_8), nonExistentTaskId
        );

        assertThat(response.statusCode()).isEqualTo(404);
    }

    private HttpResponse<String> uploadFile(
            String token, String filename, String mimeType, byte[] content, String purpose
    ) throws Exception {
        return uploadFile(token, filename, mimeType, content, purpose, null);
    }

    private HttpResponse<String> uploadFile(
            String token, String filename, String mimeType, byte[] content, String purpose, UUID taskId
    ) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();

        writePart(out, "file", filename, mimeType, content);
        writeFieldPart(out, "purpose", purpose);
        if (taskId != null) {
            writeFieldPart(out, "taskId", taskId.toString());
        }
        out.write(("--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder(uri("/api/v1/files"))
                .header(HttpHeaders.CONTENT_TYPE, "multipart/form-data; boundary=" + BOUNDARY)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray()))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> uploadFileWithTask(
            String token, String filename, String mimeType, byte[] content, UUID taskId
    ) throws Exception {
        return uploadFile(token, filename, mimeType, content, "TASK_EVIDENCE", taskId);
    }
    

    private void writePart(
            java.io.ByteArrayOutputStream out, String name, String filename, String mimeType, byte[] content
    ) throws java.io.IOException {
        out.write(("--" + BOUNDARY + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(
                ("Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + filename + "\"\r\n")
                        .getBytes(StandardCharsets.UTF_8)
        );
        out.write(("Content-Type: " + mimeType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(content);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private void writeFieldPart(java.io.ByteArrayOutputStream out, String name, String value) throws java.io.IOException {
        out.write(("--" + BOUNDARY + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(value.getBytes(StandardCharsets.UTF_8));
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> login(String email) throws Exception {
        String body = """
                {"email":"%s","password":"%s"}
                """.formatted(email, PASSWORD);
        HttpRequest request = HttpRequest.newBuilder(uri("/api/v1/auth/login"))
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String accessToken(HttpResponse<String> loginResponse) {
        assertThat(loginResponse.statusCode()).isEqualTo(200);
        return JsonPath.read(loginResponse.body(), "$.access_token");
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
