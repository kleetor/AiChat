package com.example.aichat.service.tool;

import com.example.aichat.service.SearchService;
import com.example.aichat.service.TavilySearchService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * search_web 工具实现 —— Tavily + 百度千帆双引擎并发竞速。
 * <p>
 * 每个搜索查询同时发起 Tavily 和千帆两个请求，取先返回的结果，
 * 消除传统"先 A 再降级 B"的串行等待延迟。
 */
@Component
public class SearchWebTool implements ToolHandler {

    private static final Logger logger = LoggerFactory.getLogger(SearchWebTool.class);
    private static final int MAX_QUERY_LENGTH = 500;
    private static final int MAX_RESULTS = 5;
    private static final int SUMMARY_MAX_LENGTH = 2000;
    private static final long ENGINE_RACE_TIMEOUT_SECONDS = 30;

    private final TavilySearchService tavily;
    private final SearchService qianfan;
    private final ObjectMapper objectMapper;

    public SearchWebTool(TavilySearchService tavily, SearchService qianfan,
                          ObjectMapper objectMapper) {
        this.tavily = tavily;
        this.qianfan = qianfan;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "search_web";
    }

    @Override
    public ToolDefinition getDefinition() {
        ObjectNode paramsNode = objectMapper.createObjectNode();
        paramsNode.put("type", "object");

        ObjectNode propertiesNode = objectMapper.createObjectNode();
        ObjectNode queryProp = objectMapper.createObjectNode();
        queryProp.put("type", "string");
        queryProp.put("description",
                "搜索关键词，应提炼用户问题中的核心信息。尽量包含时间、地点等限定词以提高搜索准确性。" +
                "例如：'2026年7月 重庆 天气'、'凡人修仙传 最新集数 2026年7月'");
        propertiesNode.set("query", queryProp);
        paramsNode.set("properties", propertiesNode);

        com.fasterxml.jackson.databind.node.ArrayNode required = objectMapper.createArrayNode();
        required.add("query");
        paramsNode.set("required", required);

        return new ToolDefinition(
                "search_web",
                "搜索互联网获取实时信息。当用户询问以下内容时应调用此工具：\n" +
                "- 新闻、热点、时事\n- 天气、股价、汇率等实时数据\n- 最新动态、事件进展\n" +
                "- 任何需要最新信息才能准确回答的问题\n\n" +
                "不要在你已知的常识性问题上调用此工具。",
                paramsNode
        );
    }

    @Override
    public ToolResult execute(ToolCall call) {
        String query;
        try {
            JsonNode args = objectMapper.readTree(call.getArguments());
            JsonNode queryNode = args.get("query");
            if (queryNode == null || queryNode.asText().isBlank()) {
                return new ToolResult(call.getId(), name(), "搜索失败：未提供查询关键词");
            }
            query = queryNode.asText();
        } catch (JsonProcessingException e) {
            logger.warn("解析 search_web arguments 失败: {}", call.getArguments());
            return new ToolResult(call.getId(), name(), "搜索失败：参数解析错误");
        }

        // 长度限制，防止恶意超长查询
        if (query.length() > MAX_QUERY_LENGTH) {
            query = query.substring(0, MAX_QUERY_LENGTH);
        }

        logger.info("search_web query={}", query);

        // 双引擎降级
        String md = searchWithFallback(query);
        return new ToolResult(call.getId(), name(), md);
    }

    /**
     * 双引擎并发竞速：同时发起 Tavily 和千帆请求，取先返回的结果。
     * 若一方失败，等待另一方（两者均已发起，无需串行重试）。
     */
    private String searchWithFallback(String query) {
        CompletableFuture<String> tavilyFuture = CompletableFuture.supplyAsync(() -> {
            long start = System.currentTimeMillis();
            String md = tavily.searchAsMarkdown(query, MAX_RESULTS);
            logger.info("Tavily 搜索完成 query={} 耗时={}ms", query, System.currentTimeMillis() - start);
            return md;
        });

        CompletableFuture<String> qianfanFuture = CompletableFuture.supplyAsync(() -> {
            long start = System.currentTimeMillis();
            String md = qianfan.searchAsMarkdown(query, MAX_RESULTS);
            logger.info("千帆搜索完成 query={} 耗时={}ms", query, System.currentTimeMillis() - start);
            return md;
        });

        // 竞速：先返回的先采用
        for (int i = 0; i < 2; i++) {
            try {
                Object result = CompletableFuture
                        .anyOf(tavilyFuture, qianfanFuture)
                        .get(ENGINE_RACE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                logger.info("双引擎竞速 winner={} query={}",
                        tavilyFuture.isDone() ? "Tavily" : "千帆", query);
                return truncate((String) result, SUMMARY_MAX_LENGTH);
            } catch (java.util.concurrent.TimeoutException e) {
                logger.warn("双引擎竞速超时 ({}s)，检查另一方...", ENGINE_RACE_TIMEOUT_SECONDS);
            } catch (Exception e) {
                // 一方异常，检查另一方是否已完成
                logger.debug("竞速中一方异常: {}", e.getMessage());
            }

            // 第一轮失败/超时 → 检查另一方
            if (tavilyFuture.isDone() && !tavilyFuture.isCompletedExceptionally()) {
                try {
                    return truncate(tavilyFuture.get(), SUMMARY_MAX_LENGTH);
                } catch (Exception ignored) { }
            }
            if (qianfanFuture.isDone() && !qianfanFuture.isCompletedExceptionally()) {
                try {
                    return truncate(qianfanFuture.get(), SUMMARY_MAX_LENGTH);
                } catch (Exception ignored) { }
            }

            // 两方都没完成 → 等待剩余
            try {
                Object result = CompletableFuture
                        .anyOf(tavilyFuture, qianfanFuture)
                        .get(ENGINE_RACE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                return truncate((String) result, SUMMARY_MAX_LENGTH);
            } catch (Exception ignored) { }
        }

        // 双方全部失败
        logger.error("双引擎搜索全部失败 query={}", query);
        return "搜索暂时不可用，请稍后重试。";
    }

    private String truncate(String text, int maxLen) {
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "\n\n（搜索结果过长，已截断）";
    }
}
