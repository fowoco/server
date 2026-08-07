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
    private static final UUID COMPANY_B = UUID.fromString("70000000-0000-0000-0000-000000000002");
    private static final UUID HR_B = UUID.fromString("71000000-0000-0000-0000-000000000002");
    private static final String HR_B_EMAIL = "hr.file.b@example.com";
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
        jdbcTemplate.update(
                """
                INSERT INTO company (company_id, name, status, created_at, updated_at, version)
                VALUES (?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """,
                COMPANY_B,
                "사업장 B"
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
        jdbcTemplate.update(
                """
                INSERT INTO user_account (
                    user_id, company_id, email, normalized_email, password_hash,
                    role, status, created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, ?, 'HR', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """,
                HR_B,
                COMPANY_B,
                HR_B_EMAIL,
                HR_B_EMAIL,
                passwordHash
        );
    }

    @BeforeEach
    void resetFileState() {
        jdbcTemplate.update("DELETE FROM stored_file");
    }

    @org.junit.jupiter.api.AfterAll
    void cleanupFileState() {
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
    void uploadAcceptsValidHwpxStructure() throws Exception {
        String token = accessToken(login(HR_A_EMAIL));
        byte[] hwpxContent = buildValidHwpxZip();

        HttpResponse<String> response = uploadFile(
                token, "contract.hwpx", "application/octet-stream", hwpxContent, "GENERAL"
        );

        assertThat(response.statusCode()).as("body: %s", response.body()).isEqualTo(201);
        assertThat(JsonPath.<String>read(response.body(), "$.name")).isEqualTo("contract.hwpx");
    }

    @Test
    void uploadRejectsHwpxExtensionWithFakeContent() throws Exception {
        String token = accessToken(login(HR_A_EMAIL));

        HttpResponse<String> response = uploadFile(
                token, "fake.hwpx", "application/hwp+zip",
                "hwpx content".getBytes(StandardCharsets.UTF_8), "GENERAL"
        );

        assertThat(response.statusCode()).as("body: %s", response.body()).isEqualTo(415);
    }

    @Test
    void uploadRejectsZipWithoutHwpxContents() throws Exception {
        String token = accessToken(login(HR_A_EMAIL));
        byte[] plainZip = buildZipWithoutHwpxContents();

        HttpResponse<String> response = uploadFile(
                token, "notreally.hwpx", "application/octet-stream", plainZip, "GENERAL"
        );

        assertThat(response.statusCode()).as("body: %s", response.body()).isEqualTo(415);
    }

    private byte[] buildValidHwpxZip() throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(out)) {
            zip.putNextEntry(new java.util.zip.ZipEntry("mimetype"));
            zip.write("application/hwp+zip".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new java.util.zip.ZipEntry("Contents/section0.xml"));
            zip.write("<xml>placeholder</xml>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return out.toByteArray();
    }

    private byte[] buildZipWithoutHwpxContents() throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(out)) {
            zip.putNextEntry(new java.util.zip.ZipEntry("readme.txt"));
            zip.write("just a plain zip file".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return out.toByteArray();
    }

    @Test
    void uploadAcceptsValidHwpFileBySignature() throws Exception {
        String token = accessToken(login(HR_A_EMAIL));
        byte[] hwpContent = buildValidHwpOleFile();

        HttpResponse<String> response = uploadFile(
                token, "contract.hwp", "application/octet-stream", hwpContent, "GENERAL"
        );

        assertThat(response.statusCode()).as("body: %s", response.body()).isEqualTo(201);
        assertThat(JsonPath.<String>read(response.body(), "$.name")).isEqualTo("contract.hwp");
    }

    @Test
    void uploadRejectsHwpExtensionWithInvalidSignature() throws Exception {
        String token = accessToken(login(HR_A_EMAIL));

        HttpResponse<String> response = uploadFile(
                token, "fake.hwp", "application/octet-stream",
                "this is not a real hwp file".getBytes(StandardCharsets.UTF_8), "GENERAL"
        );

        assertThat(response.statusCode()).as("body: %s", response.body()).isEqualTo(415);
    }

    private byte[] buildValidHwpOleFile() throws Exception {
        try (org.apache.poi.poifs.filesystem.POIFSFileSystem fileSystem =
                new org.apache.poi.poifs.filesystem.POIFSFileSystem()) {
            byte[] header = new byte[256];
            byte[] signatureBytes = "HWP Document File".getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(signatureBytes, 0, header, 0, signatureBytes.length);
            fileSystem.createDocument(new java.io.ByteArrayInputStream(header), "FileHeader");

            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            fileSystem.writeFilesystem(out);
            return out.toByteArray();
        }
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

    @Test
    void sameCompanyUserCanDownloadFileAndDownloadIsAudited() throws Exception {
        String token = accessToken(login(HR_A_EMAIL));
        byte[] content = "다운로드할 계약서".getBytes(StandardCharsets.UTF_8);
        HttpResponse<String> uploadResponse = uploadFile(
                token, "근로계약서.pdf", "application/pdf", content, "GENERAL"
        );
        UUID fileId = UUID.fromString(JsonPath.read(uploadResponse.body(), "$.file_id"));

        HttpResponse<byte[]> downloadResponse = downloadFile(fileId, token);

        assertThat(downloadResponse.statusCode()).isEqualTo(200);
        assertThat(downloadResponse.body()).isEqualTo(content);
        assertThat(downloadResponse.headers().firstValue(HttpHeaders.CONTENT_TYPE)).contains("application/pdf");
        assertThat(downloadResponse.headers().firstValue(HttpHeaders.CONTENT_DISPOSITION))
                .hasValueSatisfying(value -> assertThat(value).contains("attachment").contains("filename*="));
        assertThat(downloadResponse.headers().firstValue(HttpHeaders.CACHE_CONTROL)).contains("no-store");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE target_id = ? AND action = 'FILE_DOWNLOADED'",
                Integer.class,
                fileId
        )).isEqualTo(1);
    }

    @Test
    void downloadExposesContentDispositionToAllowedBrowserOrigin() throws Exception {
        String token = accessToken(login(HR_A_EMAIL));
        HttpResponse<String> uploadResponse = uploadFile(
                token,
                "근로계약서.pdf",
                "application/pdf",
                "browser download".getBytes(StandardCharsets.UTF_8),
                "GENERAL"
        );
        UUID fileId = UUID.fromString(JsonPath.read(uploadResponse.body(), "$.file_id"));

        HttpRequest request = HttpRequest.newBuilder(uri("/api/v1/files/" + fileId + "/content"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .GET()
                .build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                .contains("http://localhost:5173");
        assertThat(response.headers().firstValue(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS))
                .hasValueSatisfying(value -> assertThat(value).containsIgnoringCase(HttpHeaders.CONTENT_DISPOSITION));
    }

    @Test
    void otherCompanyCannotDownloadFile() throws Exception {
        String companyAToken = accessToken(login(HR_A_EMAIL));
        HttpResponse<String> uploadResponse = uploadFile(
                companyAToken,
                "note.pdf",
                "application/pdf",
                "company A file".getBytes(StandardCharsets.UTF_8),
                "GENERAL"
        );
        UUID fileId = UUID.fromString(JsonPath.read(uploadResponse.body(), "$.file_id"));
        String companyBToken = accessToken(login(HR_B_EMAIL));

        HttpResponse<byte[]> response = downloadFile(fileId, companyBToken);

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_event WHERE target_id = ? AND action = 'FILE_DOWNLOADED'",
                Integer.class,
                fileId
        )).isZero();
    }

    @Test
    void unauthenticatedUserCannotDownloadFile() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri("/api/v1/files/" + UUID.randomUUID() + "/content"))
                .GET()
                .build();

        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

        assertThat(response.statusCode()).isEqualTo(401);
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

    private HttpResponse<byte[]> downloadFile(UUID fileId, String token) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri("/api/v1/files/" + fileId + "/content"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
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
