package com.fowoco.server.workerimport.infrastructure.parsing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fowoco.server.common.error.ApiException;
import com.fowoco.server.workerimport.application.error.WorkerImportErrorCode;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
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

    @Test
    void acceptsExactlyOneThousandDataRows() {
        StringBuilder csv = new StringBuilder("이름,국적\n");
        for (int row = 1; row <= 1_000; row++) {
            csv.append("근로자").append(row).append(",VN\n");
        }

        assertThat(parser.parse("workers.csv", csv.toString().getBytes(StandardCharsets.UTF_8)).rows())
                .hasSize(1_000);
    }

    @Test
    void normalizesXlsxDateCellsToIsoDateRegardlessOfDisplayFormat() throws Exception {
        byte[] content;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("workers");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("이름");
            header.createCell(1).setCellValue("체류만료일");

            var slashStyle = workbook.createCellStyle();
            slashStyle.setDataFormat(workbook.createDataFormat().getFormat("m/d/yy"));
            var first = sheet.createRow(1);
            first.createCell(0).setCellValue("응웬반안");
            first.createCell(1).setCellValue(LocalDate.of(2027, 1, 2));
            first.getCell(1).setCellStyle(slashStyle);

            var dottedStyle = workbook.createCellStyle();
            dottedStyle.setDataFormat(workbook.createDataFormat().getFormat("yyyy. m. d"));
            var second = sheet.createRow(2);
            second.createCell(0).setCellValue("쩐티비");
            second.createCell(1).setCellValue(LocalDate.of(2027, 12, 31));
            second.getCell(1).setCellStyle(dottedStyle);

            workbook.write(output);
            content = output.toByteArray();
        }

        var result = parser.parse("workers.xlsx", content);

        assertThat(result.rows().get(0).get("체류만료일")).isEqualTo("2027-01-02");
        assertThat(result.rows().get(1).get("체류만료일")).isEqualTo("2027-12-31");
    }
}
