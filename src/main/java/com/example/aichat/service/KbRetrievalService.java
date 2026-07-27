package com.example.aichat.service;

import com.example.aichat.config.props.RagProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 知识库检索编排服务：向量召回 → Rerank 精排。
 *
 * 容错：向量检索失败返回空，Rerank 失败则降级返回向量结果。
 */
@Service
public class KbRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(KbRetrievalService.class);
    private static final double RRF_K = 60.0;

    private final ChromaDBService chromaDBService;
    private final SiliconFlowRerankService rerankService;
    private final QueryRewriterService queryRewriterService;
    private final RagProperties ragProperties;

    public KbRetrievalService(ChromaDBService chromaDBService,
                               SiliconFlowRerankService rerankService,
                               QueryRewriterService queryRewriterService,
                               RagProperties ragProperties) {
        this.chromaDBService = chromaDBService;
        this.rerankService = rerankService;
        this.queryRewriterService = queryRewriterService;
        this.ragProperties = ragProperties;
    }

    /**
     * 检索入口：查询重写（可选）→ 向量召回 → Rerank 精排。
     *
     * @param kbId   知识库 ID
     * @param query  用户查询文本
     * @return 精排后的检索结果
     */
    public ChromaDBService.QueryResult hybridSearch(Long kbId, String query) {
        int candidateSize = ragProperties.getRetrieve().getCandidateSize();
        int finalTopK = ragProperties.getRetrieve().getTopK();
        boolean rewriteEnabled = ragProperties.getRetrieve().isQueryRewriteEnabled();

        // === Phase 0: 查询重写（可选） ===
        List<String> queries;
        if (rewriteEnabled) {
            queries = queryRewriterService.rewrite(query);
            log.debug("KB 查询重写: {} → {} 个变体", query, queries.size());
        } else {
            queries = List.of(query);
        }

        // === Phase 1: 多查询并行向量召回 ===
        List<CompletableFuture<ChromaDBService.QueryResult>> futures =
                queries.stream()
                        .map(q -> CompletableFuture.supplyAsync(
                                () -> safeVectorSearch(kbId, q, candidateSize)))
                        .toList();

        List<ChromaDBService.QueryResult> allResults = futures.stream()
                .map(CompletableFuture::join).toList();

        // === Phase 2: RRF 融合（多查询） ===
        List<ChromaDBService.QueryResultItem> fused = rrfFusion(allResults, finalTopK * 3);
        if (fused.isEmpty()) return ChromaDBService.QueryResult.empty();

        // === Phase 3: Cross-Encoder 精排（用原始查询打分） ===
        List<ChromaDBService.QueryResultItem> reranked = rerankResults(query, fused, finalTopK);

        return new ChromaDBService.QueryResult(reranked);
    }

    // ==================== 内部方法 ====================

    /** 安全的向量检索，异常返回空 */
    private ChromaDBService.QueryResult safeVectorSearch(Long kbId, String query, int topK) {
        try {
            return chromaDBService.query(kbId, query, topK);
        } catch (Exception e) {
            log.warn("KB 向量检索失败: kbId={}", kbId, e);
            return ChromaDBService.QueryResult.empty();
        }
    }

    /**
     * 多查询 Reciprocal Rank Fusion：每个查询的向量结果独立参与排名。
     * score(doc) = Σ 1 / (K + rank_i) ，跨所有查询叠加。
     */
    private List<ChromaDBService.QueryResultItem> rrfFusion(
            List<ChromaDBService.QueryResult> allResults,
            int maxCandidates) {

        // chunkId → RRF 分数
        Map<String, Double> scores = new LinkedHashMap<>();
        Map<String, ChromaDBService.QueryResultItem> itemMap = new HashMap<>();

        for (var result : allResults) {
            List<ChromaDBService.QueryResultItem> items = result.items();
            for (int i = 0; i < items.size(); i++) {
                var item = items.get(i);
                scores.merge(item.id(), 1.0 / (RRF_K + i + 1), Double::sum);
                itemMap.putIfAbsent(item.id(), item);
            }
        }

        // 按融合分数降序、取前 maxCandidates
        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(maxCandidates)
                .map(Map.Entry::getKey)
                .map(itemMap::get)
                .filter(Objects::nonNull)
                .toList();
    }

    /** Rerank 精排，失败降级返回 RRF 结果 */
    private List<ChromaDBService.QueryResultItem> rerankResults(
            String query,
            List<ChromaDBService.QueryResultItem> candidates,
            int topN) {

        if (candidates.isEmpty()) return List.of();

        try {
            List<String> documents = candidates.stream()
                    .map(ChromaDBService.QueryResultItem::document)
                    .toList();

            // 调用 SiliconFlow Rerank API
            List<SiliconFlowRerankService.RerankTextResult> scored = rerankService.rerankTexts(query, documents, topN);

            // 按 score 降序重排
            return scored.stream()
                    .map(s -> candidates.get(s.index()))
                    .toList();

        } catch (Exception e) {
            log.warn("KB Rerank 失败，降级为 RRF 结果: kbId query={}", query, e);
            return candidates.stream().limit(topN).toList();
        }
    }
}
