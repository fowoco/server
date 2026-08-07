package com.fowoco.server.workerimport.infrastructure.parsing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fowoco.server.common.error.ApiException;
import com.fowoco.server.workerimport.application.error.WorkerImportErrorCode;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class DefaultWorkerImportFileParserTest {

    private final DefaultWorkerImportFileParser parser = new DefaultWorkerImportFileParser();

    @Test
    void parsesQuotedUtf8CsvWithoutLosingCommas() {
        var result = parser.parse(
                "workers.csv",
                "이름,국적,메모\n\"응웬, 반안\",VN,정상\n".getBytes(StandardCharsets.UTF_8)
        );

        assertThat(result.headers()).containsExactly("이름", "국적", "메모");
        assertThat(result.rows()).hasSize(1);
        assertThat(result.rows().get(0).get("이름")).isEqualTo("응웬, 반안");
    }

    @Test
    void rejectsSensitiveColumnsAndFormulaInjection() {
        assertThatThrownBy(() -> parser.parse(
                "workers.csv",
                "이름,여권번호\n응웬반안,M1234\n".getBytes(StandardCharsets.UTF_8)
        )).isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.errorCode()).isEqualTo(WorkerImportErrorCode.IMPORT_SENSITIVE_COLUMN_NOT_ALLOWED)
        );

        assertThatThrownBy(() -> parser.parse(
                "workers.csv",
                "이름,국적\n=HYPERLINK(\"x\"),VN\n".getBytes(StandardCharsets.UTF_8)
        )).isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.errorCode()).isEqualTo(WorkerImportErrorCode.IMPORT_FORMULA_NOT_ALLOWED)
        );
    }

    @Test
    void parsesXlsxButRejectsFormulaCells() throws Exception {
        byte[] normal;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("workers");
            sheet.createRow(0).createCell(0).setCellValue("이름");
            sheet.getRow(0).createCell(1).setCellValue("국적");
            sheet.createRow(1).createCell(0).setCellValue("응웬반안");
            sheet.getRow(1).createCell(1).setCellValue("VN");
            workbook.write(output);
            normal = output.toByteArray();
        }
        assertThat(parser.parse("workers.xlsx", normal).rows()).hasSize(1);

        byte[] formula;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("workers");
            sheet.createRow(0).createCell(0).setCellValue("이름");
            sheet.createRow(1).createCell(0).setCellFormula("1+1");
            workbook.write(output);
            formula = output.toByteArray();
        }
        assertThatThrownBy(() -> parser.parse("workers.xlsx", formula))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(WorkerImportErrorCode.IMPORT_FORMULA_NOT_ALLOWED)
                );
    }
}
