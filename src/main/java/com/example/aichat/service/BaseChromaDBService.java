package com.example.aichat.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ChromaDB V2 HTTP API 抽象基类。
 * 
 * 封装 ChromaDB REST API 的通用调用逻辑（Collection 管理、向量增删查），
 * 子类只需实现 {@link #collectionName(Object)} 提供各自的命名规则。
 *
 * @param <T> 标识类型（ChromaDBService 使用 Long kbId，MemoryChromaService 使用 Long userId）
 */
public abstract class BaseChromaDBService<T> {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected static final String V2_BASE = "/api/v2";
    protected static final String TENANT = "default_tenant";
    protected static final String DATABASE = "default_database";

    protected final RestTemplate restTemplate;
    protected final SiliconFlowEmbeddingService embeddingService;
    protected final String chromaUrl;

    /** id → Collection UUID 缓存 */
    protected final ConcurrentHashMap<T, String> uuidCache = new ConcurrentHashMap<>();

    protected BaseChromaDBService(RestTemplate restTemplate,
                                  SiliconFlowEmbeddingService embeddingService,
                                  String chromaUrl) {
        this.restTemplate = restTemplate;
        this.embeddingService = embeddingService;
        this.chromaUrl = chromaUrl;
    }

    /**
     * 子类实现：根据标识返回 Collection 名称
     */
    protected abstract String collectionName(T id);

    // ==================== Collection 管理 ====================

    /** 创建 Collection 并缓存 UUID */
    public void createCollection(T id) {
        String name = collectionName(id);
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(
                    Map.of("name", name), headers);

            String url = buildUrl("/collections");
            ResponseEntity<Map> resp = restTemplate.postForEntity(url, request, Map.class);

            @SuppressWarnings("unchecked")
            String uuid = (String) resp.getBody().get("id");
            uuidCache.put(id, uuid);
            log.info("ChromaDB Collection 创建成功: name={}, uuid={}", name, uuid);
        } catch (Exception e) {
            log.error("创建 ChromaDB Collection 失败: {}", name, e);
            throw new RuntimeException("创建 ChromaDB Collection 失败", e);
        }
    }

    /** 获取 Collection UUID（查缓存或 API） */
    protected String getCollectionUuid(T id) {
        return uuidCache.computeIfAbsent(id, key -> {
            try {
                String name = collectionName(key);
                String url = buildUrl("/collections/" + name);
                ResponseEntity<Map> resp = restTemplate.getForEntity(url, Map.class);
                return (String) resp.getBody().get("id");
            } catch (Exception e) {
                log.warn("获取 Collection UUID 失败: id={}", key, e);
                return null;
            }
        });
    }

    /** 删除 Collection */
    public void deleteCollection(T id) {
        String name = collectionName(id);
        try {
            String url = buildUrl("/collections/" + name);
            restTemplate.delete(url);
            uuidCache.remove(id);
            log.info("ChromaDB Collection 删除成功: {}", name);
        } catch (Exception e) {
            log.warn("删除 ChromaDB Collection 失败: {}", name, e);
        }
    }

    // ==================== 向量操作 ====================

    /** 添加向量（批量） */
    protected void add(String collectionUuid, List<String> ids,
                       List<List<Double>> embeddings, List<String> documents,
                       List<Map<String, Object>> metadatas) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ids", ids);
        body.put("embeddings", embeddings);
        body.put("documents", documents);
        body.put("metadatas", metadatas);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        String url = buildUrl("/collections/" + collectionUuid + "/add");
        restTemplate.postForEntity(url, request, String.class);
    }

    /** 语义检索 */
    protected Map<String, Object> queryRaw(String collectionUuid, List<Double> queryEmbedding, int topK) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query_embeddings", List.of(queryEmbedding));
        body.put("n_results", topK);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        String url = buildUrl("/collections/" + collectionUuid + "/query");
        ResponseEntity<Map> resp = restTemplate.postForEntity(url, request, Map.class);
        return resp.getBody();
    }

    /** 按 ID 删除向量 */
    protected void deleteByIds(String collectionUuid, List<String> ids) {
        Map<String, Object> body = Map.of("ids", ids);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        String url = buildUrl("/collections/" + collectionUuid + "/delete");
        restTemplate.postForEntity(url, request, String.class);
    }

    /** 按条件删除向量 */
    protected void deleteByWhere(String collectionUuid, Map<String, Object> where) {
        Map<String, Object> body = Map.of("where", where);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        String url = buildUrl("/collections/" + collectionUuid + "/delete");
        restTemplate.postForEntity(url, request, String.class);
    }

    /** 按 ID 获取文档 */
    @SuppressWarnings("unchecked")
    protected Map<String, Object> getByIds(String collectionUuid, List<String> ids) {
        Map<String, Object> body = Map.of("ids", ids);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        String url = buildUrl("/collections/" + collectionUuid + "/get");
        ResponseEntity<Map> resp = restTemplate.postForEntity(url, request, Map.class);
        return resp.getBody();
    }

    // ==================== URL 构建 ====================

    protected String buildUrl(String path) {
        return String.format("%s%s/tenants/%s/databases/%s%s",
                chromaUrl, V2_BASE, TENANT, DATABASE, path);
    }
}
