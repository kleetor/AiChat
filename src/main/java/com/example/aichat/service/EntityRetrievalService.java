package com.example.aichat.service;

import com.example.aichat.model.MemoryEntity;
import com.example.aichat.model.MemoryRelation;
import com.example.aichat.repository.MemoryEntityRepository;
import com.example.aichat.repository.MemoryItemEntityRepository;
import com.example.aichat.repository.MemoryRelationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 基于知识图谱的实体检索服务。
 * 从查询语句中提取实体词，匹配用户实体库，找到关联的记忆。
 * 优化3: 实体匹配后沿知识图谱做1跳扩展，召回邻接实体的关联记忆。
 */
@Service
public class EntityRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(EntityRetrievalService.class);

    private final MemoryEntityRepository entityRepo;
    private final MemoryItemEntityRepository itemEntityRepo;
    private final MemoryRelationRepository relationRepo;
    private final LLMService llmService;

    public EntityRetrievalService(MemoryEntityRepository entityRepo,
                                   MemoryItemEntityRepository itemEntityRepo,
                                   MemoryRelationRepository relationRepo,
                                   LLMService llmService) {
        this.entityRepo = entityRepo;
        this.itemEntityRepo = itemEntityRepo;
        this.relationRepo = relationRepo;
        this.llmService = llmService;
    }

    /**
     * 从 query 中提取实体词，在用户实体库中匹配，返回关联的记忆及匹配度。
     * 优化3: 匹配后沿知识图谱做1跳图扩展，召回邻接实体的关联记忆。
     */
    public List<ScoredItem> searchByEntities(Long userId, String query, int topK) {
        try {
            // 1. LLM 提取 query 中的专有名词
            List<String> queryEntities = extractEntitiesFromQuery(query);
            if (queryEntities.isEmpty()) return List.of();

            // 2. 在 memory_entities 中匹配
            List<MemoryEntity> matched = new ArrayList<>();
            for (String name : queryEntities) {
                entityRepo.findByUserIdAndName(userId, name).ifPresent(matched::add);
            }
            if (matched.isEmpty()) return List.of();

            // 3. 聚合：每条记忆命中了多少个 query 实体（直接匹配）
            Map<Long, Double> itemScores = new HashMap<>();
            for (MemoryEntity entity : matched) {
                List<Long> itemIds = itemEntityRepo.findMemoryIdsByEntityId(entity.getId());
                for (Long itemId : itemIds) {
                    itemScores.merge(itemId, 1.0, Double::sum);
                }
            }

            // 4. 优化3: 图扩展 — 沿关系找邻接实体，召回它们的关联记忆
            Set<Long> expandedEntityIds = new HashSet<>();
            for (MemoryEntity entity : matched) {
                // 出边: entity → neighbor
                for (MemoryRelation rel : relationRepo.findBySubjectId(entity.getId())) {
                    if (expandedEntityIds.add(rel.getObjectId())) {
                        List<Long> neighborItemIds = itemEntityRepo.findMemoryIdsByEntityId(rel.getObjectId());
                        for (Long itemId : neighborItemIds) {
                            itemScores.merge(itemId, 0.5, (old, v) -> Math.max(old, 0.5));
                        }
                    }
                }
                // 入边: neighbor → entity
                for (MemoryRelation rel : relationRepo.findByObjectId(entity.getId())) {
                    if (expandedEntityIds.add(rel.getSubjectId())) {
                        List<Long> neighborItemIds = itemEntityRepo.findMemoryIdsByEntityId(rel.getSubjectId());
                        for (Long itemId : neighborItemIds) {
                            itemScores.merge(itemId, 0.5, (old, v) -> Math.max(old, 0.5));
                        }
                    }
                }
            }

            // 5. 归一化打分并排序
            double maxScore = Math.max(1.0, queryEntities.size());
            return itemScores.entrySet().stream()
                    .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                    .limit(topK)
                    .map(e -> new ScoredItem(e.getKey(), e.getValue() / maxScore))
                    .toList();

        } catch (Exception e) {
            log.warn("实体检索失败: {}", e.getMessage());
            return List.of();
        }
    }

    /** 用 LLM 从查询语句中提取专有名词 */
    private List<String> extractEntitiesFromQuery(String query) {
        try {
            String prompt = """
                从以下查询中提取专有名词（人名/地名/机构名/产品名）。
                每行一个，只输出名词本身，无专有名词则回复 NONE。

                查询: %s
                """.formatted(query);

            String result = llmService.chatSync(prompt);
            if (result == null || "NONE".equals(result.trim())) return List.of();

            return Arrays.stream(result.split("\n"))
                    .map(String::strip)
                    .filter(s -> !s.isEmpty())
                    .toList();
        } catch (Exception e) {
            log.warn("实体提取失败: {}", e.getMessage());
            return List.of();
        }
    }

    public record ScoredItem(long itemId, double score) {}
}
