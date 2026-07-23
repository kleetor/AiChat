package com.example.aichat.service.parser;

import java.io.IOException;
import java.nio.file.Path;

/**
 * 文档解析器统一接口。
 * 每种文件格式一个实现，统一返回纯文本供后续分块。
 */
public interface DocumentParser {

    /** 解析文件为纯文本 */
    String parse(Path filePath) throws IOException;

    /** 是否支持该文件类型 */
    boolean supports(String fileType);
}
