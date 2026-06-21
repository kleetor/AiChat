package com.example.aichat.service;

import com.example.aichat.config.ChromaDBConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ChromaDB V2 HTTP API 封装。
 * add/query/delete 操作需要 Collection UUID，内部自动缓存。
 */
@Service
public class ChromaDBService {

    private static final Logger log = LoggerFactory.getLogger(ChromaDBService.class);

    private static final String V2_BASE = "/api/v2";
    private static final String TENANT = "default_tenant";
    private static final String DATABASE = "default_database";

    private final RestTemplate restTemplate;
    private final SiliconFlowEmbeddingService embeddingService;
    private final String chromaUrl;

    /** kbId → Collection UUID 缓存 */
    private final ConcurrentHashMap<Long, String> uuidCache = new ConcurrentHashMap<>();

    public ChromaDBService(RestTemplate restTemplate,
                            SiliconFlowEmbeddingService embeddingService,
                            ChromaDBConfig config) {
        this.restTemplate = restTemplate;
        this.embeddingService = embeddingService;
        this.chromaUrl = config.getChromaUrl();
    }

    private String collectionName(Long kbId) {
        return "kb_" + kbId;
    }

    /** 创建 Collection 并缓存 UUID */
    public void createCollection(Long kbId) {
        String name = collectionName(kbId);
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
            uuidCache.put(kbId, uuid);
            log.info("ChromaDB Collection 创建成功: name={}, uuid={}", name, uuid);
        } catch (Exception e) {
            log.error("创建 ChromaDB Collection 失败: {}", name, e);
            throw new RuntimeException("创建 ChromaDB Collection 失败", e);
        }
    }

    /** 获取 Collection UUID（查缓存或 API） */
    @SuppressWarnings("unchecked")
    private String getCollectionUuid(Long kbId) {
        return uuidCache.computeIfAbsent(kbId, id -> {
            try {
                String name = collectionName(id);
                String url = String.format("%s%s/tenants/%s/databases/%s/collections/%s",
                        chromaUrl, V2_BASE, TENANT, DATABASE, name);
                ResponseEntity<Map> resp = restTemplate.getForEntity(url, Map.class);
                return (String) resp.getBody().get("id");
            } catch (Exception e) {
                log.error("获取 Collection UUID 失败: kbId={}", id, e);
                return null;
            }
        });
    }

    /** 删除 Collection */
    public void deleteCollection(Long kbId) {
        String name = collectionName(kbId);
        try {
            String url = String.format("%s%s/tenants/%s/databases/%s/collections/%s",
                    chromaUrl, V2_BASE, TENANT, DATABASE, name);
            restTemplate.delete(url);
            uuidCache.remove(kbId);
            log.info("ChromaDB Collection 删除成功: {}", name);
        } catch (Exception e) {
            log.warn("删除 ChromaDB Collection 失败: {}", name, e);
        }
    }

    /** 添加文档分块 */
    public void addChunks(Long kbId, List<ChunkData> chunks) {
        if (chunks == null || chunks.isEmpty()) return;
        String uuid = getCollectionUuid(kbId);
        if (uuid == null) throw new RuntimeException("Collection 不存在: kbId=" + kbId);

        try {
            List<String> texts = chunks.stream().map(ChunkData::content).toList();
            List<List<Double>> allEmbeddings = embeddingService.embedBatch(texts);

            List<String> ids = new ArrayList<>();
            List<Map<String, Object>> metadatas = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                ChunkData c = chunks.get(i);
                ids.add("doc_" + c.documentId + "_chunk_" + c.chunkIndex);
                metadatas.add(Map.of(
                        "document_id", String.valueOf(c.documentId),
                        "chunk_index", String.valueOf(c.chunkIndex),
                        "kb_id", String.valueOf(kbId),
                        "file_name", c.fileName != null ? c.fileName : ""
                ));
            }

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("ids", ids);
            body.put("embeddings", allEmbeddings);
            body.put("documents", texts);
            body.put("metadatas", metadatas);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            String url = String.format("%s%s/tenants/%s/databases/%s/collections/%s/add",
                    chromaUrl, V2_BASE, TENANT, DATABASE, uuid);
            restTemplate.postForEntity(url, request, String.class);
            log.info("ChromaDB 写入 {} 个分块: kbId={}, uuid={}", chunks.size(), kbId, uuid);
        } catch (Exception e) {
            log.error("ChromaDB add 失败: kbId={}", kbId, e);
            throw new RuntimeException("ChromaDB add 失败", e);
        }
    }

    /** 语义检索 */
    public QueryResult query(Long kbId, String queryText, int topK) {
        String uuid = getCollectionUuid(kbId);
        if (uuid == null) return QueryResult.empty();

        try {
            List<Double> queryEmbedding = embeddingService.embed(queryText);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("query_embeddings", List.of(queryEmbedding));
            body.put("n_results", topK);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            String url = String.format("%s%s/tenants/%s/databases/%s/collections/%s/query",
                    chromaUrl, V2_BASE, TENANT, DATABASE, uuid);
            ResponseEntity<Map> resp = restTemplate.postForEntity(url, request, Map.class);

            return parseQueryResult(resp.getBody());
        } catch (Exception e) {
            log.warn("ChromaDB query 失败: kbId={}", kbId, e);
            return QueryResult.empty();
        }
    }

    /** 按文档 ID 删除向量 */
    public void deleteByDocument(Long kbId, Long documentId) {
        String uuid = getCollectionUuid(kbId);
        if (uuid == null) return;

        try {
            Map<String, Object> body = Map.of("where",
                    Map.of("document_id", String.valueOf(documentId)));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            String url = String.format("%s%s/tenants/%s/databases/%s/collections/%s/delete",
                    chromaUrl, V2_BASE, TENANT, DATABASE, uuid);
            restTemplate.postForEntity(url, request, String.class);
            log.info("ChromaDB 删除文档向量: kbId={}, docId={}", kbId, documentId);
        } catch (Exception e) {
            log.warn("ChromaDB delete 失败: kbId={}, docId={}", kbId, documentId, e);
        }
    }

    // ---------- 结果解析 ----------

    @SuppressWarnings("unchecked")
    private QueryResult parseQueryResult(Map<String, Object> raw) {
        if (raw == null || !raw.containsKey("ids")) return QueryResult.empty();
        List<List<String>> ids = (List<List<String>>) raw.get("ids");
        List<List<Double>> distances = (List<List<Double>>) raw.get("distances");
        List<List<String>> documents = (List<List<String>>) raw.get("documents");
        List<List<Map<String, Object>>> metadatas =
                (List<List<Map<String, Object>>>) raw.get("metadatas");

        if (ids == null || ids.isEmpty() || ids.get(0).isEmpty()) return QueryResult.empty();

        List<String> idList = ids.get(0);
        List<Double> distList = distances != null ? distances.get(0) :
                Collections.nCopies(idList.size(), 0.0);
        List<String> docList = documents != null ? documents.get(0) :
                Collections.nCopies(idList.size(), "");
        List<Map<String, Object>> metaList = metadatas != null ? metadatas.get(0) :
                Collections.nCopies(idList.size(), Collections.emptyMap());

        List<QueryResultItem> items = new ArrayList<>();
        for (int i = 0; i < idList.size(); i++) {
            items.add(new QueryResultItem(idList.get(i), docList.get(i),
                    distList.get(i), metaList.get(i)));
        }
        return new QueryResult(items);
    }

    // ---------- 内部类型 ----------

    public record ChunkData(Long documentId, int chunkIndex, String fileName, String content) {}

    public record QueryResultItem(String id, String document, double distance,
                                   Map<String, Object> metadata) {}

    public record QueryResult(List<QueryResultItem> items) {
        public static QueryResult empty() { return new QueryResult(Collections.emptyList()); }
        public boolean isEmpty() { return items.isEmpty(); }
        public int size() { return items.size(); }
    }
}
