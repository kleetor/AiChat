package com.example.aichat.service;

import com.example.aichat.config.props.RagProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 知识库混合检索编排服务：向量召回 + BM25 关键词 → RRF 融合 → Rerank 精排。
 *
 * 双路召回：
 *   - ChromaDB 语义向量（已有）
 *   - Lucene BM25 关键词（KbBm25IndexService）
 *
 * 容错：任一路失败不影响另一路，Rerank 失败则降级返回 RRF 结果。
 */
@Service
public class KbRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(KbRetrievalService.class);
    private static final double RRF_K = 60.0;

    private final ChromaDBService chromaDBService;
    private final KbBm25IndexService bm25Service;
    private final SiliconFlowRerankService rerankService;
    private final QueryRewriterService queryRewriterService;
    private final RagProperties ragProperties;

    public KbRetrievalService(ChromaDBService chromaDBService,
                               KbBm25IndexService bm25Service,
                               SiliconFlowRerankService rerankService,
                               QueryRewriterService queryRewriterService,
                               RagProperties ragProperties) {
        this.chromaDBService = chromaDBService;
        this.bm25Service = bm25Service;
        this.rerankService = rerankService;
        this.queryRewriterService = queryRewriterService;
        this.ragProperties = ragProperties;
    }

    /**
     * 混合检索入口：查询重写（可选）→ 多路并行召回 → RRF 融合 → Rerank 精排。
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

        // === Phase 1: 多查询并行召回 ===
        List<CompletableFuture<Map.Entry<ChromaDBService.QueryResult, List<KbBm25IndexService.KbBm25Hit>>>> futures =
                queries.stream()
                        .map(q -> CompletableFuture.supplyAsync(() -> {
                            var vf = CompletableFuture.supplyAsync(
                                    () -> safeVectorSearch(kbId, q, candidateSize));
                            var bf = CompletableFuture.supplyAsync(
                                    () -> bm25Service.search(kbId, q, candidateSize));
                            return Map.entry(vf.join(), bf.join());
                        }))
                        .toList();

        // 收集所有召回结果（每个查询独立权重进入 RRF）
        List<ChromaDBService.QueryResult> allVectorResults = new ArrayList<>();
        List<List<KbBm25IndexService.KbBm25Hit>> allBm25Results = new ArrayList<>();
        for (var f : futures) {
            var entry = f.join();
            allVectorResults.add(entry.getKey());
            allBm25Results.add(entry.getValue());
        }

        log.debug("KB 多查询召回完成: kbId={}, queries={}, vector={}, bm25={}",
                kbId, queries.size(),
                allVectorResults.stream().mapToInt(ChromaDBService.QueryResult::size).sum(),
                allBm25Results.stream().mapToInt(List::size).sum());

        // === Phase 2: 多查询 RRF 融合 ===
        List<ChromaDBService.QueryResultItem> fused = rrfFusionMulti(allVectorResults, allBm25Results, finalTopK * 3);
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
     * 多查询 Reciprocal Rank Fusion：每个查询的向量+BM25 结果独立参与排名。
     * score(doc) = Σ 1 / (K + rank_i) ，跨所有查询叠加。
     */
    private List<ChromaDBService.QueryResultItem> rrfFusionMulti(
            List<ChromaDBService.QueryResult> allVectorResults,
            List<List<KbBm25IndexService.KbBm25Hit>> allBm25Results,
            int maxCandidates) {

        // chunkId → RRF 分数
        Map<String, Double> scores = new LinkedHashMap<>();
        // chunkId → 数据来源
        Map<String, ChromaDBService.QueryResultItem> itemMap = new HashMap<>();
        Map<String, String> bm25TextMap = new HashMap<>();
        Map<String, String> bm25FileMap = new HashMap<>();

        for (int qi = 0; qi < allVectorResults.size(); qi++) {
            // 向量路
            List<ChromaDBService.QueryResultItem> vItems = allVectorResults.get(qi).items();
            for (int i = 0; i < vItems.size(); i++) {
                var item = vItems.get(i);
                scores.merge(item.id(), 1.0 / (RRF_K + i + 1), Double::sum);
                itemMap.putIfAbsent(item.id(), item);
            }

            // BM25 路
            List<KbBm25IndexService.KbBm25Hit> bmHits = allBm25Results.get(qi);
            for (int i = 0; i < bmHits.size(); i++) {
                var hit = bmHits.get(i);
                scores.merge(hit.chunkId(), 1.0 / (RRF_K + i + 1), Double::sum);
                if (!itemMap.containsKey(hit.chunkId())) {
                    bm25TextMap.put(hit.chunkId(), hit.text());
                    bm25FileMap.put(hit.chunkId(), hit.fileName());
                }
            }
        }

        // 按融合分数降序、取前 maxCandidates
        List<String> rankedIds = scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(maxCandidates)
                .map(Map.Entry::getKey)
                .toList();

        // 按 RRF 排名输出候选
        List<ChromaDBService.QueryResultItem> candidates = new ArrayList<>();
        for (String id : rankedIds) {
            ChromaDBService.QueryResultItem item = itemMap.get(id);
            if (item != null) {
                candidates.add(item);
            } else if (bm25TextMap.containsKey(id)) {
                candidates.add(new ChromaDBService.QueryResultItem(
                        id, bm25TextMap.get(id), 0.0,
                        Map.of("file_name", bm25FileMap.get(id))
                ));
            }
        }
        return candidates;
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
