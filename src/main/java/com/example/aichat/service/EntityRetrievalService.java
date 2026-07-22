package com.example.aichat.service;

import com.example.aichat.model.MemoryEntity;
import com.example.aichat.repository.MemoryEntityRepository;
import com.example.aichat.repository.MemoryItemEntityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 基于知识图谱的实体检索服务。
 * 从查询语句中提取实体词，匹配用户实体库，找到关联的记忆。
 */
@Service
public class EntityRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(EntityRetrievalService.class);

    private final MemoryEntityRepository entityRepo;
    private final MemoryItemEntityRepository itemEntityRepo;
    private final LLMService llmService;

    public EntityRetrievalService(MemoryEntityRepository entityRepo,
                                   MemoryItemEntityRepository itemEntityRepo,
                                   LLMService llmService) {
        this.entityRepo = entityRepo;
        this.itemEntityRepo = itemEntityRepo;
        this.llmService = llmService;
    }

    /**
     * 从 query 中提取实体词，在用户实体库中匹配，返回关联的记忆及匹配度。
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

            // 3. 聚合：每条记忆命中了多少个 query 实体
            Map<Long, Integer> itemScores = new HashMap<>();
            for (MemoryEntity entity : matched) {
                List<Long> itemIds = itemEntityRepo.findMemoryIdsByEntityId(entity.getId());
                for (Long itemId : itemIds) {
                    itemScores.merge(itemId, 1, Integer::sum);
                }
            }

            // 4. 归一化打分并排序
            int maxHits = queryEntities.size();
            return itemScores.entrySet().stream()
                    .sorted(Map.Entry.<Long, Integer>comparingByValue().reversed())
                    .limit(topK)
                    .map(e -> new ScoredItem(e.getKey(), (double) e.getValue() / maxHits))
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
