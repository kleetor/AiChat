package com.example.aichat.service;

import com.example.aichat.config.ChromaDBConfig;

import java.util.*;

/**
 * ChromaDB 知识库操作服务。
 * Collection 命名规则：kb_{kbId}
 */
@org.springframework.stereotype.Service
public class ChromaDBService extends BaseChromaDBService<Long> {

    public ChromaDBService(org.springframework.web.client.RestTemplate restTemplate,
                            SiliconFlowEmbeddingService embeddingService,
                            ChromaDBConfig config) {
        super(restTemplate, embeddingService, config.getChromaUrl());
    }

    @Override
    protected String collectionName(Long kbId) {
        return "kb_" + kbId;
    }

    // ==================== 知识库文档块操作 ====================

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

            add(uuid, ids, allEmbeddings, texts, metadatas);
            // 释放大对象
            allEmbeddings = null;
            texts = null;
            ids = null;
            metadatas = null;
            System.gc();

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
            Map<String, Object> raw = queryRaw(uuid, queryEmbedding, topK);
            return parseQueryResult(raw);
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
            deleteByWhere(uuid, Map.of("document_id", String.valueOf(documentId)));
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
