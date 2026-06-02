package com.example.aichat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    @Value("${bocha.api.key}")
    private String apiKey;

    @Value("${bocha.api.url:https://api.bocha.cn/v1/web-search}")
    private String apiUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public SearchService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> search(String query, boolean summary, String freshness, int count) {
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("query", query);
        requestBody.put("summary", summary);
        requestBody.put("freshness", freshness != null ? freshness : "noLimit");
        requestBody.put("count", count > 0 ? count : 10);

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
            JsonNode data = root.get("data");
            JsonNode webPages = data != null ? data.get("webPages") : null;
            JsonNode results = webPages != null ? webPages.get("value") : null;

            List<Map<String, Object>> searchResults = new ArrayList<>();
            if (results != null && results.isArray()) {
                for (JsonNode result : results) {
                    Map<String, Object> item = new HashMap<>();
                    if (result.has("name")) item.put("title", result.get("name").asText());
                    if (result.has("url")) item.put("url", result.get("url").asText());
                    if (result.has("summary")) item.put("summary", result.get("summary").asText());
                    if (result.has("siteName")) item.put("siteName", result.get("siteName").asText());
                    if (result.has("datePublished")) item.put("publishTime", result.get("datePublished").asText());
                    if (result.has("thumbnailUrl")) item.put("imageUrl", result.get("thumbnailUrl").asText());
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