package com.example.aichat.service.parser;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Word (.docx) 解析器 */
@Component
public class DocxParser implements DocumentParser {

    private static final Logger log = LoggerFactory.getLogger(DocxParser.class);

    @Override
    public String parse(Path filePath) throws IOException {
        try (InputStream is = Files.newInputStream(filePath);
             XWPFDocument doc = new XWPFDocument(is)) {

            StringBuilder sb = new StringBuilder();

            // 段落
            List<XWPFParagraph> paragraphs = doc.getParagraphs();
            for (XWPFParagraph p : paragraphs) {
                String text = p.getText();
                if (text != null && !text.isBlank()) {
                    sb.append(text.trim()).append("\n");
                }
            }

            // 表格
            List<XWPFTable> tables = doc.getTables();
            for (int ti = 0; ti < tables.size(); ti++) {
                sb.append("\n[表格 ").append(ti + 1).append("]\n");
                XWPFTable table = tables.get(ti);
                List<XWPFTableRow> rows = table.getRows();
                for (int ri = 0; ri < rows.size(); ri++) {
                    XWPFTableRow row = rows.get(ri);
                    List<XWPFTableCell> cells = row.getTableCells();
                    for (int ci = 0; ci < cells.size(); ci++) {
                        String cellText = cells.get(ci).getText().replace("\n", " ").trim();
                        sb.append(cellText);
                        if (ci < cells.size() - 1) sb.append(" | ");
                    }
                    sb.append("\n");
                    // 表头分隔线
                    if (ri == 0 && rows.size() > 1) {
                        sb.append("-".repeat(10)).append(" | ").append("-".repeat(10)).append("\n");
                    }
                }
                sb.append("\n");
            }

            return sb.toString().trim();
        } catch (Exception e) {
            log.error("Word 解析失败: {}", filePath, e);
            throw new RuntimeException("Word 文档解析失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean supports(String fileType) {
        return "docx".equals(fileType);
    }
}
