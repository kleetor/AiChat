package com.example.aichat.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

/**
 * ChromaDB 记忆操作服务。
 * Collection 命名规则：mem_{userId}
 */
@Service
public class MemoryChromaService extends BaseChromaDBService<Long> {

    public MemoryChromaService(RestTemplate restTemplate,
                               SiliconFlowEmbeddingService embeddingService,
                               @Value("${chromadb.url}") String chromaUrl) {
        super(restTemplate, embeddingService, chromaUrl);
    }

    @Override
    protected String collectionName(Long userId) {
        return "mem_" + userId;
    }

    // ==================== Collection 管理 ====================

    /** 懒创建用户记忆 Collection */
    public void ensureCollection(Long userId) {
        uuidCache.computeIfAbsent(userId, id -> {
            String name = collectionName(id);
            try {
                String url = buildUrl("/collections");
                var headers = new org.springframework.http.HttpHeaders();
                headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
                var request = new org.springframework.http.HttpEntity<>(
                        Map.of("name", name), headers);
                var resp = restTemplate.postForEntity(url, request, Map.class);
                @SuppressWarnings("unchecked")
                String uuid = (String) resp.getBody().get("id");
                log.info("记忆 Collection 创建: name={}, uuid={}", name, uuid);
                return uuid;
            } catch (Exception e) {
                log.error("创建记忆 Collection 失败: {}", name, e);
                throw new RuntimeException("创建记忆 Collection 失败", e);
            }
        });
    }

    // ==================== 记忆 CRUD ====================

    /** 添加一条记忆，返回 chroma_id */
    public String addMemory(Long userId, String text, Map<String, String> metadata) {
        ensureCollection(userId);
        String uuid = getCollectionUuid(userId);
        if (uuid == null) throw new RuntimeException("Collection 不存在: userId=" + userId);

        try {
            List<Double> embedding = embeddingService.embed(text);
            String docId = "mem_" + System.currentTimeMillis() + "_" + Thread.currentThread().getId();

            add(uuid,
                List.of(docId),
                List.of(embedding),
                List.of(text),
                List.of(metadata != null ? new HashMap<>(metadata) : Map.of()));

            log.debug("记忆写入 ChromaDB: userId={}, chromaId={}", userId, docId);
            return docId;
        } catch (Exception e) {
            log.error("记忆写入 ChromaDB 失败: userId={}", userId, e);
            throw new RuntimeException("记忆写入 ChromaDB 失败", e);
        }
    }

    /** 语义搜索 */
    public List<MemoryHit> search(Long userId, String query, int topK) {
        String uuid = getCollectionUuid(userId);
        if (uuid == null) return List.of();

        try {
            List<Double> queryEmbedding = embeddingService.embed(query);
            Map<String, Object> raw = queryRaw(uuid, queryEmbedding, topK);
            return parseSearchResult(raw);
        } catch (Exception e) {
            log.warn("记忆搜索失败: userId={}", userId, e);
            return List.of();
        }
    }

    /** 更新记忆文本 (删旧加新，因 ChromaDB 不支持直接 update) */
    public void updateMemory(Long userId, String chromaId, String newText) {
        String uuid = getCollectionUuid(userId);
        if (uuid == null) return;

        try {
            // 1. 获取旧文档的 metadata
            Map<String, String> oldMeta = Map.of();
            try {
                Map<String, Object> getResp = getByIds(uuid, List.of(chromaId));
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> metaList =
                    (List<Map<String, Object>>) getResp.getOrDefault("metadatas", List.of());
                if (!metaList.isEmpty() && metaList.get(0) != null) {
                    oldMeta = metaList.get(0).entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, e -> String.valueOf(e.getValue())));
                }
            } catch (Exception ignored) {
                // 旧文档可能已不存在，忽略
            }

            // 2. 删除旧文档
            deleteByIds(uuid, List.of(chromaId));

            // 3. 重新向量化并写入
            List<Double> embedding = embeddingService.embed(newText);
            add(uuid,
                List.of(chromaId),
                List.of(embedding),
                List.of(newText),
                List.of(new HashMap<>(oldMeta)));

            log.debug("记忆更新: userId={}, chromaId={}", userId, chromaId);
        } catch (Exception e) {
            log.error("记忆更新失败: userId={}, chromaId={}", userId, e);
        }
    }

    /** 删除单条记忆 */
    public void deleteMemory(Long userId, String chromaId) {
        String uuid = getCollectionUuid(userId);
        if (uuid == null) return;

        try {
            deleteByIds(uuid, List.of(chromaId));
            log.debug("记忆删除: userId={}, chromaId={}", userId, chromaId);
        } catch (Exception e) {
            log.warn("记忆删除失败: userId={}, chromaId={}", userId, e);
        }
    }

    /** 清空用户全部记忆 Collection */
    public void deleteAll(Long userId) {
        deleteCollection(userId);
    }

    // ==================== 内部类型 ====================

    @SuppressWarnings("unchecked")
    private List<MemoryHit> parseSearchResult(Map<String, Object> raw) {
        if (raw == null || !raw.containsKey("ids")) return List.of();
        List<List<String>> ids = (List<List<String>>) raw.get("ids");
        List<List<Double>> distances = (List<List<Double>>) raw.get("distances");
        List<List<String>> documents = (List<List<String>>) raw.get("documents");
        List<List<Map<String, Object>>> metadatas =
                (List<List<Map<String, Object>>>) raw.get("metadatas");

        if (ids == null || ids.isEmpty() || ids.get(0).isEmpty()) return List.of();

        List<String> idList = ids.get(0);
        List<Double> distList = distances != null ? distances.get(0) :
                Collections.nCopies(idList.size(), 0.0);
        List<String> docList = documents != null ? documents.get(0) :
                Collections.nCopies(idList.size(), "");
        List<Map<String, Object>> metaList = metadatas != null ? metadatas.get(0) :
                Collections.nCopies(idList.size(), Collections.emptyMap());

        List<MemoryHit> results = new ArrayList<>();
        for (int i = 0; i < idList.size(); i++) {
            Map<String, String> strMeta = new HashMap<>();
            if (metaList.get(i) != null) {
                metaList.get(i).forEach((k, v) -> strMeta.put(k, String.valueOf(v)));
            }
            results.add(new MemoryHit(idList.get(i), docList.get(i), 1.0 - distList.get(i), strMeta));
        }
        return results;
    }

    public record MemoryHit(String chromaId, String document, double score, Map<String, String> metadata) {}
}
