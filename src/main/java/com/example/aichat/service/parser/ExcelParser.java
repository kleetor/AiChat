package com.example.aichat.service.parser;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Excel (.xlsx) 解析器，每个 Sheet 转为 Markdown 表格 */
@Component
public class ExcelParser implements DocumentParser {

    private static final Logger log = LoggerFactory.getLogger(ExcelParser.class);
    private static final int MAX_ROWS = 10_000;
    private static final int MAX_TEXT_LENGTH = 5 * 1024 * 1024;

    @Override
    public String parse(Path filePath) throws IOException {
        log.info("[Excel] 开始解析: {}", filePath.getFileName());
        try (InputStream is = Files.newInputStream(filePath);
             XSSFWorkbook wb = new XSSFWorkbook(is)) {

            StringBuilder sb = new StringBuilder();
            int totalRows = 0;

            for (int si = 0; si < wb.getNumberOfSheets(); si++) {
                Sheet sheet = wb.getSheetAt(si);
                if (sheet.getPhysicalNumberOfRows() == 0) continue;

                sb.append("\n[工作表: ").append(sheet.getSheetName()).append("]\n");

                for (int ri = 0; ri <= sheet.getLastRowNum() && totalRows < MAX_ROWS; ri++) {
                    Row row = sheet.getRow(ri);
                    if (row == null) continue;

                    boolean hasContent = false;
                    for (int ci = 0; ci < row.getLastCellNum(); ci++) {
                        Cell cell = row.getCell(ci);
                        String val = cellToString(cell);
                        if (!val.isEmpty()) hasContent = true;
                        sb.append(val);
                        if (ci < row.getLastCellNum() - 1) sb.append(" | ");
                    }
                    if (hasContent) { sb.append("\n"); totalRows++; }
                }
                if (totalRows >= MAX_ROWS) {
                    sb.append("\n[行数超过上限 ").append(MAX_ROWS).append("，已截断]\n");
                    break;
                }
                sb.append("\n");
            }

            String text = sb.toString().trim();
            if (text.length() > MAX_TEXT_LENGTH) {
                log.warn("[Excel] 文本过长，截断: {} → {} 字符", text.length(), MAX_TEXT_LENGTH);
                text = text.substring(0, MAX_TEXT_LENGTH);
            }
            log.info("[Excel] 解析完成: {} 行, {} 字符", totalRows, text.length());
            return text;
        } catch (Exception e) {
            log.error("[Excel] 解析失败: {}", filePath, e);
            throw new RuntimeException("Excel 解析失败: " + e.getMessage(), e);
        }
    }

    private String cellToString(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toString();
                }
                double v = cell.getNumericCellValue();
                if (v == Math.floor(v) && !Double.isInfinite(v)) {
                    yield String.valueOf((long) v);
                }
                yield String.valueOf(v);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try { yield cell.getStringCellValue().trim(); }
                catch (Exception e) { yield String.valueOf(cell.getNumericCellValue()); }
            }
            default -> "";
        };
    }

    @Override
    public boolean supports(String fileType) {
        return "xlsx".equals(fileType);
    }
}
