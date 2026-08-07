package com.fowoco.server.workerimport.infrastructure.parsing;

import com.fowoco.server.common.error.ApiException;
import com.fowoco.server.workerimport.application.ParsedWorkerImport;
import com.fowoco.server.workerimport.application.error.WorkerImportErrorCode;
import com.fowoco.server.workerimport.application.port.WorkerImportFileParser;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

@Component
public class DefaultWorkerImportFileParser implements WorkerImportFileParser {

    static final int MAX_ROWS = 1_000;
    static final int MAX_COLUMNS = 50;
    static final int MAX_CELL_LENGTH = 500;
    private static final Pattern FORMULA_PREFIX = Pattern.compile("^[=+\\-@].*");
    private static final Set<String> BLOCKED_HEADERS = Set.of(
            "passportnumber", "passportno", "여권번호",
            "alienregistrationnumber", "alienregistrationno", "외국인등록번호", "주민등록번호",
            "accountnumber", "bankaccount", "계좌번호",
            "phone", "phonenumber", "mobile", "전화번호", "휴대폰", "휴대전화",
            "email", "emailaddress", "이메일", "메일주소",
            "address", "homeaddress", "주소", "거주지주소",
            "dateofbirth", "birthdate", "생년월일",
            "legalname", "법정실명"
    );

    @Override
    public ParsedWorkerImport parse(String fileName, byte[] content) {
        if (fileName == null || fileName.isBlank()) {
            throw new ApiException(WorkerImportErrorCode.IMPORT_FILE_TYPE_UNSUPPORTED);
        }
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        ParsedWorkerImport parsed;
        if (lowerName.endsWith(".csv")) {
            parsed = parseCsv(content);
        } else if (lowerName.endsWith(".xlsx")) {
            parsed = parseXlsx(content);
        } else {
            throw new ApiException(WorkerImportErrorCode.IMPORT_FILE_TYPE_UNSUPPORTED);
        }
        validateParsed(parsed);
        return parsed;
    }

    private ParsedWorkerImport parseCsv(byte[] content) {
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new ApiException(WorkerImportErrorCode.IMPORT_FILE_INVALID);
        }
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }
        List<List<String>> table = parseCsvTable(text);
        return toParsed(table);
    }

    private List<List<String>> parseCsvTable(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (quoted) {
                if (current == '"') {
                    if (index + 1 < text.length() && text.charAt(index + 1) == '"') {
                        cell.append('"');
                        index++;
                    } else {
                        quoted = false;
                    }
                } else {
                    cell.append(current);
                }
                continue;
            }
            if (current == '"' && cell.length() == 0) {
                quoted = true;
            } else if (current == ',') {
                row.add(cell.toString());
                cell.setLength(0);
            } else if (current == '\n' || current == '\r') {
                if (current == '\r' && index + 1 < text.length() && text.charAt(index + 1) == '\n') {
                    index++;
                }
                row.add(cell.toString());
                cell.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
            } else {
                cell.append(current);
            }
        }
        if (quoted) {
            throw new ApiException(WorkerImportErrorCode.IMPORT_FILE_INVALID);
        }
        if (cell.length() > 0 || !row.isEmpty()) {
            row.add(cell.toString());
            rows.add(row);
        }
        return rows;
    }

    private ParsedWorkerImport parseXlsx(byte[] content) {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new ApiException(WorkerImportErrorCode.IMPORT_FILE_EMPTY);
            }
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet.getLastRowNum() > MAX_ROWS) {
                throw new ApiException(WorkerImportErrorCode.IMPORT_FILE_LIMIT_EXCEEDED);
            }
            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            List<List<String>> table = new ArrayList<>();
            for (int rowIndex = 0; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row sourceRow = sheet.getRow(rowIndex);
                if (sourceRow == null) {
                    table.add(List.of());
                    continue;
                }
                int lastCell = Math.max(sourceRow.getLastCellNum(), 0);
                if (lastCell > MAX_COLUMNS) {
                    throw new ApiException(WorkerImportErrorCode.IMPORT_FILE_LIMIT_EXCEEDED);
                }
                List<String> values = new ArrayList<>(lastCell);
                for (int column = 0; column < lastCell; column++) {
                    Cell sourceCell = sourceRow.getCell(column, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    if (sourceCell == null) {
                        values.add("");
                    } else if (sourceCell.getCellType() == CellType.FORMULA) {
                        throw new ApiException(WorkerImportErrorCode.IMPORT_FORMULA_NOT_ALLOWED);
                    } else {
                        values.add(formatter.formatCellValue(sourceCell));
                    }
                }
                table.add(values);
            }
            return toParsed(table);
        } catch (ApiException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new ApiException(WorkerImportErrorCode.IMPORT_FILE_INVALID);
        }
    }

    private ParsedWorkerImport toParsed(List<List<String>> table) {
        while (!table.isEmpty() && isBlankRow(table.get(table.size() - 1))) {
            table.remove(table.size() - 1);
        }
        if (table.size() < 2) {
            throw new ApiException(WorkerImportErrorCode.IMPORT_FILE_EMPTY);
        }
        List<String> headers = table.get(0).stream().map(String::strip).toList();
        if (headers.size() > MAX_COLUMNS || table.size() - 1 > MAX_ROWS) {
            throw new ApiException(WorkerImportErrorCode.IMPORT_FILE_LIMIT_EXCEEDED);
        }
        List<Map<String, String>> rows = new ArrayList<>();
        for (int rowIndex = 1; rowIndex < table.size(); rowIndex++) {
            List<String> cells = table.get(rowIndex);
            if (cells.size() > MAX_COLUMNS) {
                throw new ApiException(WorkerImportErrorCode.IMPORT_FILE_LIMIT_EXCEEDED);
            }
            if (isBlankRow(cells)) {
                continue;
            }
            Map<String, String> values = new LinkedHashMap<>();
            for (int column = 0; column < headers.size(); column++) {
                String value = column < cells.size() ? cells.get(column).strip() : "";
                validateCell(value);
                values.put(headers.get(column), value);
            }
            rows.add(values);
        }
        return new ParsedWorkerImport(headers, rows);
    }

    private void validateParsed(ParsedWorkerImport parsed) {
        if (parsed.headers().isEmpty() || parsed.rows().isEmpty()) {
            throw new ApiException(WorkerImportErrorCode.IMPORT_FILE_EMPTY);
        }
        Set<String> seen = new HashSet<>();
        for (String header : parsed.headers()) {
            if (header.isBlank() || !seen.add(header.toLowerCase(Locale.ROOT))) {
                throw new ApiException(WorkerImportErrorCode.IMPORT_FILE_INVALID);
            }
            String normalized = normalizeHeader(header);
            if (BLOCKED_HEADERS.contains(normalized)) {
                throw new ApiException(WorkerImportErrorCode.IMPORT_SENSITIVE_COLUMN_NOT_ALLOWED);
            }
        }
    }

    private void validateCell(String value) {
        if (value.length() > MAX_CELL_LENGTH) {
            throw new ApiException(WorkerImportErrorCode.IMPORT_FILE_INVALID);
        }
        if (FORMULA_PREFIX.matcher(value).matches()) {
            throw new ApiException(WorkerImportErrorCode.IMPORT_FORMULA_NOT_ALLOWED);
        }
    }

    private String normalizeHeader(String header) {
        return header.toLowerCase(Locale.ROOT).replaceAll("[\\s._-]", "");
    }

    private boolean isBlankRow(List<String> cells) {
        return cells.stream().allMatch(value -> value == null || value.isBlank());
    }
}
