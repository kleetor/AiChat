package com.example.aichat.service.parser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** HTML 解析器，去除标签保留纯文本 */
@Component
public class HtmlParser implements DocumentParser {

    private static final Logger log = LoggerFactory.getLogger(HtmlParser.class);
    private static final int MAX_TEXT_LENGTH = 5 * 1024 * 1024;

    @Override
    public String parse(Path filePath) throws IOException {
        byte[] bytes = Files.readAllBytes(filePath);
        log.info("[HTML] 开始解析: {} ({}KB)", filePath.getFileName(), bytes.length / 1024);
        try {
            String html = new String(bytes, StandardCharsets.UTF_8);
            Document doc = Jsoup.parse(html);
            Elements removes = doc.select("script, style, nav, footer, header, noscript");
            removes.remove();
            String text = doc.body().wholeText();
            if (text.length() > MAX_TEXT_LENGTH) {
                log.warn("[HTML] 文本过长，截断: {} → {} 字符", text.length(), MAX_TEXT_LENGTH);
                text = text.substring(0, MAX_TEXT_LENGTH);
            }
            log.info("[HTML] 解析完成: {} 字符", text.length());
            return text;
        } catch (Exception e) {
            log.error("[HTML] 解析失败: {}", filePath, e);
            throw new RuntimeException("HTML 解析失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean supports(String fileType) {
        return "html".equals(fileType) || "htm".equals(fileType);
    }
}
