package com.example.aichat.service.ocr;

import org.springframework.stereotype.Component;

/**
 * OCR 结果后处理：空格规范化 + 段落合并。
 * 注意：不含字典纠错——LLM 在回答阶段具备更强的上下文纠错能力。
 */
@Component
public class OcrPostProcessor {

    public String postProcess(String rawText) {
        if (rawText == null || rawText.isBlank()) return "";
        String result = normalizeSpaces(rawText);
        result = mergeParagraphs(result);
        return result.trim();
    }

    /**
     * 空格规范化：
     *   中文间去除空格、中英文间加半角空格、多个空格合并。
     */
    String normalizeSpaces(String text) {
        // CJK 后跟英文 → 加空格
        text = text.replaceAll("([\\u4e00-\\u9fff\\u3400-\\u4dbf\\uf900-\\ufaff])([a-zA-Z])", "$1 $2");
        // 英文后跟 CJK → 加空格
        text = text.replaceAll("([a-zA-Z])([\\u4e00-\\u9fff\\u3400-\\u4dbf\\uf900-\\ufaff])", "$1 $2");
        // CJK 间多余空格去除
        text = text.replaceAll("([\\u4e00-\\u9fff\\u3400-\\u4dbf\\uf900-\\ufaff])\\s+([\\u4e00-\\u9fff\\u3400-\\u4dbf\\uf900-\\ufaff])", "$1$2");
        // 多个空格合并
        text = text.replaceAll(" {2,}", " ");
        return text;
    }

    /**
     * 段落合并：OCR 产出的单行换行还原为段落。
     * 以句号/问号/感叹号结尾的行保留换行，其余行尾换行视为段落内换行（合并）。
     */
    String mergeParagraphs(String text) {
        return text.replaceAll("(?<![。！？.!?])\\n", "")
                   .replaceAll("\\n{3,}", "\n\n");
    }
}
