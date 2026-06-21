package com.example.aichat.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * ChromaDB 记忆操作层。
 * 与现有 ChromaDBService 并行（后者服务于 RAG 知识库 kb_{kbId}），
 * 本服务服务于用户记忆 mem_{userId}。
 */
@Service
public class MemoryChromaService {

    private static final Logger log = LoggerFactory.getLogger(MemoryChromaService.class);
    private static final String V2_BASE = "/api/v2";
    private static final String TENANT = "default_tenant";
    private static final String DATABASE = "default_database";

    private final RestTemplate restTemplate;
    private final SiliconFlowEmbeddingService embeddingService;
    private final String chromaUrl;

    /** userId → Collection UUID */
    private final ConcurrentHashMap<Long, String> uuidCache = new ConcurrentHashMap<>();

    public MemoryChromaService(RestTemplate restTemplate,
                               SiliconFlowEmbeddingService embeddingService,
                               @Value("${chromadb.url}") String chromaUrl) {
        this.restTemplate = restTemplate;
        this.embeddingService = embeddingService;
        this.chromaUrl = chromaUrl;
    }

    private String collectionName(Long userId) {
        return "mem_" + userId;
    }

    // ==================== Collection 管理 ====================

    /** 懒创建用户记忆 Collection */
    public void ensureCollection(Long userId) {
        uuidCache.computeIfAbsent(userId, id -> createCollectionInternal(id));
    }

    private String createCollectionInternal(Long userId) {
        String name = collectionName(userId);
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(
                    Map.of("name", name), headers);

            String url = String.format("%s%s/tenants/%s/databases/%s/collections",
                    chromaUrl, V2_BASE, TENANT, DATABASE);
            ResponseEntity<Map> resp = restTemplate.postForEntity(url, request, Map.class);

            @SuppressWarnings("unchecked")
            String uuid = (String) resp.getBody().get("id");
            log.info("记忆 Collection 创建: name={}, uuid={}", name, uuid);
            return uuid;
        } catch (Exception e) {
            log.error("创建记忆 Collection 失败: {}", name, e);
            throw new RuntimeException("创建记忆 Collection 失败", e);
        }
    }

    private String getCollectionUuid(Long userId) {
        return uuidCache.computeIfAbsent(userId, id -> {
            try {
                String name = collectionName(id);
                String url = String.format("%s%s/tenants/%s/databases/%s/collections/%s",
                        chromaUrl, V2_BASE, TENANT, DATABASE, name);
                ResponseEntity<Map> resp = restTemplate.getForEntity(url, Map.class);
                return (String) resp.getBody().get("id");
            } catch (Exception e) {
                log.warn("获取记忆 Collection UUID 失败: userId={}", id, e);
                return null;
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

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("ids", List.of(docId));
            body.put("embeddings", List.of(embedding));
            body.put("documents", List.of(text));
            body.put("metadatas", List.of(metadata != null ? new HashMap<>(metadata) : Map.of()));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            String url = String.format("%s%s/tenants/%s/databases/%s/collections/%s/add",
                    chromaUrl, V2_BASE, TENANT, DATABASE, uuid);
            restTemplate.postForEntity(url, request, String.class);

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

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("query_embeddings", List.of(queryEmbedding));
            body.put("n_results", topK);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            String url = String.format("%s%s/tenants/%s/databases/%s/collections/%s/query",
                    chromaUrl, V2_BASE, TENANT, DATABASE, uuid);
            ResponseEntity<Map> resp = restTemplate.postForEntity(url, request, Map.class);

            return parseSearchResult(resp.getBody());
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
                Map<String, Object> getBody = Map.of("ids", List.of(chromaId));
                HttpHeaders getHeaders = new HttpHeaders();
                getHeaders.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<Map<String, Object>> getReq = new HttpEntity<>(getBody, getHeaders);
                String getUrl = String.format("%s%s/tenants/%s/databases/%s/collections/%s/get",
                        chromaUrl, V2_BASE, TENANT, DATABASE, uuid);
                ResponseEntity<Map> getResp = restTemplate.postForEntity(getUrl, getReq, Map.class);
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> metaList =
                    (List<Map<String, Object>>) ((Map) getResp.getBody()).getOrDefault("metadatas", List.of());
                if (!metaList.isEmpty() && metaList.get(0) != null) {
                    oldMeta = metaList.get(0).entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, e -> String.valueOf(e.getValue())));
                }
            } catch (Exception ignored) {
                // 旧文档可能已不存在，忽略
            }

            // 2. 删除旧文档
            Map<String, Object> delBody = Map.of("ids", List.of(chromaId));
            HttpHeaders delHeaders = new HttpHeaders();
            delHeaders.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> delReq = new HttpEntity<>(delBody, delHeaders);
            String delUrl = String.format("%s%s/tenants/%s/databases/%s/collections/%s/delete",
                    chromaUrl, V2_BASE, TENANT, DATABASE, uuid);
            restTemplate.postForEntity(delUrl, delReq, String.class);

            // 3. 重新向量化并写入
            List<Double> embedding = embeddingService.embed(newText);

            Map<String, Object> addBody = new LinkedHashMap<>();
            addBody.put("ids", List.of(chromaId));
            addBody.put("embeddings", List.of(embedding));
            addBody.put("documents", List.of(newText));
            addBody.put("metadatas", List.of(new HashMap<>(oldMeta)));

            HttpHeaders addHeaders = new HttpHeaders();
            addHeaders.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> addReq = new HttpEntity<>(addBody, addHeaders);
            String addUrl = String.format("%s%s/tenants/%s/databases/%s/collections/%s/add",
                    chromaUrl, V2_BASE, TENANT, DATABASE, uuid);
            restTemplate.postForEntity(addUrl, addReq, String.class);

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
            Map<String, Object> body = Map.of("ids", List.of(chromaId));
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            String url = String.format("%s%s/tenants/%s/databases/%s/collections/%s/delete",
                    chromaUrl, V2_BASE, TENANT, DATABASE, uuid);
            restTemplate.postForEntity(url, request, String.class);

            log.debug("记忆删除: userId={}, chromaId={}", userId, chromaId);
        } catch (Exception e) {
            log.warn("记忆删除失败: userId={}, chromaId={}", userId, e);
        }
    }

    /** 清空用户全部记忆 Collection */
    public void deleteAll(Long userId) {
        String name = collectionName(userId);
        try {
            String url = String.format("%s%s/tenants/%s/databases/%s/collections/%s",
                    chromaUrl, V2_BASE, TENANT, DATABASE, name);
            restTemplate.delete(url);
            uuidCache.remove(userId);
            log.info("记忆 Collection 已删除: {}", name);
        } catch (Exception e) {
            log.warn("删除记忆 Collection 失败: {}", name, e);
        }
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
