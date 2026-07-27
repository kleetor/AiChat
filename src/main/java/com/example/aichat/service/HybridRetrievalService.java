package com.example.aichat.service;

import com.example.aichat.model.MemoryItem;
import com.example.aichat.repository.MemoryItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 混合检索编排服务：两路召回 → RRF 融合 → Rerank 精排。
 *
 * 两路信号：
 *   - ChromaDB 语义向量
 *   - 知识图谱实体匹配
 *
 * 容错：任一路失败不影响其他路，Rerank 失败则降级返回 RRF 结果。
 */
@Service
public class HybridRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(HybridRetrievalService.class);
    private static final double RRF_K = 60.0;
    private static final int CANDIDATE_SIZE = 30;

    private final MemoryChromaService chromaService;
    private final EntityRetrievalService entityService;
    private final SiliconFlowRerankService rerankService;
    private final MemoryItemRepository memoryRepo;

    public HybridRetrievalService(MemoryChromaService chromaService,
                                   EntityRetrievalService entityService,
                                   SiliconFlowRerankService rerankService,
                                   MemoryItemRepository memoryRepo) {
        this.chromaService = chromaService;
        this.entityService = entityService;
        this.rerankService = rerankService;
        this.memoryRepo = memoryRepo;
    }

    /**
     * 混合检索入口。
     *
     * @param userId    用户 ID
     * @param query     搜索查询
     * @param finalTopK 最终返回条数
     * @param promptId  提示词角色 ID（null 时仅搜索共享记忆）
     * @return 精排后的记忆列表
     */
    public List<MemoryItem> hybridSearch(Long userId, String query, int finalTopK, Long promptId) {
        // === Phase 1: 两路并行召回 ===
        var chromaFuture = CompletableFuture.supplyAsync(
                () -> safeChromaSearch(userId, query));
        var entityFuture = CompletableFuture.supplyAsync(
                () -> entityService.searchByEntities(userId, query, CANDIDATE_SIZE));

        List<MemoryChromaService.MemoryHit> chromaHits = chromaFuture.join();
        List<EntityRetrievalService.ScoredItem> entityHits = entityFuture.join();

        log.debug("混合召回: chroma={}, entity={}", chromaHits.size(), entityHits.size());

        // === Phase 2: RRF 融合 ===
        Map<Long, Double> fused = rrfFusion(chromaHits, entityHits);

        List<Long> candidateIds = fused.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(CANDIDATE_SIZE)
                .map(Map.Entry::getKey)
                .toList();

        if (candidateIds.isEmpty()) return List.of();

        // 保持 RRF 排序加载候选
        Map<Long, MemoryItem> candidateMap = new HashMap<>();
        memoryRepo.findAllById(candidateIds).forEach(m -> candidateMap.put(m.getId(), m));
        List<MemoryItem> candidates = candidateIds.stream()
                .map(candidateMap::get)
                .filter(Objects::nonNull)
                .toList();

        if (candidates.isEmpty()) return List.of();

        // === Phase 3: Cross-Encoder 精排 ===
        List<SiliconFlowRerankService.ScoredItem> reranked =
                rerankService.rerank(query, candidates, finalTopK);

        // === Phase 4: 返回最终结果 ===
        return reranked.stream()
                .map(s -> candidateMap.get(s.itemId()))
                .filter(Objects::nonNull)
                .toList();
    }

    // ==================== 内部方法 ====================

    /** 安全的 ChromaDB 搜索，异常返回空 */
    private List<MemoryChromaService.MemoryHit> safeChromaSearch(Long userId, String query) {
        try {
            return chromaService.search(userId, query, CANDIDATE_SIZE);
        } catch (Exception e) {
            log.warn("ChromaDB 搜索失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Reciprocal Rank Fusion: 无超参数的两路分数融合。
     * score(doc) = Σ 1 / (K + rank_i)
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private Map<Long, Double> rrfFusion(List chromaHits, List entityHits) {
        Map<Long, Double> scores = new HashMap<>();

        // ChromaDB: score 越高越相关，转为排名
        for (int i = 0; i < chromaHits.size(); i++) {
            MemoryChromaService.MemoryHit hit = (MemoryChromaService.MemoryHit) chromaHits.get(i);
            final double rank = i;
            memoryRepo.findByChromaId(hit.chromaId()).ifPresent(
                    m -> scores.merge(m.getId(), 1.0 / (RRF_K + rank + 1), Double::sum));
        }

        // 实体匹配
        for (int i = 0; i < entityHits.size(); i++) {
            EntityRetrievalService.ScoredItem hit = (EntityRetrievalService.ScoredItem) entityHits.get(i);
            scores.merge(hit.itemId(), 1.0 / (RRF_K + i + 1), Double::sum);
        }

        return scores;
    }
}
