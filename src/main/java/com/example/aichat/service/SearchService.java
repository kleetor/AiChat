package com.example.aichat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SearchService {

    @Value("${qianfan.api.key}")
    private String apiKey;

    @Value("${qianfan.api.url:https://qianfan.baidubce.com/v2/ai_search/web_search}")
    private String apiUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public SearchService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> search(String query, boolean summary, String freshness, int count) {
        ObjectNode requestBody = objectMapper.createObjectNode();
        
        ArrayNode messagesArray = objectMapper.createArrayNode();
        ObjectNode message = objectMapper.createObjectNode();
        message.put("content", query);
        message.put("role", "user");
        messagesArray.add(message);
        requestBody.set("messages", messagesArray);
        
        requestBody.put("search_source", "baidu_search_v2");
        
        ArrayNode filterArray = objectMapper.createArrayNode();
        ObjectNode filter = objectMapper.createObjectNode();
        filter.put("type", "web");
        filter.put("top_k", count > 0 ? count : 10);
        filterArray.add(filter);
        requestBody.set("resource_type_filter", filterArray);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Appbuilder-Authorization", "Bearer " + apiKey);

        HttpEntity<String> entity = new HttpEntity<>(requestBody.toString(), headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    apiUrl,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode resultList = root.get("references");

            List<Map<String, Object>> searchResults = new ArrayList<>();
            if (resultList != null && resultList.isArray()) {
                for (JsonNode result : resultList) {
                    Map<String, Object> item = new HashMap<>();
                    if (result.has("title")) item.put("title", result.get("title").asText());
                    if (result.has("url")) item.put("url", result.get("url").asText());
                    if (result.has("snippet")) item.put("summary", result.get("snippet").asText());
                    if (result.has("website")) item.put("siteName", result.get("website").asText());
                    if (result.has("date")) item.put("publishTime", result.get("date").asText());
                    searchResults.add(item);
                }
            }
            return searchResults;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("搜索失败：" + e.getMessage());
        }
    }

    public String searchAsMarkdown(String query, int count) {
        List<Map<String, Object>> results = search(query, true, "noLimit", count);
        
        StringBuilder markdown = new StringBuilder();
        markdown.append("## 搜索结果\n\n");
        
        for (int i = 0; i < results.size(); i++) {
            Map<String, Object> result = results.get(i);
            markdown.append(i + 1).append(". **").append(result.get("title")).append("**\n");
            markdown.append("   - 来源：").append(result.get("siteName")).append("\n");
            if (result.containsKey("publishTime")) {
                markdown.append("   - 时间：").append(result.get("publishTime")).append("\n");
            }
            if (result.containsKey("summary")) {
                markdown.append("   - 摘要：").append(result.get("summary")).append("\n");
            }
            markdown.append("   - 链接：").append(result.get("url")).append("\n\n");
        }
        
        if (results.isEmpty()) {
            markdown.append("未找到相关结果。\n");
        }
        
        return markdown.toString();
    }
}