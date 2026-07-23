package com.example.aichat.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 查询重写服务：将用户口语化提问转为更适合文档检索的表述。
 * 使用 LLM 生成 2-3 个变体，与原查询一起并行检索。
 */
@Service
public class QueryRewriterService {

    private static final Logger log = LoggerFactory.getLogger(QueryRewriterService.class);

    private final LLMService llmService;

    private static final String REWRITE_PROMPT = """
            将以下用户问题改写为2-3个更适合在文档库中检索的表述。
            要求：
            - 使用正式、具体的词汇，避免口语化
            - 包含关键术语和同义词
            - 每个表述一行，不要编号
            - 只输出改写结果，不要解释

            用户问题：%s
            """;

    public QueryRewriterService(LLMService llmService) {
        this.llmService = llmService;
    }

    /**
     * 改写查询，返回原始查询 + 改写变体。
     * 失败时返回仅包含原始查询的列表。
     */
    public List<String> rewrite(String originalQuery) {
        try {
            String prompt = String.format(REWRITE_PROMPT, originalQuery);
            String response = llmService.chatSync(prompt);

            List<String> variants = new ArrayList<>();
            variants.add(originalQuery); // 保留原始查询

            // 解析 LLM 返回的行
            for (String line : response.split("\n")) {
                String trimmed = line.trim();
                // 去除可能的编号前缀
                trimmed = trimmed.replaceFirst("^\\d+[\\.、)\\s]+", "").trim();
                if (!trimmed.isBlank() && !trimmed.equals(originalQuery) && variants.size() < 4) {
                    variants.add(trimmed);
                }
            }

            log.debug("查询重写: '{}' → {}", originalQuery, variants);
            return variants;
        } catch (Exception e) {
            log.warn("查询重写失败，使用原始查询: '{}'", originalQuery, e);
            return List.of(originalQuery);
        }
    }
}
