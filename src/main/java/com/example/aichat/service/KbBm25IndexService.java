package com.example.aichat.service;

import jakarta.annotation.PreDestroy;
import org.apache.lucene.analysis.cn.smart.SmartChineseAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 知识库 BM25 关键词索引服务。
 * 每个知识库一个独立 Lucene 索引目录：./data/bm25-kb/{kbId}/
 * 文档 ID 格式：doc_{docId}_chunk_{idx}，与 ChromaDB 向量 ID 一致。
 */
@Service
public class KbBm25IndexService {

    private static final Logger log = LoggerFactory.getLogger(KbBm25IndexService.class);

    private final SmartChineseAnalyzer analyzer;
    /** kbId → IndexWriter（懒创建） */
    private final ConcurrentHashMap<Long, IndexWriter> writers = new ConcurrentHashMap<>();
    private static final Path ROOT = Path.of("./data/bm25-kb");

    public KbBm25IndexService() {
        this.analyzer = new SmartChineseAnalyzer();
        try { Files.createDirectories(ROOT); } catch (IOException ignored) {}
        log.info("KB BM25 索引根目录: {}", ROOT.toAbsolutePath());
    }

    @PreDestroy
    public void shutdown() {
        writers.values().forEach(w -> {
            try { w.close(); } catch (IOException e) { log.warn("关闭 BM25 writer 失败", e); }
        });
        log.info("KB BM25 索引已全部关闭");
    }

    // ==================== 文档操作 ====================

    /** 索引一个分块 */
    public void index(Long kbId, String chunkId, Long docId, int chunkIndex, String fileName, String text) {
        try {
            IndexWriter writer = getWriter(kbId);
            // 幂等：先删后写
            writer.deleteDocuments(new Term("chunkId", chunkId));
            Document doc = new Document();
            doc.add(new StringField("chunkId", chunkId, Field.Store.YES));
            doc.add(new LongPoint("kbId", kbId));
            doc.add(new StoredField("kbId", kbId));
            doc.add(new LongPoint("docId", docId));
            doc.add(new StoredField("docId", docId));
            doc.add(new StoredField("chunkIndex", chunkIndex));
            doc.add(new StoredField("fileName", fileName));
            doc.add(new TextField("text", text, Field.Store.YES));
            writer.addDocument(doc);
            writer.commit();
        } catch (IOException e) {
            log.error("KB BM25 索引写入失败: kbId={}, chunkId={}", kbId, chunkId, e);
        }
    }

    /** 批量索引一个文档的所有分块 */
    public void indexChunks(Long kbId, List<ChromaDBService.ChunkData> chunks) {
        try {
            IndexWriter writer = getWriter(kbId);
            for (var c : chunks) {
                String chunkId = "doc_" + c.documentId() + "_chunk_" + c.chunkIndex();
                writer.deleteDocuments(new Term("chunkId", chunkId));
                Document doc = new Document();
                doc.add(new StringField("chunkId", chunkId, Field.Store.YES));
                doc.add(new LongPoint("kbId", kbId));
                doc.add(new StoredField("kbId", kbId));
                doc.add(new LongPoint("docId", c.documentId()));
                doc.add(new StoredField("docId", c.documentId()));
                doc.add(new StoredField("chunkIndex", c.chunkIndex()));
                doc.add(new StoredField("fileName", c.fileName() != null ? c.fileName() : ""));
                doc.add(new TextField("text", c.content(), Field.Store.YES));
                writer.addDocument(doc);
            }
            writer.commit();
            log.info("KB BM25 批量索引: kbId={}, count={}", kbId, chunks.size());
        } catch (IOException e) {
            log.error("KB BM25 批量索引失败: kbId={}", kbId, e);
        }
    }

    /** 删除指定文档的所有分块 */
    public void removeByDocument(Long kbId, Long docId) {
        try {
            IndexWriter writer = getWriter(kbId);
            writer.deleteDocuments(LongPoint.newExactQuery("docId", docId));
            writer.commit();
            log.info("KB BM25 删除文档: kbId={}, docId={}", kbId, docId);
        } catch (IOException e) {
            log.warn("KB BM25 删除失败: kbId={}, docId={}", kbId, docId, e);
        }
    }

    /** 删除整个知识库的索引 */
    public void deleteIndex(Long kbId) {
        IndexWriter writer = writers.remove(kbId);
        if (writer != null) {
            try { writer.close(); } catch (IOException e) { log.warn("关闭 BM25 writer 失败: kbId={}", kbId, e); }
        }
        Path indexPath = ROOT.resolve(String.valueOf(kbId));
        try {
            if (Files.exists(indexPath)) {
                try (var s = Files.walk(indexPath)) {
                    s.sorted(java.util.Comparator.reverseOrder())
                     .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
                }
            }
            log.info("KB BM25 索引已删除: kbId={}", kbId);
        } catch (IOException e) {
            log.warn("KB BM25 索引目录删除失败: kbId={}", kbId, e);
        }
    }

    // ==================== 检索 ====================

    /**
     * BM25 关键词检索，返回 (chunkId, document text, score)。
     */
    public List<KbBm25Hit> search(Long kbId, String query, int topK) {
        try {
            IndexWriter writer = writers.get(kbId);
            if (writer == null) return List.of();

            DirectoryReader reader = DirectoryReader.open(writer);
            IndexSearcher searcher = new IndexSearcher(reader);

            Query textQuery = new QueryParser("text", analyzer).parse(QueryParser.escape(query));
            Query kbFilter = LongPoint.newExactQuery("kbId", kbId);

            BooleanQuery booleanQuery = new BooleanQuery.Builder()
                    .add(textQuery, BooleanClause.Occur.MUST)
                    .add(kbFilter, BooleanClause.Occur.FILTER)
                    .build();

            TopDocs topDocs = searcher.search(booleanQuery, topK);
            List<KbBm25Hit> results = new ArrayList<>();
            for (ScoreDoc sd : topDocs.scoreDocs) {
                var stored = searcher.storedFields();
                var doc = stored.document(sd.doc);
                results.add(new KbBm25Hit(
                        doc.get("chunkId"),
                        doc.get("text"),
                        sd.score,
                        doc.get("fileName")
                ));
            }
            reader.close();
            return results;
        } catch (Exception e) {
            log.warn("KB BM25 搜索失败: kbId={}", kbId, e);
            return List.of();
        }
    }

    // ==================== 内部 ====================

    private IndexWriter getWriter(Long kbId) throws IOException {
        return writers.computeIfAbsent(kbId, id -> {
            try {
                Path indexPath = ROOT.resolve(String.valueOf(id));
                Files.createDirectories(indexPath);
                var dir = FSDirectory.open(indexPath);
                var config = new IndexWriterConfig(analyzer)
                        .setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
                return new IndexWriter(dir, config);
            } catch (IOException e) {
                throw new RuntimeException("KB BM25 索引创建失败: kbId=" + id, e);
            }
        });
    }

    // ==================== 内部类型 ====================

    public record KbBm25Hit(String chunkId, String text, float bm25Score, String fileName) {}
}
