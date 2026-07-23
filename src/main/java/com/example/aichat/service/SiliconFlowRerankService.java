package com.example.aichat.service;

import com.example.aichat.config.props.RerankProperties;
import com.example.aichat.model.MemoryItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.IntStream;

/**
 * SiliconFlow Rerank API 封装 — Cross-Encoder 精排。
 * 对粗排候选列表进行逐条精读打分，返回按 relevance_score 降序的结果。
 */
@Service
public class SiliconFlowRerankService {

    private static final Logger log = LoggerFactory.getLogger(SiliconFlowRerankService.class);

    private final RestTemplate restTemplate;
    private final RerankProperties props;
    private final ObjectMapper objectMapper;

    public SiliconFlowRerankService(RestTemplate restTemplate, RerankProperties props,
                                     ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    /**
     * 对候选记忆做 Cross-Encoder 精排，返回 topN 的 (itemId, score)。
     */
    public List<ScoredItem> rerank(String query, List<MemoryItem> candidates, int topN) {
        if (candidates.isEmpty()) return List.of();

        try {
            List<String> documents = candidates.stream()
                    .map(MemoryItem::getValue)
                    .toList();

            Map<String, Object> body = Map.of(
                    "model", props.getModel(),
                    "query", query,
                    "documents", documents,
                    "top_n", Math.min(topN, documents.size())
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(props.getApiKey());

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<Map> resp = restTemplate.postForEntity(
                    props.getApiUrl(), request, Map.class);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> results = (List<Map<String, Object>>) resp.getBody().get("results");
            if (results == null || results.isEmpty()) return List.of();

            return results.stream()
                    .map(r -> {
                        int idx = ((Number) r.get("index")).intValue();
                        double score = ((Number) r.get("relevance_score")).doubleValue();
                        Long itemId = candidates.get(idx).getId();
                        return new ScoredItem(itemId, score);
                    })
                    .sorted(Comparator.comparingDouble(ScoredItem::score).reversed())
                    .toList();

        } catch (Exception e) {
            log.warn("Rerank API 调用失败，降级为原始排序: {}", e.getMessage());
            // 降级：返回原顺序
            return candidates.stream()
                    .map(m -> new ScoredItem(m.getId(), 1.0))
                    .toList();
        }
    }

    /**
     * 通用文本精排：对候选文档列表做 Cross-Encoder 打分，返回 (index, score)。
     * 供知识库等非 MemoryItem 场景使用。
     */
    public List<RerankTextResult> rerankTexts(String query, List<String> documents, int topN) {
        if (documents.isEmpty()) return List.of();

        try {
            Map<String, Object> body = Map.of(
                    "model", props.getModel(),
                    "query", query,
                    "documents", documents,
                    "top_n", Math.min(topN, documents.size())
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(props.getApiKey());

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<Map> resp = restTemplate.postForEntity(
                    props.getApiUrl(), request, Map.class);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> results = (List<Map<String, Object>>) resp.getBody().get("results");
            if (results == null || results.isEmpty()) return List.of();

            return results.stream()
                    .map(r -> {
                        int idx = ((Number) r.get("index")).intValue();
                        double score = ((Number) r.get("relevance_score")).doubleValue();
                        return new RerankTextResult(idx, score);
                    })
                    .sorted(Comparator.comparingDouble(RerankTextResult::score).reversed())
                    .toList();

        } catch (Exception e) {
            log.warn("Rerank API 调用失败，降级为原始排序: {}", e.getMessage());
            return IntStream.range(0, Math.min(topN, documents.size()))
                    .mapToObj(i -> new RerankTextResult(i, 1.0))
                    .toList();
        }
    }

    public record ScoredItem(Long itemId, double score) {}
    public record RerankTextResult(int index, double score) {}
}
