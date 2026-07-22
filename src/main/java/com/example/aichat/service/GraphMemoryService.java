package com.example.aichat.service;

import com.example.aichat.model.MemoryEntity;
import com.example.aichat.model.MemoryItem;
import com.example.aichat.model.MemoryItemEntity;
import com.example.aichat.model.MemoryRelation;
import com.example.aichat.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 知识图谱服务 — 记忆实体提取、关系构建、图扩展检索。
 */
@Service
public class GraphMemoryService {

    private static final Logger log = LoggerFactory.getLogger(GraphMemoryService.class);

    private final MemoryEntityRepository entityRepo;
    private final MemoryItemEntityRepository itemEntityRepo;
    private final MemoryRelationRepository relationRepo;
    private final MemoryItemRepository memoryItemRepo;
    private final LLMService llmService;

    public GraphMemoryService(MemoryEntityRepository entityRepo,
                              MemoryItemEntityRepository itemEntityRepo,
                              MemoryRelationRepository relationRepo,
                              MemoryItemRepository memoryItemRepo,
                              LLMService llmService) {
        this.entityRepo = entityRepo;
        this.itemEntityRepo = itemEntityRepo;
        this.relationRepo = relationRepo;
        this.memoryItemRepo = memoryItemRepo;
        this.llmService = llmService;
    }

    /**
     * 从一条记忆事实中提取三元组 (主语, 谓词, 宾语)，
     * 并建立实体节点、记忆-实体关联、实体间关系。
     */
    @Transactional
    public void linkMemory(Long userId, MemoryItem memory, String factText) {
        List<Triple> triples = extractTriples(factText);
        if (triples.isEmpty()) return;

        for (Triple t : triples) {
            try {
                // 1. upsert 实体
                MemoryEntity subject = getOrCreateEntity(userId, t.subject, inferType(t.subject));
                MemoryEntity object = getOrCreateEntity(userId, t.object, inferType(t.object));

                // 2. 建立记忆-实体关联
                saveItemEntityLink(memory.getId(), subject.getId(), "SUBJECT");
                saveItemEntityLink(memory.getId(), object.getId(), "OBJECT");

                // 3. 建立实体间关系
                saveRelation(subject.getId(), t.predicate, object.getId(), memory.getId(), userId);
            } catch (Exception e) {
                log.warn("三元组处理失败: {} -[{}]-> {}: {}", t.subject, t.predicate, t.object, e.getMessage());
            }
        }
    }

    /**
     * 图扩展检索：从一条种子记忆出发，沿实体→关联记忆→邻接实体→关联记忆 做 1 跳扩展。
     *
     * @return 图扩展出的额外记忆（去重，不含种子记忆自身）
     */
    public List<MemoryItem> expandViaGraph(Long seedItemId, int maxResults) {
        Set<Long> visited = new HashSet<>();
        visited.add(seedItemId);
        List<MemoryItem> results = new ArrayList<>();

        // 1. 找到种子记忆关联的实体
        List<Long> entityIds = itemEntityRepo.findEntityIdsByMemoryId(seedItemId);

        // 2. 每个实体做 1 跳扩展
        for (Long entityId : entityIds) {
            if (results.size() >= maxResults) break;

            // 2a. 同实体关联的其他记忆
            for (Long itemId : itemEntityRepo.findMemoryIdsByEntityId(entityId)) {
                if (visited.add(itemId)) {
                    memoryItemRepo.findById(itemId).ifPresent(results::add);
                    if (results.size() >= maxResults) break;
                }
            }

            // 2b. 沿出边找邻接实体 → 再找它们的关联记忆
            for (MemoryRelation rel : relationRepo.findBySubjectId(entityId)) {
                if (results.size() >= maxResults) break;
                for (Long neighborItemId : itemEntityRepo.findMemoryIdsByEntityId(rel.getObjectId())) {
                    if (visited.add(neighborItemId)) {
                        memoryItemRepo.findById(neighborItemId).ifPresent(results::add);
                        if (results.size() >= maxResults) break;
                    }
                }
            }

            // 2c. 入边同理
            for (MemoryRelation rel : relationRepo.findByObjectId(entityId)) {
                if (results.size() >= maxResults) break;
                for (Long neighborItemId : itemEntityRepo.findMemoryIdsByEntityId(rel.getSubjectId())) {
                    if (visited.add(neighborItemId)) {
                        memoryItemRepo.findById(neighborItemId).ifPresent(results::add);
                        if (results.size() >= maxResults) break;
                    }
                }
            }
        }
        return results;
    }

    /**
     * 删除一条记忆时，清理其关联的实体链接。
     */
    @Transactional
    public void unlinkMemory(Long memoryItemId) {
        itemEntityRepo.deleteByMemoryItemId(memoryItemId);
    }

    // ==================== 内部方法 ====================

    /** 用 LLM 从事实文本中提取 (主语, 谓词, 宾语) */
    private List<Triple> extractTriples(String text) {
        try {
            String prompt = """
                从以下描述中提取三元组。每行一组，格式：主语 | 谓词 | 宾语
                主语/宾语: 人名/地名/机构名/产品名等专有名词
                谓词: 如 工作于/居住于/毕业于/拥有/喜欢/调到/升为
                无实体则回复 NONE

                描述: %s
                """.formatted(text);

            String result = llmService.chatSync(prompt);
            if (result == null || "NONE".equals(result.trim())) return List.of();

            List<Triple> triples = new ArrayList<>();
            for (String line : result.split("\n")) {
                line = line.strip();
                if (line.isEmpty()) continue;
                String[] parts = line.split("\\|", 3);
                if (parts.length < 3) continue;
                triples.add(new Triple(
                        parts[0].strip(),
                        parts[1].strip(),
                        parts[2].strip()
                ));
            }
            return triples;
        } catch (Exception e) {
            log.warn("三元组提取失败: {}", text, e);
            return List.of();
        }
    }

    /** 获取或创建实体（用户级唯一） */
    private MemoryEntity getOrCreateEntity(Long userId, String name, String type) {
        return entityRepo.findByUserIdAndName(userId, name)
                .orElseGet(() -> entityRepo.save(MemoryEntity.builder()
                        .userId(userId)
                        .name(name)
                        .type(type)
                        .build()));
    }

    /** 保存记忆-实体关联（忽略重复） */
    private void saveItemEntityLink(Long itemId, Long entityId, String role) {
        // 唯一约束 uk_mem_entity_role 防止重复，直接 insert 即可
        try {
            itemEntityRepo.save(MemoryItemEntity.builder()
                    .memoryItemId(itemId)
                    .entityId(entityId)
                    .role(role)
                    .build());
        } catch (Exception ignored) {
            // 重复忽略
        }
    }

    /** 保存实体间关系（不做去重，同关系可多次出现） */
    private void saveRelation(Long subjectId, String predicate, Long objectId, Long sourceItemId, Long userId) {
        relationRepo.save(MemoryRelation.builder()
                .subjectId(subjectId)
                .predicate(predicate)
                .objectId(objectId)
                .sourceItemId(sourceItemId)
                .userId(userId)
                .build());
    }

    /** 根据实体名推断类型 */
    private String inferType(String name) {
        // 简易规则，实际可由 LLM 提供更精确的分类
        if (name.length() <= 3 && name.chars().allMatch(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN)) {
            return "PERSON";
        }
        if (name.contains("公司") || name.contains("集团") || name.contains("科技")) {
            return "ORG";
        }
        if (name.contains("市") || name.contains("省") || name.contains("区") || name.contains("国")) {
            return "LOCATION";
        }
        return "MISC";
    }

    record Triple(String subject, String predicate, String object) {}
}
