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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于 Lucene 的 BM25 关键词检索服务。
 * 全局单个索引，通过 userId 字段过滤实现用户隔离。
 */
@Service
public class Bm25IndexService {

    private static final Logger log = LoggerFactory.getLogger(Bm25IndexService.class);

    private final SmartChineseAnalyzer analyzer;
    private final IndexWriter writer;

    public Bm25IndexService() {
        try {
            this.analyzer = new SmartChineseAnalyzer();
            Path indexPath = Path.of("./data/bm25-index");
            var dir = FSDirectory.open(indexPath);
            var config = new IndexWriterConfig(analyzer)
                    .setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
            this.writer = new IndexWriter(dir, config);
            log.info("BM25 索引已就绪: {}", indexPath.toAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("BM25 索引初始化失败", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        try {
            writer.close();
            log.info("BM25 索引已关闭");
        } catch (IOException e) {
            log.warn("BM25 索引关闭异常", e);
        }
    }

    /** 索引或更新一条记忆。promptId 为 null 时存为 0（共享） */
    public void index(Long itemId, Long userId, String text, Long promptId) {
        try {
            long pid = (promptId != null) ? promptId : 0L;
            writer.deleteDocuments(LongPoint.newExactQuery("itemId", itemId));
            Document doc = new Document();
            doc.add(new LongPoint("itemId", itemId));
            doc.add(new StoredField("itemId", itemId));
            doc.add(new LongPoint("userId", userId));
            doc.add(new StoredField("userId", userId));
            doc.add(new LongPoint("promptId", pid));
            doc.add(new StoredField("promptId", pid));
            doc.add(new TextField("text", text, Field.Store.YES));
            writer.addDocument(doc);
            writer.commit();
        } catch (IOException e) {
            log.error("BM25 索引写入失败: itemId={}", itemId, e);
        }
    }

    /** 从索引中移除一条记忆 */
    public void remove(Long itemId) {
        try {
            writer.deleteDocuments(LongPoint.newExactQuery("itemId", itemId));
            writer.commit();
        } catch (IOException e) {
            log.warn("BM25 索引删除失败: itemId={}", itemId, e);
        }
    }

    /**
     * BM25 搜索，返回 (itemId, score)。
     * promptId 为 null 时只匹配共享记忆(0)，非 null 时匹配共享+该角色。
     */
    public List<DocHit> search(Long userId, String query, int topK, Long promptId) {
        try {
            DirectoryReader reader = DirectoryReader.open(writer);
            IndexSearcher searcher = new IndexSearcher(reader);

            Query textQuery = new QueryParser("text", analyzer).parse(QueryParser.escape(query));
            Query userFilter = LongPoint.newExactQuery("userId", userId);

            // promptId 过滤：共享(0) 或 指定角色
            BooleanQuery.Builder promptFilter = new BooleanQuery.Builder();
            promptFilter.add(LongPoint.newExactQuery("promptId", 0L), BooleanClause.Occur.SHOULD);
            if (promptId != null) {
                promptFilter.add(LongPoint.newExactQuery("promptId", promptId), BooleanClause.Occur.SHOULD);
            }

            BooleanQuery booleanQuery = new BooleanQuery.Builder()
                    .add(textQuery, BooleanClause.Occur.MUST)
                    .add(userFilter, BooleanClause.Occur.FILTER)
                    .add(promptFilter.build(), BooleanClause.Occur.FILTER)
                    .build();

            TopDocs topDocs = searcher.search(booleanQuery, topK);
            List<DocHit> results = new ArrayList<>();
            for (ScoreDoc sd : topDocs.scoreDocs) {
                var stored = searcher.storedFields();
                var doc = stored.document(sd.doc);
                long itemId = Long.parseLong(doc.get("itemId"));
                results.add(new DocHit(itemId, sd.score));
            }
            reader.close();
            return results;
        } catch (Exception e) {
            log.warn("BM25 搜索失败: {}", e.getMessage());
            return List.of();
        }
    }

    /** 从 MySQL 全量重建指定用户的索引 */
    public void rebuildUser(Long userId, List<MemoryDoc> docs) {
        try {
            writer.deleteDocuments(LongPoint.newExactQuery("userId", userId));
            for (var d : docs) {
                Document doc = new Document();
                doc.add(new LongPoint("itemId", d.itemId));
                doc.add(new StoredField("itemId", d.itemId));
                doc.add(new LongPoint("userId", userId));
                doc.add(new StoredField("userId", userId));
                doc.add(new TextField("text", d.text, Field.Store.YES));
                writer.addDocument(doc);
            }
            writer.commit();
            log.info("BM25 全量重建完成: userId={}, count={}", userId, docs.size());
        } catch (IOException e) {
            log.error("BM25 全量重建失败: userId={}", userId, e);
        }
    }

    public record DocHit(long itemId, float score) {}
    public record MemoryDoc(long itemId, String text, Long promptId) {}
}
