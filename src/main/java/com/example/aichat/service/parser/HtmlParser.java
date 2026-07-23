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

    @Override
    public String parse(Path filePath) throws IOException {
        try {
            String html = new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8);
            Document doc = Jsoup.parse(html);

            // 移除不需要的元素
            Elements removes = doc.select("script, style, nav, footer, header, noscript");
            removes.remove();

            return doc.body().wholeText();
        } catch (Exception e) {
            log.error("HTML 解析失败: {}", filePath, e);
            throw new RuntimeException("HTML 解析失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean supports(String fileType) {
        return "html".equals(fileType) || "htm".equals(fileType);
    }
}
