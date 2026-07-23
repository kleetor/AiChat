package com.example.aichat.service.ocr;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OcrPostProcessor 后处理逻辑测试。
 */
class OcrPostProcessorTest {

    private final OcrPostProcessor processor = new OcrPostProcessor();

    @Test
    @DisplayName("中文间多余空格 → 去除")
    void removeSpacesBetweenCjk() {
        // 两个 CJK 词之间有空格 → 去除
        String input = "你好 世界";
        String result = processor.normalizeSpaces(input);
        assertEquals("你好世界", result);
    }

    @Test
    @DisplayName("中英文交界 → 加半角空格")
    void addSpaceBetweenCjkAndEnglish() {
        String input = "使用Java开发SpringBoot应用";
        String result = processor.normalizeSpaces(input);
        assertTrue(result.contains("使用 Java 开发 SpringBoot 应用") || result.contains("使用 Java 开发 Spring Boot 应用"));
    }

    @Test
    @DisplayName("英文后中文 → 加空格")
    void addSpaceBetweenEnglishAndCjk() {
        String input = "ApachePOI解析文档";
        String result = processor.normalizeSpaces(input);
        // 英文后接中文处加空格：POI 解析文档
        assertTrue(result.contains("POI 解析文档"));
    }

    @Test
    @DisplayName("多个连续空格 → 合并为一个")
    void mergeMultipleSpaces() {
        String input = "hello    world    test";
        String result = processor.normalizeSpaces(input);
        assertEquals("hello world test", result);
    }

    @Test
    @DisplayName("非段落结尾的行尾换行 → 合并")
    void mergeNonParagraphLineBreaks() {
        String input = "第一行继续\n第二行继续\n第三行结束。\n第四行结束。";
        String result = processor.mergeParagraphs(input);
        // 前两行无句号，合并；后两行有句号，保留换行
        assertTrue(result.contains("第一行继续第二行继续第三行结束"));
        assertTrue(result.contains("。\n第四行结束。"));
    }

    @Test
    @DisplayName("句号/问号/感叹号结尾 → 保留换行")
    void keepParagraphBreaks() {
        String input = "问题一。\n问题二！\n问题三？\n问题四。";
        String result = processor.mergeParagraphs(input);
        assertTrue(result.contains("。\n问题二"));
        assertTrue(result.contains("！\n问题三"));
        assertTrue(result.contains("？\n问题四"));
    }

    @Test
    @DisplayName("多个空行 → 合并为段落分隔")
    void mergeMultipleBlankLines() {
        // 句号结尾的行之间多个空行 → 合并为单换行
        String input = "段落一。\n\n\n\n段落二。";
        String result = processor.mergeParagraphs(input);
        // 句号后保留换行，多余空行被去除
        assertTrue(result.contains("段落一。\n段落二。") || result.contains("段落一。\n\n段落二。"));
    }

    @Test
    @DisplayName("null / 空字符串 → 返回空字符串")
    void nullOrBlankReturnsEmpty() {
        assertEquals("", processor.postProcess(null));
        assertEquals("", processor.postProcess(""));
        assertEquals("", processor.postProcess("   "));
    }

    @Test
    @DisplayName("正常文本 → 完整后处理管线")
    void fullPipeline() {
        String input = "使 用  Java   开发\nSpringBoot  应用。\n\n\n部署 到  Docker。";
        String result = processor.postProcess(input);
        assertFalse(result.contains("  "), "不应有连续空格");
        assertTrue(result.contains("应用"), "应保留核心内容");
        assertDoesNotThrow(() -> processor.postProcess(input));
    }
}
