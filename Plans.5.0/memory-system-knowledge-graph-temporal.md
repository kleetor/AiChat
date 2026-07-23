# 记忆系统改造方案：知识图谱 + 时态管理

> 生成日期：2026-07-22

## 总览

在不破坏现有四种记忆模式（提取/注入/回溯/衰减）的前提下，渐进式引入两个新能力。整体思路：

- **知识图谱**：基于现有 MySQL + Flyway 模式，新增3张表 + 1个Service，零额外基础设施
- **时态管理**：在现有 `memory_items` 表加3个字段 + 1个枚举，与懒衰减协同工作
- **改动热点**：主要集中在 `MemoryService.extractAndStore()` 和 `MessageContextBuilder`

---

## 一、知识图谱

### 1.1 数据模型

新增三张表，全部走 Flyway 迁移：

```sql
-- V2__memory_graph.sql

-- 用户级实体表（每个用户有独立的实体空间）
CREATE TABLE memory_entities (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT        NOT NULL,
    name        VARCHAR(200)  NOT NULL COMMENT '实体名称，如 张三/阿里/杭州',
    type        VARCHAR(30)   NOT NULL COMMENT 'PERSON/ORG/LOCATION/PRODUCT/MISC',
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_name (user_id, name),
    INDEX idx_user_type (user_id, type),
    FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 记忆-实体关联表（哪条记忆提到了哪个实体）
CREATE TABLE memory_item_entities (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    memory_item_id  BIGINT        NOT NULL,
    entity_id       BIGINT        NOT NULL,
    role            VARCHAR(20)   NOT NULL COMMENT 'SUBJECT/OBJECT/ATTRIBUTE',
    UNIQUE KEY uk_mem_entity_role (memory_item_id, entity_id, role),
    INDEX idx_entity (entity_id),
    FOREIGN KEY (memory_item_id) REFERENCES memory_items(id) ON DELETE CASCADE,
    FOREIGN KEY (entity_id) REFERENCES memory_entities(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 实体间关系表（用于多跳推理）
CREATE TABLE memory_relations (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    subject_id      BIGINT        NOT NULL COMMENT '主语实体',
    predicate       VARCHAR(100)  NOT NULL COMMENT '关系谓词，如 工作于/居住在/毕业于',
    object_id       BIGINT        NOT NULL COMMENT '宾语实体',
    source_item_id  BIGINT        NULL     COMMENT '从哪条记忆提取',
    user_id         BIGINT        NOT NULL,
    valid_from      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    valid_until     DATETIME      NULL,
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_subject (subject_id),
    INDEX idx_object (object_id),
    INDEX idx_user_predicate (user_id, predicate),
    FOREIGN KEY (subject_id) REFERENCES memory_entities(id) ON DELETE CASCADE,
    FOREIGN KEY (object_id) REFERENCES memory_entities(id) ON DELETE CASCADE,
    FOREIGN KEY (source_item_id) REFERENCES memory_items(id) ON DELETE SET NULL,
    FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 1.2 JPA 实体

新增三个实体类 `MemoryEntity`、`MemoryItemEntity`、`MemoryRelation`（结构同上表的字段映射，此处略）。

### 1.3 实体提取流程

修改 `MemoryService.extractAndStore()`，在事实提取之后增加实体提取步骤。

**修改点一：扩展提取 Prompt**

原来的 Prompt 只提取事实。改为分两步：先提取事实（不变），再对每行事实提取实体和关系。

```java
// 新增：实体提取 Prompt
String entityPrompt = """
    从以下记忆中提取实体和关系。
    规则：
    - 每行一条，格式为：主语 | 谓词 | 宾语
    - 主语/宾语是人名、地名、机构名、产品名等专有名词
    - 谓词是动作或关系，如"工作于""居住在""毕业于""拥有""喜欢"
    - 不是每句话都有实体，无实体则回复 NONE
    - 不要编造不存在的关系

    记忆：%s
    """.formatted(line);  // line 是前面提取出的单行事实
```

**修改点二：新增 GraphMemoryService**

```java
@Service
public class GraphMemoryService {

    // 核心：将新记忆挂载到实体图上
    @Transactional
    public void linkMemory(Long userId, MemoryItem memory, String extractedFacts) {
        // 1. LLM 提取 (subject, predicate, object)
        List<Triple> triples = extractTriples(extractedFacts);

        for (Triple t : triples) {
            // 2. upsert 实体
            MemoryEntity subject = getOrCreateEntity(userId, t.subject, inferType(t.subject));
            MemoryEntity object = getOrCreateEntity(userId, t.object, inferType(t.object));

            // 3. 建立 记忆-实体 关联
            linkItemToEntity(memory.getId(), subject.getId(), "SUBJECT");
            linkItemToEntity(memory.getId(), object.getId(), "OBJECT");

            // 4. 建立 实体-关系-实体
            MemoryRelation rel = new MemoryRelation();
            rel.setSubjectId(subject.getId());
            rel.setPredicate(t.predicate);
            rel.setObjectId(object.getId());
            rel.setSourceItemId(memory.getId());
            rel.setUserId(userId);
            relationRepo.save(rel);
        }
    }

    // 图形搜索：从一条记忆出发，找关联的记忆
    public List<MemoryItem> expandViaGraph(Long userId, MemoryItem seed, int maxHops) {
        Set<Long> visited = new HashSet<>();
        List<MemoryItem> results = new ArrayList<>();

        // 1. 找到这条记忆关联的实体
        List<Long> entityIds = itemEntityRepo.findEntityIdsByMemoryId(seed.getId());

        // 2. 沿关系图遍历 1 跳
        for (Long entityId : entityIds) {
            // 2a. 同一个实体关联的其他记忆
            List<Long> relatedItemIds = itemEntityRepo.findMemoryIdsByEntityId(entityId);

            // 2b. 通过关系边找到的邻接实体 → 再找它们的关联记忆
            List<MemoryRelation> outRelations = relationRepo.findBySubjectId(entityId);
            for (MemoryRelation rel : outRelations) {
                List<Long> neighborItemIds = itemEntityRepo.findMemoryIdsByEntityId(rel.getObjectId());
                relatedItemIds.addAll(neighborItemIds);
            }

            for (Long itemId : relatedItemIds) {
                if (!visited.contains(itemId) && !itemId.equals(seed.getId())) {
                    visited.add(itemId);
                    memoryRepo.findById(itemId).ifPresent(results::add);
                }
            }
        }
        return results;
    }

    record Triple(String subject, String predicate, String object) {}
}
```

### 1.4 检索集成

修改 `MessageContextBuilder` 的记忆注入部分，从单纯的"取最近20条"改为"取最近N条 + 图扩展"：

```java
// 当前：仅取最近20条
List<MemoryItem> memories = memoryService.getRecentMemoriesForContext(userId);

// 改后：取最近10条 + 每条做1跳图扩展
List<MemoryItem> seeds = memoryService.getRecentMemoriesForContext(userId, 10);
Set<Long> allIds = new HashSet<>();
for (MemoryItem seed : seeds) {
    allIds.add(seed.getId());
    List<MemoryItem> expanded = graphMemoryService.expandViaGraph(userId, seed, 1);
    for (MemoryItem m : expanded) {
        if (allIds.size() >= 20) break;
        allIds.add(m.getId());
    }
}
```

### 1.5 改动清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `V2__memory_graph.sql` | 新增 | Flyway 迁移，建3张表 |
| `model/MemoryEntity.java` | 新增 | 实体 JPA 实体 |
| `model/MemoryItemEntity.java` | 新增 | 记忆-实体关联 |
| `model/MemoryRelation.java` | 新增 | 实体间关系 |
| `repository/MemoryEntityRepository.java` | 新增 | 实体仓库 |
| `repository/MemoryItemEntityRepository.java` | 新增 | 关联仓库 |
| `repository/MemoryRelationRepository.java` | 新增 | 关系仓库 |
| `service/GraphMemoryService.java` | 新增 | 图操作逻辑 |
| `service/MemoryService.java` | 修改 | `extractAndStore()` 末尾调用 `linkMemory()` |
| `service/MessageContextBuilder.java` | 修改 | 注入时增加图扩展 |

---

## 二、时态管理

### 2.1 数据模型变更

在现有 `memory_items` 表上加三个字段：

```sql
-- V2__memory_graph.sql 同文件追加

ALTER TABLE memory_items
    ADD COLUMN valid_from  DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '事实生效时间',
    ADD COLUMN valid_until DATETIME NULL COMMENT '事实失效时间（null=当前有效）',
    ADD COLUMN superseded_by_id BIGINT NULL COMMENT '被哪条记忆取代',
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        COMMENT 'ACTIVE/SUPERSEDED/EXPIRED',
    ADD INDEX idx_status (user_id, status),
    ADD FOREIGN KEY (superseded_by_id) REFERENCES memory_items(id) ON DELETE SET NULL;
```

对应的 `MemoryItem` 实体增加字段：

```java
@Column(name = "valid_from")
private LocalDateTime validFrom;

@Column(name = "valid_until")
private LocalDateTime validUntil;

@Column(name = "superseded_by_id")
private Long supersededById;

@Enumerated(EnumType.STRING)
@Column(name = "status", length = 20)
@Builder.Default
private MemoryStatus status = MemoryStatus.ACTIVE;

public enum MemoryStatus {
    ACTIVE,      // 当前有效
    SUPERSEDED,  // 被新事实取代（时态管理）
    EXPIRED      // 衰减到期（懒衰减）
}
```

### 2.2 时态冲突检测

修改 `MemoryService.extractAndStore()`，在去重步骤之后增加时态冲突检测：

```java
// 在 extractAndStore() 中，写入新记忆前：
// 步骤：去重（已有）→ 时态冲突检测（新增）→ 写入

for (String line : extractedFacts) {
    // ... 现有去重逻辑 ...

    // === 新增：时态冲突检测 ===
    detectAndResolveTemporalConflict(userId, line, newChromaId);

    // ... 现有写入逻辑 ...
}
```

冲突检测逻辑：

```java
/**
 * 检测新事实是否与旧事实冲突。
 * 例：旧事实"用户在北京工作" vs 新事实"用户调到上海了"
 * → 标记旧事实为 SUPERSEDED，新事实正常写入为 ACTIVE。
 */
private void detectAndResolveTemporalConflict(Long userId, String newFact, String newChromaId) {
    // 1. 用 ChromaDB 搜索语义相近的旧记忆（阈值调高到 0.75，比去重的 0.85 低一档）
    var similar = chromaService.search(userId, newFact, 5).stream()
            .filter(h -> h.score() > 0.75 && h.score() < 0.90) // 相似但不完全相同
            .toList();

    if (similar.isEmpty()) return;

    // 2. 用 LLM 判断是否构成冲突
    //    （同一实体、同一属性、不同值 → 冲突）
    for (var hit : similar) {
        MemoryItem oldItem = memoryRepo.findByChromaId(hit.chromaId()).orElse(null);
        if (oldItem == null || oldItem.getStatus() != MemoryStatus.ACTIVE) continue;

        boolean isConflict = llmCheckConflict(oldItem.getOriginalValue(), newFact);
        if (isConflict) {
            // 3. 标记旧事实为 SUPERSEDED
            oldItem.setStatus(MemoryStatus.SUPERSEDED);
            oldItem.setValidUntil(LocalDateTime.now());
            oldItem.setSupersededById(supersededById); // 需要在写入新记忆后回填
            memoryRepo.save(oldItem);
            log.info("时态冲突已解决: old={}, new={}", oldItem.getId(), newFact);
        }
    }
}

private boolean llmCheckConflict(String oldFact, String newFact) {
    String prompt = """
        判断以下两句话是否描述同一个事实，且新信息构成对旧信息的更新/取代。
        只回复 YES 或 NO。
        旧: %s
        新: %s
        """.formatted(oldFact, newFact);
    String result = llmService.chatSync(prompt);
    return "YES".equalsIgnoreCase(result != null ? result.trim() : "NO");
}
```

### 2.3 时态注入过滤

修改 `getRecentMemoriesForContext()`，增加状态过滤：

```java
// 当前：查 FULL + BRIEF + enabled
// 改后：增加 status != EXPIRED 过滤
public List<MemoryItem> getRecentMemoriesForContext(Long userId) {
    // 只取 ACTIVE 状态（SUPERSEDED 和 EXPIRED 都不注入）
    List<MemoryItem> memories = memoryRepo.findTopNActive(userId,
            List.of(DetailLevel.FULL, DetailLevel.BRIEF),
            List.of(MemoryStatus.ACTIVE),
            PageRequest.of(0, memoryProperties.getInject().getRecentCount(),
                    Sort.by(Sort.Direction.DESC, "lastAccessedAt")));

    return memories.stream()
            .filter(m -> !checkAndApplyDecay(m)) // 懒衰减保留
            .toList();
}
```

同时修改 Repository：

```java
@Query("SELECT m FROM MemoryItem m WHERE m.userId = :userId " +
       "AND m.enabled = true AND m.status IN :statuses " +
       "AND m.detailLevel IN :levels " +
       "ORDER BY m.lastAccessedAt DESC")
List<MemoryItem> findTopNActive(@Param("userId") Long userId,
                                @Param("levels") List<DetailLevel> levels,
                                @Param("statuses") List<MemoryStatus> statuses,
                                Pageable pageable);
```

### 2.4 与懒衰减的协同

两种遗忘机制互补：

| 场景 | 懒衰减处理 | 时态管理处理 |
|------|-----------|-------------|
| 用户3天没聊工作 → 地址记忆衰减 | FULL→BRIEF（懒衰减触发） | 不处理（仍是当前事实） |
| 用户主动说"我换工作了" | 旧记忆可能还在 FULL/BRIEF | 旧事实 → SUPERSEDED（时态触发） |
| 用户3天没聊工作 + 之前说换了工作 | 新事实 FULL，旧事实已 SUPERSEDED | SUPERSEDED 的不参与衰减 |

懒衰减的 `checkAndApplyDecay()` 中增加状态判断：

```java
private boolean checkAndApplyDecay(MemoryItem item) {
    // SUPERSEDED 的记忆不参与衰减（已经在时态管理中被标记失效）
    if (item.getStatus() == MemoryStatus.SUPERSEDED) {
        return false; // 不衰减但也注入时会被过滤掉
    }
    // ... 现有衰减逻辑不变 ...
    // TITLE → 遗忘时，同时将 status 设为 EXPIRED
    if (now.isAfter(threshold)) {
        item.setStatus(MemoryStatus.EXPIRED);
        // ... 删除逻辑不变 ...
    }
}
```

### 2.5 改动清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `V2__memory_graph.sql` | 修改 | ALTER TABLE memory_items 加3字段+1枚举 |
| `model/MemoryItem.java` | 修改 | 增加 validFrom/validUntil/supersededById/status |
| `repository/MemoryItemRepository.java` | 修改 | 增加按 status 过滤的查询 |
| `service/MemoryService.java` | 修改 | `extractAndStore()` 增加冲突检测；`getRecentMemoriesForContext()` 增加状态过滤；`checkAndApplyDecay()` 增加状态判断 |

---

## 三、影响范围总览

```
改动文件数: 14
├── 新增: 7
│   ├── V2__memory_graph.sql         (Flyway 迁移)
│   ├── MemoryEntity.java            (JPA 实体)
│   ├── MemoryItemEntity.java        (JPA 实体)
│   ├── MemoryRelation.java          (JPA 实体)
│   ├── MemoryEntityRepository.java  (Spring Data)
│   ├── MemoryItemEntityRepository.java
│   ├── MemoryRelationRepository.java
│   └── GraphMemoryService.java      (图操作服务)
├── 修改: 4
│   ├── MemoryItem.java              (加字段)
│   ├── MemoryItemRepository.java    (加查询)
│   ├── MemoryService.java           (加冲突检测 + 状态过滤)
│   └── MessageContextBuilder.java   (加图扩展)
└── 不涉及: 其余全部现有文件

现有四种模式影响:
├── 模式1 (自动提取): 增加实体提取 + 时态冲突检测（新增步骤，不改变原有流程）
├── 模式2 (默认注入): 增加图扩展 + 状态过滤（增强，非重写）
├── 模式3 (按需回溯): 不受影响（语义搜索后仍走现有恢复逻辑）
└── 模式4 (懒衰减):  增加 SUPERSEDED 豁免（微调，不改变衰减规则）
```

---

## 四、实施步骤

| 阶段 | 内容 | 预估复杂度 | 依赖 |
|------|------|-----------|------|
| **Step 1** | 创建 Flyway V2 迁移（建表+改表） | 低 | 无 |
| **Step 2** | 创建 JPA 实体和 Repository | 低 | Step 1 |
| **Step 3** | 实现 `GraphMemoryService` | 中 | Step 2 |
| **Step 4** | 修改 `MemoryService.extractAndStore()` 集成实体提取 | 中 | Step 3 |
| **Step 5** | 实现时态冲突检测 | 中 | Step 2 |
| **Step 6** | 修改 `MessageContextBuilder` 集成图扩展 | 低 | Step 3 |
| **Step 7** | 修改 `getRecentMemoriesForContext` 增加状态过滤 | 低 | Step 2 |
| **Step 8** | 编写集成测试 | 中 | Step 4-7 |
| **Step 9** | 用 LoCoMo/LongMemEval 评测对比 | 高 | Step 8 |

建议分两个 PR 提交：PR1 = 知识图谱（Step 1-4），PR2 = 时态管理（Step 5-7）。

---

## 五、关键设计决策

### 为什么图存储用 MySQL 而不是 Neo4j/Kuzu？

| 因素 | MySQL | Kuzu (嵌入式) | Neo4j |
|------|-------|-------------|-------|
| 基础设施 | 已有 | 需加依赖 | 需独立部署 |
| 部署复杂度 | 零 | 低（嵌入式JAR） | 高（独立服务） |
| 图查询能力 | JOIN 模拟，够用 | 原生 Cypher 子集 | 完整 Cypher |
| 初次实现成本 | 极低 | 中 | 高 |
| 后续迁移成本 | 到 Kuzu 容易 | 已到位 | 已到位 |

选择 MySQL 是为了**最快落地、零运维负担**。当关系数据量超过百万级时，可无缝迁移到 Kuzu（同是嵌入式），SQL 查询可以 1:1 翻译成 Cypher。

### 为什么冲突检测用 LLM 而不是纯规则？

纯规则（如字符串相似度）在中文语境下准确率不足：
- "我在北京工作" vs "我调到上海了" → 规则难以判断是同一属性的更新
- "我在北京工作" vs "我大学在武汉读的" → 规则容易误判为冲突

LLM 可以用一次轻量调用（< 100 tokens）精确判断，成本可忽略。

### 为什么 SUPERSEDED 不参与衰减？

被新事实主动取代的旧事实，语义上已经是"历史版本"，不值得再占衰减资源。直接标记为 SUPERSEDED + 设置 validUntil，比等时间衰减更准确。
