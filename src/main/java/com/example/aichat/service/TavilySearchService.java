package com.example.aichat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class TavilySearchService {

    private static final Logger logger = LoggerFactory.getLogger(TavilySearchService.class);

    @Value("${tavily.api.key}")
    private String apiKey;

    @Value("${tavily.api.url:https://api.tavily.com/search}")
    private String apiUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public TavilySearchService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 执行 Tavily 搜索
     * @param query     搜索查询词
     * @param maxResults 最大返回结果数 (1-20)
     * @param searchDepth 搜索深度: basic / advanced
     * @param includeAnswer 是否包含 LLM 生成的答案
     */
    public List<Map<String, Object>> search(String query, int maxResults, String searchDepth, boolean includeAnswer) {
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("query", query);
        requestBody.put("search_depth", searchDepth != null ? searchDepth : "basic");
        requestBody.put("max_results", Math.max(1, Math.min(maxResults, 20)));
        requestBody.put("include_answer", includeAnswer);
        requestBody.put("include_raw_content", false);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);

        HttpEntity<String> entity = new HttpEntity<>(requestBody.toString(), headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    apiUrl,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode resultList = root.get("results");

            List<Map<String, Object>> searchResults = new ArrayList<>();
            if (resultList != null && resultList.isArray()) {
                for (JsonNode result : resultList) {
                    Map<String, Object> item = new HashMap<>();
                    if (result.has("title")) item.put("title", result.get("title").asText());
                    if (result.has("url")) item.put("url", result.get("url").asText());
                    if (result.has("content")) item.put("content", result.get("content").asText());
                    if (result.has("score")) item.put("score", result.get("score").asDouble());
                    if (result.has("published_date")) item.put("publishedDate", result.get("published_date").asText());
                    searchResults.add(item);
                }
            }

            // 如果有 LLM 生成的答案，也加入结果
            if (root.has("answer") && !root.get("answer").isNull()) {
                Map<String, Object> answerItem = new HashMap<>();
                answerItem.put("type", "answer");
                answerItem.put("content", root.get("answer").asText());
                searchResults.add(0, answerItem);
            }

            return searchResults;
        } catch (Exception e) {
            logger.error("Tavily 搜索失败: {}", e.getMessage());
            throw new RuntimeException("Tavily 搜索失败：" + e.getMessage());
        }
    }

    /**
     * 搜索并返回 Markdown 格式结果（供 ChatService 注入 AI 上下文）
     * @param query 搜索查询词
     * @param maxResults 最大结果数
     */
    public String searchAsMarkdown(String query, int maxResults) {
        List<Map<String, Object>> results = search(query, maxResults, "basic", false);

        StringBuilder markdown = new StringBuilder();
        markdown.append("## Tavily 搜索结果\n\n");

        int count = 0;
        for (Map<String, Object> result : results) {
            // 跳过 LLM 答案类型的条目（其 content 可能很长）
            if ("answer".equals(result.get("type"))) {
                continue;
            }
            count++;
            markdown.append(count).append(". **").append(result.get("title")).append("**\n");
            if (result.containsKey("publishedDate")) {
                markdown.append("   - 时间：").append(result.get("publishedDate")).append("\n");
            }
            if (result.containsKey("content")) {
                String content = (String) result.get("content");
                // 截取摘要，避免上下文过长
                if (content.length() > 300) {
                    content = content.substring(0, 300) + "…";
                }
                markdown.append("   - 摘要：").append(content).append("\n");
            }
            markdown.append("   - 链接：").append(result.get("url")).append("\n\n");
        }

        if (count == 0) {
            markdown.append("未找到相关结果。\n");
        }

        return markdown.toString();
    }
}
