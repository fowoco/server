package com.fowoco.server.task.application.renewal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fowoco.server.aiintegration.application.document.DocumentGenerationClient;
import com.fowoco.server.aiintegration.application.document.GeneratedDocumentFile;
import com.fowoco.server.aiintegration.application.error.AiRuntimeCallException;
import com.fowoco.server.aiintegration.application.renewal.RenewalGeneratedDocument;
import com.fowoco.server.file.application.FileService;
import com.fowoco.server.worker.application.WorkerDocumentService;
import com.fowoco.server.worker.domain.DocumentType;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GeneratedDocumentServiceTest {

    private final DocumentGenerationClient generationClient = mock(DocumentGenerationClient.class);
    private final GeneratedDocumentService service = new GeneratedDocumentService(
            generationClient,
            mock(FileService.class),
            mock(WorkerDocumentService.class)
    );

    @BeforeEach
    void setUp() {
        when(generationClient.generate(any())).thenAnswer(invocation -> {
            var request = invocation.<com.fowoco.server.aiintegration.application.document.DocumentGenerationRequest>
                    getArgument(0);
            return new GeneratedDocumentFile(
                    request.templateId() + ".hwp",
                    request.format(),
                    request.templateId().getBytes(StandardCharsets.UTF_8)
            );
        });
    }

    @Test
    void routesCaseWideAgentDocumentsToTheOwningTask() {
        List<RenewalGeneratedDocument> documents = caseWideDocuments();

        assertThat(service.prepare("RECONTRACT", documents))
                .extracting(result -> result.descriptor().templateId())
                .containsExactly("standard_labor_contract_v6");
        assertThat(service.prepare("EMPLOYMENT_PERIOD_EXTENSION", documents))
                .extracting(result -> result.descriptor().templateId())
                .containsExactly("employment_extension_application_v12_3");
        assertThat(service.prepare("STAY_PERIOD_EXTENSION", documents))
                .extracting(result -> result.descriptor().templateId())
                .containsExactly(
                        "immigration_integrated_application_v34",
                        "identity_guaranty_v129"
                );
        verify(generationClient, times(4)).generate(any());
    }

    @Test
    void rejectsAResponseWithoutADocumentForTheCurrentTask() {
        assertThatThrownBy(() -> service.prepare(
                "RECONTRACT",
                List.of(document("immigration_integrated_application_v34"))
        )).isInstanceOf(AiRuntimeCallException.class);
    }

    @Test
    void rejectsAStayResponseMissingOneOfItsOwnedDocuments() {
        assertThatThrownBy(() -> service.prepare(
                "STAY_PERIOD_EXTENSION",
                List.of(document("immigration_integrated_application_v34"))
        )).isInstanceOf(AiRuntimeCallException.class);
    }

    @Test
    void rejectsADraftWithMissingRequiredMappedValuesBeforeGeneration() {
        RenewalGeneratedDocument incomplete = new RenewalGeneratedDocument(
                "standard_labor_contract_v6",
                "standard_labor_contract_v6",
                "hwp",
                "READY",
                null,
                null,
                List.of(),
                List.of(),
                Map.of(
                        "employee_name", "NGUYEN VAN AN",
                        "enterprise_name", "FOWOCO"
                )
        );

        assertThatThrownBy(() -> service.prepare("RECONTRACT", List.of(incomplete)))
                .isInstanceOf(AiRuntimeCallException.class);
        verify(generationClient, times(0)).generate(any());
    }

    @Test
    void mapsGeneratedTemplatesToTheirActualDocumentTypes() {
        assertThat(service.documentType("standard_labor_contract_v6"))
                .isEqualTo(DocumentType.CONTRACT);
        assertThat(service.documentType("employment_extension_application_v12_3"))
                .isEqualTo(DocumentType.EMPLOYMENT_EXTENSION_APPLICATION);
        assertThat(service.documentType("immigration_integrated_application_v34"))
                .isEqualTo(DocumentType.INTEGRATED_APPLICATION);
        assertThat(service.documentType("identity_guaranty_v129"))
                .isEqualTo(DocumentType.IDENTITY_GUARANTY);
    }

    private List<RenewalGeneratedDocument> caseWideDocuments() {
        return List.of(
                document("standard_labor_contract_v6"),
                document("employment_extension_application_v12_3"),
                document("immigration_integrated_application_v34"),
                document("identity_guaranty_v129")
        );
    }

    private RenewalGeneratedDocument document(String templateId) {
        return new RenewalGeneratedDocument(
                templateId, templateId, "hwp", "READY",
                null, null, List.of(), List.of(), values(templateId)
        );
    }

    private Map<String, Object> values(String templateId) {
        return switch (templateId) {
            case "standard_labor_contract_v6" -> Map.of(
                    "employee_name", "NGUYEN VAN AN",
                    "employee_birthdate", "1995-04-12",
                    "enterprise_name", "FOWOCO"
            );
            case "employment_extension_application_v12_3" -> Map.of(
                    "employee_1_name", "NGUYEN VAN AN",
                    "employee_1_resident_number", "950412-5123456",
                    "employee_1_passport_number", "DEMO-P06-NOT-VALID"
            );
            case "immigration_integrated_application_v34" -> Map.of(
                    "given_names", "VAN AN",
                    "passport_number", "DEMO-P06-NOT-VALID",
                    "birth_year", "1995",
                    "birth_month", "04",
                    "birth_day", "12"
            );
            case "identity_guaranty_v129" -> Map.of(
                    "foreign_name", "NGUYEN VAN AN",
                    "foreign_birthdate", "1995-04-12",
                    "foreign_passport", "DEMO-P06-NOT-VALID"
            );
            default -> throw new IllegalArgumentException("unsupported test template");
        };
    }
}
