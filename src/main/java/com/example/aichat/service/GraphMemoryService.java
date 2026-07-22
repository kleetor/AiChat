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
                // 1. upsert 实体（优化4: LLM 类型优先，fallback 到规则推断）
                String subjType = t.subjectType != null ? t.subjectType : inferType(t.subject);
                String objType  = t.objectType  != null ? t.objectType  : inferType(t.object);
                MemoryEntity subject = getOrCreateEntity(userId, t.subject, subjType);
                MemoryEntity object = getOrCreateEntity(userId, t.object, objType);

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

    /** 优化4: 用 LLM 从事实文本中提取 (主语, 谓词, 宾语, 类型)，含 few-shot 示例 */
    private List<Triple> extractTriples(String text) {
        try {
            String prompt = """
                从以下描述中提取三元组。每行一组，格式：主语 | 谓词 | 宾语 | 主语类型 | 宾语类型
                类型: PERSON / ORG / LOCATION / PRODUCT / MISC
                谓词: 如 工作于/居住于/毕业于/拥有/喜欢/调到/升为
                无实体则回复 NONE

                示例:
                张三在北京工作 → 张三 | 工作于 | 北京 | PERSON | LOCATION
                李华就职于华为公司 → 李华 | 任职于 | 华为公司 | PERSON | ORG
                小王毕业于北京大学 → 小王 | 毕业于 | 北京大学 | PERSON | ORG

                描述: %s
                """.formatted(text);

            String result = llmService.chatSync(prompt);
            if (result == null || "NONE".equals(result.trim())) return List.of();

            List<Triple> triples = new ArrayList<>();
            for (String line : result.split("\n")) {
                line = line.strip();
                if (line.isEmpty()) continue;
                String[] parts = line.split("\\|");
                if (parts.length < 3) continue;
                String subject = parts[0].strip();
                String predicate = parts[1].strip();
                String object = parts[2].strip();
                String subjectType = parts.length >= 4 ? parts[3].strip() : inferType(subject);
                String objectType = parts.length >= 5 ? parts[4].strip() : inferType(object);

                // 校验
                if (subject.equals(object)) continue;          // 主语≠宾语
                if (predicate.length() > 50) continue;        // 谓词长度 ≤ 50
                if (subject.isEmpty() || object.isEmpty()) continue;

                triples.add(new Triple(subject, predicate, object, subjectType, objectType));
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

    // 优化5: 高频谓词反向映射
    private static final Map<String, String> PREDICATE_INVERSES = Map.of(
            "工作于", "拥有员工",
            "位于",   "包含",
            "毕业于", "培养",
            "属于",   "包含",
            "任职于", "拥有员工",
            "调往",   "接收"
    );

    /** 优化1+5: 保存实体间关系，去重并自动追加反向边 */
    private void saveRelation(Long subjectId, String predicate, Long objectId, Long sourceItemId, Long userId) {
        // 优化1: 去重 — 存在则更新 valid_until 和 source_item_id
        var existing = relationRepo.findBySubjectIdAndPredicateAndObjectId(subjectId, predicate, objectId);
        if (existing.isPresent()) {
            MemoryRelation rel = existing.get();
            rel.setValidUntil(null); // 重新激活
            rel.setSourceItemId(sourceItemId);
            relationRepo.save(rel);
            log.debug("关系去重: {} -[{}]-> {}", subjectId, predicate, objectId);
        } else {
            relationRepo.save(MemoryRelation.builder()
                    .subjectId(subjectId)
                    .predicate(predicate)
                    .objectId(objectId)
                    .sourceItemId(sourceItemId)
                    .userId(userId)
                    .build());
        }

        // 优化5: 自动追加反向边
        String inverse = PREDICATE_INVERSES.get(predicate);
        if (inverse != null) {
            var invExisting = relationRepo.findBySubjectIdAndPredicateAndObjectId(objectId, inverse, subjectId);
            if (invExisting.isEmpty()) {
                relationRepo.save(MemoryRelation.builder()
                        .subjectId(objectId)
                        .predicate(inverse)
                        .objectId(subjectId)
                        .sourceItemId(sourceItemId)
                        .userId(userId)
                        .build());
            }
        }
    }

    /** 优化2: 将指定 sourceItem 的所有关系标记为过期 */
    @Transactional
    public void expireRelations(Long itemId) {
        int count = relationRepo.expireBySourceItemId(itemId, LocalDateTime.now());
        if (count > 0) {
            log.info("关系过期: sourceItemId={}, count={}", itemId, count);
        }
    }

    // ==================== 优化6: 实体消歧 ====================

    /**
     * 用 LLM 扫描用户所有实体，识别可合并的候选对（同人异名/简称全称等）。
     * @return 合并候选列表 [{fromId, toId}]，toId 为保留的目标实体
     */
    public List<MergeCandidate> suggestMerges(Long userId) {
        List<MemoryEntity> entities = entityRepo.findByUserId(userId);
        if (entities.size() < 2) return List.of();

        // 构建实体清单
        StringBuilder sb = new StringBuilder();
        for (MemoryEntity e : entities) {
            sb.append(e.getId()).append(": ").append(e.getName()).append(" (").append(e.getType()).append(")\n");
        }

        try {
            String prompt = """
                以下是一个用户的实体列表（格式: 编号: 名称 (类型)）。
                请找出其中可能指代同一真实实体的对（如同一个人不同称呼、机构全称与简称）。
                返回 JSON 数组，每个元素包含 from 和 to 字段（from 为应合并掉的编号，to 为保留的目标编号）。
                无候选则返回空数组 []。

                实体列表:
                %s
                """.formatted(sb.toString());

            String result = llmService.chatSync(prompt);
            if (result == null || result.trim().isEmpty()) return List.of();

            // 提取 JSON 部分（LLM 可能包裹在 ```json ... ``` 中）
            String json = result.trim();
            if (json.contains("```")) {
                json = json.replaceAll("```\\w*\\n?", "").replace("```", "").trim();
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> raw = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(json, new com.fasterxml.jackson.core.type.TypeReference<>() {});
            return raw.stream()
                    .map(m -> new MergeCandidate(
                            ((Number) m.get("from")).longValue(),
                            ((Number) m.get("to")).longValue()))
                    .toList();
        } catch (Exception e) {
            log.warn("实体消歧建议失败: userId={}: {}", userId, e.getMessage());
            return List.of();
        }
    }

    /**
     * 执行实体合并：将 fromId 的所有关系和关联转移到 toId，然后删除 fromId。
     * 在事务中执行，保证原子性。
     */
    @Transactional
    public void mergeEntities(Long fromId, Long toId) {
        if (fromId.equals(toId)) {
            log.warn("实体合并跳过: fromId == toId == {}", fromId);
            return;
        }

        MemoryEntity fromEntity = entityRepo.findById(fromId).orElse(null);
        MemoryEntity toEntity = entityRepo.findById(toId).orElse(null);
        if (fromEntity == null || toEntity == null) {
            log.warn("实体合并跳过: 实体不存在 fromId={}, toId={}", fromId, toId);
            return;
        }

        log.info("实体合并开始: {} (id={}) → {} (id={})", fromEntity.getName(), fromId, toEntity.getName(), toId);

        // 1. 删除会产生唯一约束冲突的 memory_item_entities 行
        int deletedLinks = itemEntityRepo.deleteConflicting(fromId, toId);
        log.debug("  删除冲突关联: {} 行", deletedLinks);

        // 2. 将剩余 memory_item_entities 转移到目标实体
        int updatedLinks = itemEntityRepo.updateEntityId(fromId, toId);
        log.debug("  转移关联: {} 行", updatedLinks);

        // 3. 更新关系中的 subject_id
        int updatedSubject = relationRepo.updateSubjectId(fromId, toId);
        log.debug("  更新 subject_id: {} 行", updatedSubject);

        // 4. 更新关系中的 object_id
        int updatedObject = relationRepo.updateObjectId(fromId, toId);
        log.debug("  更新 object_id: {} 行", updatedObject);

        // 5. 删除源实体（数据库级联会处理 FK）
        entityRepo.deleteById(fromId);
        log.info("实体合并完成: {} → {}", fromEntity.getName(), toEntity.getName());
    }

    public record MergeCandidate(long fromId, long toId) {}

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

    record Triple(String subject, String predicate, String object, String subjectType, String objectType) {
        Triple(String subject, String predicate, String object) {
            this(subject, predicate, object, null, null);
        }
    }
}
