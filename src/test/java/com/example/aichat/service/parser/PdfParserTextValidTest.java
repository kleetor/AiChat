package com.example.aichat.service.parser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PdfParser.isTextValid() 文本有效性判断测试。
 */
class PdfParserTextValidTest {

    @Test
    @DisplayName("null / blank → false")
    void nullOrBlankReturnsFalse() throws Exception {
        Method m = PdfParser.class.getDeclaredMethod("isTextValid", String.class);
        m.setAccessible(true);
        assertFalse((boolean) m.invoke(null, (String) null));
        assertFalse((boolean) m.invoke(null, ""));
        assertFalse((boolean) m.invoke(null, "   \n\t"));
    }

    @Test
    @DisplayName("纯中文 ≥ 10 个字符 → true")
    void pureCjkValid() throws Exception {
        Method m = PdfParser.class.getDeclaredMethod("isTextValid", String.class);
        m.setAccessible(true);
        assertTrue((boolean) m.invoke(null, "这是一段有效的中文测试文本内容"));
    }

    @Test
    @DisplayName("中文 < 10 个字符 → false")
    void tooFewCjkInvalid() throws Exception {
        Method m = PdfParser.class.getDeclaredMethod("isTextValid", String.class);
        m.setAccessible(true);
        assertFalse((boolean) m.invoke(null, "短文本"));
    }

    @Test
    @DisplayName("纯英文有效文本 → true")
    void pureEnglishValid() throws Exception {
        Method m = PdfParser.class.getDeclaredMethod("isTextValid", String.class);
        m.setAccessible(true);
        assertTrue((boolean) m.invoke(null, "This is valid English text with enough characters"));
    }

    @Test
    @DisplayName("混入大量乱码 → false（有效字符占比 < 30%）")
    void mostlyGarbledReturnsFalse() throws Exception {
        Method m = PdfParser.class.getDeclaredMethod("isTextValid", String.class);
        m.setAccessible(true);
        String garbled = "@#$%^&*()   @@@  !!!  ~~~  ``  >>> 中文小段";
        assertFalse((boolean) m.invoke(null, garbled));
    }

    @Test
    @DisplayName("空格为主夹少量文字 → false")
    void mostlyWhitespaceReturnsFalse() throws Exception {
        Method m = PdfParser.class.getDeclaredMethod("isTextValid", String.class);
        m.setAccessible(true);
        // 仅 3 个有效字符，不足 10
        String text = "                                       hi !     ";
        assertFalse((boolean) m.invoke(null, text));
    }

    @Test
    @DisplayName("中英混合正常文本 → true")
    void mixedCjkEnglishValid() throws Exception {
        Method m = PdfParser.class.getDeclaredMethod("isTextValid", String.class);
        m.setAccessible(true);
        assertTrue((boolean) m.invoke(null, "使用 Java 开发 Spring Boot 应用程序的完整指南"));
    }
}
