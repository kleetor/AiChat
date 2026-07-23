package com.example.aichat.service.parser;

import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** PPT (.pptx) 解析器，按幻灯片提取文本 */
// @Component — 前端仅开放 TXT/MD/PDF/DOCX，PPT 格式暂时隐藏
public class PptxParser implements DocumentParser {

    private static final Logger log = LoggerFactory.getLogger(PptxParser.class);
    private static final int MAX_TEXT_LENGTH = 5 * 1024 * 1024;

    @Override
    public String parse(Path filePath) throws IOException {
        log.info("[PPT] 开始解析: {}", filePath.getFileName());
        try (InputStream is = Files.newInputStream(filePath);
             XMLSlideShow ppt = new XMLSlideShow(is)) {

            StringBuilder sb = new StringBuilder();

            for (XSLFSlide slide : ppt.getSlides()) {
                int slideNum = slide.getSlideNumber();
                sb.append("\n[幻灯片 ").append(slideNum).append("]\n");

                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape textShape) {
                        String text = textShape.getText();
                        if (text != null && !text.isBlank()) {
                            sb.append(text.trim()).append("\n");
                        }
                    }
                }
            }

            String text = sb.toString().trim();
            if (text.length() > MAX_TEXT_LENGTH) {
                log.warn("[PPT] 文本过长，截断: {} → {} 字符", text.length(), MAX_TEXT_LENGTH);
                text = text.substring(0, MAX_TEXT_LENGTH);
            }
            log.info("[PPT] 解析完成: {} 字符", text.length());
            return text;
        } catch (Exception e) {
            log.error("[PPT] 解析失败: {}", filePath, e);
            throw new RuntimeException("PPT 解析失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean supports(String fileType) {
        return "pptx".equals(fileType);
    }
}
