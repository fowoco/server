package com.fowoco.server.common.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fowoco.server.common.web.RequestIdFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

    private static final String REQUEST_ID = "00000000-0000-0000-0000-000000000001";

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-07-21T00:00:00Z"), ZoneOffset.UTC);
        RequestIdFilter requestIdFilter = new RequestIdFilter(() -> UUID.fromString(REQUEST_ID));

        mockMvc = MockMvcBuilders.standaloneSetup(new ValidationTestController())
                .setControllerAdvice(new GlobalExceptionHandler(fixedClock))
                .addFilters(requestIdFilter)
                .build();
    }

    @Test
    void validationErrorUsesCommonResponseAndRequestId() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(RequestIdFilter.HEADER_NAME, REQUEST_ID))
                .andExpect(jsonPath("$.timestamp").value("2026-07-21T00:00:00Z"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.request_id").value(REQUEST_ID))
                .andExpect(jsonPath("$.field_errors[0].field").value("name"));
    }

    @Test
    void unsupportedMethodUsesCommonResponse() throws Exception {
        mockMvc.perform(get("/test/validation"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(RequestIdFilter.HEADER_NAME, REQUEST_ID))
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.request_id").value(REQUEST_ID));
    }

    @RestController
    @RequestMapping("/test")
    private static class ValidationTestController {

        @PostMapping("/validation")
        private void validate(@Valid @RequestBody ValidationRequest request) {
        }
    }

    private record ValidationRequest(@NotBlank(message = "값을 입력해 주세요.") String name) {
    }
}
