package com.example.aichat.service;

import com.example.aichat.config.ChromaDBConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class SiliconFlowEmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(SiliconFlowEmbeddingService.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ChromaDBConfig config;

    public SiliconFlowEmbeddingService(RestTemplate restTemplate, ObjectMapper objectMapper,
                                        ChromaDBConfig config) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.config = config;
    }

    /**
     * 单条文本向量化
     */
    public List<Double> embed(String text) {
        return embedBatch(List.of(text)).get(0);
    }

    /**
     * 批量文本向量化
     */
    public List<List<Double>> embedBatch(List<String> texts) {
        try {
            Map<String, Object> body = Map.of(
                    "model", config.getEmbeddingModel(),
                    "input", texts,
                    "encoding_format", "float"
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(config.getEmbeddingApiKey());

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<Map> resp = restTemplate.postForEntity(
                    config.getEmbeddingApiUrl(), request, Map.class);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = (List<Map<String, Object>>) resp.getBody().get("data");

            return data.stream()
                    .map(item -> {
                        @SuppressWarnings("unchecked")
                        List<Double> emb = (List<Double>) item.get("embedding");
                        return emb;
                    })
                    .toList();
        } catch (Exception e) {
            log.error("嵌入向量化失败: {}", e.getMessage(), e);
            throw new RuntimeException("嵌入向量化失败: " + e.getMessage(), e);
        }
    }
}
