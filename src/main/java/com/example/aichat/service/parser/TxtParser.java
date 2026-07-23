package com.example.aichat.service.parser;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** 纯文本/Markdown 解析器 */
@Component
public class TxtParser implements DocumentParser {

    @Override
    public String parse(Path filePath) throws IOException {
        return new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8);
    }

    @Override
    public boolean supports(String fileType) {
        return "txt".equals(fileType) || "md".equals(fileType);
    }
}
