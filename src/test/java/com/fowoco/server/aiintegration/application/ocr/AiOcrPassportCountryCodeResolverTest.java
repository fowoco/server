package com.fowoco.server.aiintegration.application.ocr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fowoco.server.aiintegration.application.error.AiRuntimeContractException;
import com.fowoco.server.aiintegration.application.error.AiRuntimeFailureCode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class AiOcrPassportCountryCodeResolverTest {

    private final AiOcrPassportCountryCodeResolver resolver =
            new AiOcrPassportCountryCodeResolver();

    @ParameterizedTest
    @CsvSource({
            "KR, KOR",
            "PH, PHL",
            "JP, JPN",
            "CN, CHN",
            "VN, VNM",
            "' vn ', VNM"
    })
    void mapsWorkerAlpha2NationalityToSupportedPassportTemplateCountry(
            String workerNationality,
            String expectedOcrCountry
    ) {
        assertThat(resolver.fromWorkerNationalityCode(workerNationality))
                .isEqualTo(expectedOcrCountry);
    }

    @ParameterizedTest
    @ValueSource(strings = {"TH", "NP"})
    void rejectsCountriesWithoutADeployedPassportTemplate(String workerNationality) {
        assertThatThrownBy(() -> resolver.fromWorkerNationalityCode(workerNationality))
                .isInstanceOfSatisfying(AiRuntimeContractException.class, exception ->
                        assertThat(exception.failureCode())
                                .isEqualTo(AiRuntimeFailureCode.UNSUPPORTED_OCR_COUNTRY));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "V", "VNM", "V1"})
    void rejectsWorkerNationalityThatIsNotAlpha2(String workerNationality) {
        assertThatThrownBy(() -> resolver.fromWorkerNationalityCode(workerNationality))
                .isInstanceOfSatisfying(AiRuntimeContractException.class, exception ->
                        assertThat(exception.failureCode())
                                .isEqualTo(AiRuntimeFailureCode.INVALID_REQUEST_CONTRACT));
    }
}
