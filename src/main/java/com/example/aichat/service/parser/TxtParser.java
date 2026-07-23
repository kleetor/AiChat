package com.example.aichat.service.parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** 纯文本/Markdown 解析器 */
@Component
public class TxtParser implements DocumentParser {

    private static final Logger log = LoggerFactory.getLogger(TxtParser.class);
    /** 解析文本最大长度（5MB），超出截断 */
    private static final int MAX_TEXT_LENGTH = 5 * 1024 * 1024;

    @Override
    public String parse(Path filePath) throws IOException {
        byte[] bytes = Files.readAllBytes(filePath);
        log.info("[TXT] 开始解析: {} ({}KB)", filePath.getFileName(), bytes.length / 1024);
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (text.length() > MAX_TEXT_LENGTH) {
            log.warn("[TXT] 文本过长，截断: {} → {} 字符", text.length(), MAX_TEXT_LENGTH);
            text = text.substring(0, MAX_TEXT_LENGTH);
        }
        log.info("[TXT] 解析完成: {} 字符", text.length());
        return text;
    }

    @Override
    public boolean supports(String fileType) {
        return "txt".equals(fileType) || "md".equals(fileType);
    }
}
