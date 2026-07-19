长期记忆（Long Memory）技术方案 — v3 人类记忆模型
==================================================

> 项目: AI Chat (Spring Boot 4.0.6 + Java 17 + MySQL 8.0)
> 原则: 基于现有架构纯增量开发，不破坏任何现有功能
> 核心思路: 模拟人类记忆系统 — 时间衰减 + 访问强化 + 阶梯抽象 + 按需回溯
> 底层复用: ChromaDB + SiliconFlow Embedding + ChatService.chatSync


## 一、现有基础设施分析

### 1.1 已有可复用的核心组件

```
┌─────────────────────────────────────────────────────────┐
│  向量数据库                                              │
│  ├─ ChromaDB (docker-compose, 端口 8000) — 已部署运行    │
│  ├─ ChromaDBService — 完整封装                           │
│  │   ├─ createCollection(kbId)                           │
│  │   ├─ addChunks(kbId, chunks) — 写入向量+元数据         │
│  │   ├─ query(kbId, text, topK) — 语义检索返回结果        │
│  │   └─ deleteByDocument(kbId, docId) — 按条件删除        │
│  └─ 元数据支持: document_id, chunk_index, kb_id, file_name│
├─────────────────────────────────────────────────────────┤
│  嵌入模型                                                │
│  ├─ SiliconFlowEmbeddingService — 已封装                  │
│  │   ├─ embed(text) → List<Double>                       │
│  │   └─ embedBatch(texts) → List<List<Double>>           │
│  └─ 模型: BAAI/bge-large-zh-v1.5 (1024维)                │
├─────────────────────────────────────────────────────────┤
│  LLM 调用能力                                            │
│  ├─ ChatService.chatSync(prompt) — 同步调用生成文本       │
│  ├─ ChatService.chatStream() — 流式 SSE 调用              │
│  └─ ModelConfig 表 — 多模型配置，可复用                    │
├─────────────────────────────────────────────────────────┤
│  SSE 流式回复框架                                        │
│  ├─ chatStream() → buildMessagesArray → streamDeepSeek → │
│  │   (SSE发送) → saveMessage → deductTokens → [异步钩子]  │
│  └─ 回复完成后可挂入异步钩子                               │
└─────────────────────────────────────────────────────────┘
```

### 1.2 buildMessagesArray 当前注入管线 (ChatService 行 113-207)

```
  1. Prompt (用户个人提示词)
  2. 知识库检索 (ChromaDB 语义检索)
  3. 最近30条历史消息
  4. 联网搜索结果
  5. 图片识别描述
  6. 当前用户消息
```

### 1.3 ChatRequest 现有字段

```java
message, promptId, modelConfigId, webSearchEnabled, imageDescription, knowledgeBaseId
```

### 1.4 ChatMessage 结构

```java
id, userId, conversationId, userMessage, aiReply, timestamp
```


## 二、人类记忆模型 — 核心设计

### 2.1 设计原则

```
不做的事:
  ❌ 不引入 mem0 / LangChain4j 等新外部服务或依赖
  ❌ 不改变 ChatService 核心流程
  ❌ 不阻塞对话（记忆操作全异步）

要做的事:
  ✅ 模拟人类记忆的四个核心特征:
     时间衰减、访问强化、阶梯抽象、按需回溯
  ✅ 复用 ChromaDB + SiliconFlow Embedding + ChatService.chatSync
  ✅ MySQL 存储记忆生命周期状态，ChromaDB 存储全文向量
  ✅ 自动静默提取（不打断对话）
```

### 2.2 记忆生命周期

```
                    记忆出生 (LLM 自动提取)
                         │
                         ▼
              ┌──────────────────────┐
              │  清晰期 (0-3天)       │ ← 原文全文，细节清晰
              │  detail_level = FULL │   每次对话自动注入
              │                      │   可被访问重置计时器
              └──────┬───────────────┘
                     │ 3天未被访问
                     ▼
              ┌──────────────────────┐
              │  模糊期 (3-7天)       │ ← LLM 压缩为中等摘要(~200字)
              │  detail_level = BRIEF│   仍可注入，但信息量降低
              │                      │
              └──────┬───────────────┘
                     │ 7天未被访问
                     ▼
              ┌──────────────────────┐
              │  轮廓期 (7-14天)      │ ← LLM 压缩为一行概要(~50字)
              │  detail_level = TITLE│   不主动注入，仅被查询时召回
              │                      │
              └──────┬───────────────┘
                     │ 14天未被访问
                     ▼
              ┌──────────────────────┐
              │  遗忘 (删除)          │ ← 从 ChromaDB + MySQL 中移除
              │                      │
              └──────────────────────┘

          ▲ 访问强化: 任何时候被查询/注入，计时器归零，跳回清晰期
```

### 2.3 记忆的四种操作模式

```
┌─────────────────────────────────────────────────────────────┐
│  模式1: 自动提取 (静默)                                      │
│  触发: AI回复完成后                                          │
│  流程: 用户消息+AI回复 → LLM提取关键事实 → ChromaDB+MySQL写入 │
│  感知: 用户无感知，不中断对话                                 │
├─────────────────────────────────────────────────────────────┤
│  模式2: 默认注入 (时间倒序)                                  │
│  触发: 每次对话开始时                                         │
│  流程: 取最近N条清晰期/模糊期记忆 → system prompt注入         │
│  效果: "刚发生的事记得最清楚"                                 │
├─────────────────────────────────────────────────────────────┤
│  模式3: 按需回溯 (语义搜索)                                  │
│  触发: 用户提问涉及历史内容时                                 │
│  流程: 当前消息向量化 → ChromaDB全库搜索 → 返回匹配记忆       │
│  效果: "被问到的事，能想起来，并重新巩固"                     │
│        被查到的记忆 → 访问时间归零 → 跳回清晰期               │
├─────────────────────────────────────────────────────────────┤
│  模式4: 衰减维护 (定时任务)                                  │
│  触发: 每日凌晨定时扫描                                       │
│  流程: 检查每条记忆的last_accessed → 按时间区间触发压缩/删除  │
│  效果: 模拟遗忘曲线，防止记忆无限膨胀                         │
└─────────────────────────────────────────────────────────────┘
```

### 2.4 上下文注入顺序

```
ChatService.buildMessagesArray() 注入顺序:
  1. Prompt (用户个人提示词)                              ← 已有
  2. 长期记忆 — 最近N条清晰期/模糊期记忆 (时间倒序)         ← 新增(模式2)
  3. 对话摘要 (MySQL conversation_summaries)              ← 新增
  4. 知识库检索 (ChromaDB 语义检索 kb_{kbId})              ← 已有
  5. 联网搜索结果 (Tavily/千帆)                            ← 已有
  6. 图片识别描述                                          ← 已有
  7. 最近30条历史消息                                     ← 已有
  8. 当前用户消息                                          ← 已有
```


## 三、MySQL 新增表

```sql
-- 对话摘要表
CREATE TABLE conversation_summaries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT NOT NULL UNIQUE,
    summary TEXT NOT NULL,
    message_count_at_generation INT NOT NULL,
    version INT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE
);

-- 长期记忆表（MySQL 存放生命周期状态，ChromaDB 存放全文向量）
CREATE TABLE memory_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    chroma_id VARCHAR(100) NOT NULL COMMENT 'ChromaDB 中对应的文档ID',

    -- 记忆内容
    `value` TEXT NOT NULL COMMENT '当前记忆文本(经阶梯压缩后的版本)',

    -- 生命周期状态
    detail_level VARCHAR(20) NOT NULL DEFAULT 'FULL'
        COMMENT 'FULL(原文)/BRIEF(200字摘要)/TITLE(一行50字)',
    source VARCHAR(30) NOT NULL DEFAULT 'AUTO' COMMENT 'AUTO/MANUAL',

    -- 人类记忆核心: 时间轴
    created_at DATETIME NOT NULL COMMENT '记忆出生时间',
    last_accessed_at DATETIME NOT NULL COMMENT '最后被访问/查询/注入的时间',
    access_count INT NOT NULL DEFAULT 0 COMMENT '被访问次数',

    -- 元数据
    conversation_id BIGINT NULL COMMENT '来源对话',
    enabled TINYINT(1) NOT NULL DEFAULT 1,

    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE SET NULL,
    INDEX idx_user_dl_time (user_id, detail_level, last_accessed_at DESC),
    INDEX idx_user_enabled (user_id, enabled),
    INDEX idx_decay_check (detail_level, last_accessed_at),
    UNIQUE KEY uk_chroma_id (chroma_id)
);
```


## 四、application.properties 追加

```properties
# ========== 记忆衰减阈值 ==========
memory.decay.fresh-days=3
memory.decay.brief-days=7
memory.decay.forget-days=14
# 注入上下文的最近记忆条数
memory.inject.recent-count=20
# 按需回溯的搜索数量
memory.search.top-k=10
```


## 五、ChromaDB Collection 设计

### 5.1 记忆 Collection

```
Collection 命名: mem_{userId}

每条文档 = 一条记忆的全文向量:
  {
    "id":       "mem_{memoryItem.id}",
    "document": "用户偏好React 18 + TypeScript开发，不喜欢Redux",
    "metadata": {
      "mysql_id":      "5",
      "detail_level":  "FULL",
      "last_accessed": "2026-06-20T10:30:00",
      "access_count":  "3"
    },
    "embedding": [0.123, -0.456, ...]
  }
```

### 5.2 与现有 ChromaDBService 的关系

```
ChromaDB (端口 8000)
     │
     ├── Collection: kb_{kbId}        ← RAG 知识库 (已有)
     │
     └── Collection: mem_{userId}     ← 长期记忆 (新增)

共用底层: ChromaDBService 的 HTTP 调用能力
新增封装: MemoryChromaService (适配 userId 维度的 Collection 管理)
```


## 六、核心代码设计

### 6.1 MemoryChromaService — ChromaDB 操作层

```java
@Service
public class MemoryChromaService {
    private final RestTemplate restTemplate;       // 直接调 ChromaDB HTTP API
    private final SiliconFlowEmbeddingService embeddingService;

    /** 懒创建用户记忆 Collection */
    public void ensureCollection(Long userId) { ... }

    /** 添加记忆: 文本 → 向量化 → 写入 ChromaDB，返回 chroma_id */
    public String addMemory(Long userId, String text, Map<String, String> metadata) { ... }

    /** 语义搜索: 用户提问 → 向量化 → ChromaDB query → 返回匹配结果 */
    public List<MemoryHit> search(Long userId, String query, int topK) { ... }

    /** 更新记忆文本 (阶梯压缩时用，需重新向量化) */
    public void updateMemory(Long userId, String chromaId, String newText) { ... }

    /** 删除单条记忆 */
    public void deleteMemory(Long userId, String chromaId) { ... }

    /** 清空用户全部记忆 */
    public void deleteAll(Long userId) { ... }
}

record MemoryHit(String chromaId, String document, double score, Map<String, String> metadata) {}
```

### 6.2 MemoryService — 记忆业务逻辑层

```java
@Service
@Slf4j
public class MemoryService {
    private final MemoryChromaService chromaService;
    private final MemoryItemRepository memoryRepo;
    private final ChatService chatService;

    // ==================== 模式1: 自动提取 ====================

    @Async
    public void extractAndStore(Long userId, Long conversationId,
                                String userMessage, String aiReply) {
        String prompt = """
            从以下对话中提取关于用户的值得长期记住的关键信息。
            规则:
            - 只提取有价值的事实、偏好、习惯、重要事件
            - 每行一条，格式直接写事实描述
            - 闲聊、问候等无信息量的对话回复 "NONE"

            用户: %s
            AI: %s
            """.formatted(userMessage, aiReply);

        String result = chatService.chatSync(prompt);
        if ("NONE".equals(result.trim())) return;

        for (String line : result.split("\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            // 去格式前缀 "- " "1. " 等
            line = line.replaceFirst("^[-*\\d.]+\\s*", "");

            String chromaId = chromaService.addMemory(userId, line,
                Map.of("conversation_id", String.valueOf(conversationId)));

            memoryRepo.save(MemoryItem.builder()
                .userId(userId).chromaId(chromaId).value(line)
                .detailLevel(DetailLevel.FULL)
                .source("AUTO")
                .conversationId(conversationId)
                .lastAccessedAt(LocalDateTime.now())
                .build());
        }
    }

    // ==================== 模式2: 默认注入 ====================

    /** 获取最近N条清晰期+模糊期记忆，时间倒序 */
    public List<MemoryItem> getRecentMemoriesForContext(Long userId, int n) {
        return memoryRepo.findTopNEnabled(userId,
            List.of(DetailLevel.FULL, DetailLevel.BRIEF),
            PageRequest.of(0, n, Sort.by("lastAccessedAt").descending()));
    }

    // ==================== 模式3: 按需回溯 ====================

    /** 用户问起历史内容时，语义搜索全库 */
    public List<MemoryItem> searchMemories(Long userId, String query) {
        var hits = chromaService.search(userId, query, 10);
        List<MemoryItem> results = new ArrayList<>();
        for (var hit : hits) {
            memoryRepo.findByChromaId(hit.chromaId()).ifPresent(item -> {
                // 访问强化: 被查询到 = 记忆被"回忆"，重置计时器
                item.setLastAccessedAt(LocalDateTime.now());
                item.setAccessCount(item.getAccessCount() + 1);
                // 如果已衰减到 BRIEF/TITLE，恢复到 FULL
                if (item.getDetailLevel() != DetailLevel.FULL) {
                    item.setDetailLevel(DetailLevel.FULL);
                    // ChromaDB 中也需要恢复原文
                    chromaService.updateMemory(userId, item.getChromaId(), hit.document());
                }
                memoryRepo.save(item);
                results.add(item);
            });
        }
        return results;
    }

    // ==================== 模式4: 衰减维护 ====================

    /** 每日定时任务: 扫描记忆，执行阶梯衰减 */
    @Scheduled(cron = "0 0 3 * * ?")  // 凌晨3点
    public void decayMaintenance() {
        LocalDateTime now = LocalDateTime.now();

        // FULL → BRIEF: 3天未访问
        List<MemoryItem> toBrief = memoryRepo.findByDetailLevelAndLastAccessedBefore(
            DetailLevel.FULL, now.minusDays(3));
        for (var item : toBrief) {
            String compressed = compressWithLLM(item.getValue(), DetailLevel.BRIEF);
            item.setValue(compressed);
            item.setDetailLevel(DetailLevel.BRIEF);
            memoryRepo.save(item);
            chromaService.updateMemory(item.getUserId(), item.getChromaId(), compressed);
        }

        // BRIEF → TITLE: 7天未访问
        List<MemoryItem> toTitle = memoryRepo.findByDetailLevelAndLastAccessedBefore(
            DetailLevel.BRIEF, now.minusDays(7));
        for (var item : toTitle) {
            String compressed = compressWithLLM(item.getValue(), DetailLevel.TITLE);
            item.setValue(compressed);
            item.setDetailLevel(DetailLevel.TITLE);
            memoryRepo.save(item);
            chromaService.updateMemory(item.getUserId(), item.getChromaId(), compressed);
        }

        // TITLE → 遗忘: 14天未访问
        List<MemoryItem> toForget = memoryRepo.findByDetailLevelAndLastAccessedBefore(
            DetailLevel.TITLE, now.minusDays(14));
        for (var item : toForget) {
            chromaService.deleteMemory(item.getUserId(), item.getChromaId());
            memoryRepo.delete(item);
        }
    }

    /** 用LLM压缩记忆文本 */
    private String compressWithLLM(String original, DetailLevel target) {
        String prompt = switch (target) {
            case BRIEF -> "将以下信息压缩为200字以内的摘要，保留核心事实：\n" + original;
            case TITLE -> "将以下信息压缩为一句话（50字以内），只保留最核心的关键词：\n" + original;
            default    -> original;
        };
        return chatService.chatSync(prompt);
    }

    // ==================== 手动CRUD ====================

    public List<MemoryItem> listAll(Long userId) {
        return memoryRepo.findByUserIdOrderByLastAccessedAtDesc(userId);
    }

    public MemoryItem addManual(Long userId, String value) {
        String chromaId = chromaService.addMemory(userId, value, Map.of("source", "manual"));
        return memoryRepo.save(MemoryItem.builder()
            .userId(userId).chromaId(chromaId).value(value)
            .detailLevel(DetailLevel.FULL).source("MANUAL")
            .lastAccessedAt(LocalDateTime.now()).build());
    }

    public void update(Long id, String newValue) {
        memoryRepo.findById(id).ifPresent(item -> {
            item.setValue(newValue);
            item.setDetailLevel(DetailLevel.FULL);
            item.setLastAccessedAt(LocalDateTime.now());
            memoryRepo.save(item);
            chromaService.updateMemory(item.getUserId(), item.getChromaId(), newValue);
        });
    }

    public void toggleEnabled(Long id, boolean enabled) {
        memoryRepo.findById(id).ifPresent(item -> {
            item.setEnabled(enabled);
            memoryRepo.save(item);
        });
    }

    public void delete(Long id) {
        memoryRepo.findById(id).ifPresent(item -> {
            chromaService.deleteMemory(item.getUserId(), item.getChromaId());
            memoryRepo.delete(item);
        });
    }
}
```

### 6.3 MemoryItem — JPA 实体

```java
@Entity
@Table(name = "memory_items")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class MemoryItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "chroma_id", nullable = false, length = 100)
    private String chromaId;

    @Column(name = "`value`", nullable = false, columnDefinition = "TEXT")
    private String value;

    @Enumerated(EnumType.STRING)
    @Column(name = "detail_level", nullable = false, length = 20)
    @Builder.Default
    private DetailLevel detailLevel = DetailLevel.FULL;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String source = "AUTO";

    @Column(name = "last_accessed_at", nullable = false)
    @Builder.Default
    private LocalDateTime lastAccessedAt = LocalDateTime.now();

    @Column(name = "access_count", nullable = false)
    @Builder.Default
    private Integer accessCount = 0;

    @Column(name = "conversation_id")
    private Long conversationId;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    // 无 updated_at — 记忆的生命周期由 lastAccessedAt 驱动
}

enum DetailLevel {
    FULL,   // 清晰期: 原文全文
    BRIEF,  // 模糊期: ~200字摘要
    TITLE   // 轮廓期: 一行~50字
}
```

### 6.4 SummaryService — 对话摘要

```java
@Service
public class SummaryService {
    // 消息 ≥ 20 条首次生成摘要，之后每 +10 条刷新
    // 保留最近 10 条完整消息（不被摘要覆盖）

    @Async
    public void checkAndGenerate(Long conversationId) { ... }

    private void generate(Long conversationId, long msgCount) {
        // 1. 获取全部消息，截掉最近10条
        // 2. 拼接 Prompt（包含上一版摘要作增量）
        // 3. chatService.chatSync()
        // 4. 写入 conversation_summaries 表
    }
}
```

### 6.5 ChatService 改造

#### buildMessagesArray() — 注入记忆和摘要

```java
/**
 * 构建消息数组 (在 Prompt 之后、知识库之前插入记忆层)
 */
private ArrayNode buildMessagesArray(Long conversationId, Long promptId,
                                      String userMessage, boolean webSearchEnabled,
                                      String imageDescription, Long knowledgeBaseId,
                                      Long userId, Boolean longMemoryEnabled) {
    ArrayNode messagesArray = objectMapper.createArrayNode();
    List<ChatMessage> history = getRecentHistory(conversationId);

    // 1. 注入 Prompt (已有, 不变)

    // 2. 注入长期记忆 — 最近N条 (时间倒序)
    if (userId != null && Boolean.TRUE.equals(longMemoryEnabled)) {
        List<MemoryItem> recentMemories =
            memoryService.getRecentMemoriesForContext(userId, 20);
        if (!recentMemories.isEmpty()) {
            StringBuilder sb = new StringBuilder("【关于用户的已知信息（最近）】\n");
            for (var m : recentMemories) {
                sb.append("- ").append(m.getValue()).append("\n");
            }
            addSystemMessage(messagesArray, sb.toString());

            // 注入即访问: 刷新 lastAccessedAt
            for (var m : recentMemories) {
                m.setLastAccessedAt(LocalDateTime.now());
                m.setAccessCount(m.getAccessCount() + 1);
                memoryRepo.save(m);
            }
        }
    }

    // 3. 注入对话摘要
    if (Boolean.TRUE.equals(longMemoryEnabled)) {
        ConversationSummary summary = summaryRepo.findByConversationId(conversationId);
        if (summary != null) {
            addSystemMessage(messagesArray, "【历史对话摘要】\n" + summary.getSummary());
        }
    }

    // 4. 注入知识库检索 (已有, 不变)

    // 5-8. 历史消息、搜索、图片、当前消息 (已有, 不变)

    return messagesArray;
}
```

#### streamDeepSeek() — 回复后异步触发

```java
// 在保存消息、扣费完成后:
if (userId != null && Boolean.TRUE.equals(longMemoryEnabled)) {
    memoryService.extractAndStore(userId, conversationId, userMessage, completeResponse);
    summaryService.checkAndGenerate(conversationId);
}
```

### 6.6 ChatRequest 扩展

```java
@Data
public class ChatRequest {
    // 已有字段
    private String message;
    private Long promptId;
    private Long modelConfigId;
    private Boolean webSearchEnabled;
    private String imageDescription;
    private Long knowledgeBaseId;

    // 新增
    private Boolean longMemoryEnabled; // 默认 true
}
```


## 七、API 设计

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/memory/list?userId=` | 获取用户所有记忆 (时间倒序) |
| POST | `/api/memory/add` | 手动添加记忆 |
| PUT | `/api/memory/{id}` | 编辑记忆 |
| PUT | `/api/memory/{id}/toggle` | 启用/禁用 |
| DELETE | `/api/memory/{id}` | 删除记忆 |
| DELETE | `/api/memory/clear?userId=` | 清空全部记忆 |
| POST | `/api/memory/search` | 手动检索记忆 (模式3) |


## 八、文件清单

```
新增 (8 个文件):
  src/main/java/com/example/aichat/
  ├── model/ConversationSummary.java
  ├── model/MemoryItem.java            ← 含 DetailLevel 枚举
  ├── repository/ConversationSummaryRepository.java
  ├── repository/MemoryItemRepository.java
  ├── service/MemoryChromaService.java  ← ChromaDB 记忆操作
  ├── service/MemoryService.java       ← 生命周期管理 + 4种模式
  ├── service/SummaryService.java      ← 对话摘要
  └── controller/MemoryController.java

修改 (2 个文件):
  service/ChatService.java   ← buildMessagesArray() + streamDeepSeek()
  dto/ChatRequest.java       ← +longMemoryEnabled

零新增依赖, pom.xml 不变, docker-compose.yml 不变
```


## 九、实施步骤

1. SQL 建表 `conversation_summaries`、`memory_items`
2. 编写实体 `ConversationSummary`、`MemoryItem` (含 DetailLevel)
3. 编写 Repository
4. 编写 `MemoryChromaService` (复用 ChromaDB HTTP API 底层)
5. 编写 `MemoryService` (提取 + 注入 + 回溯 + 衰减 + CRUD)
6. 编写 `SummaryService` (增量摘要)
7. 编写 `MemoryController`
8. 改造 `ChatService.buildMessagesArray()` — 注入记忆 (模式2) + 摘要
9. 改造 `ChatService.streamDeepSeek()` — 回复后异步提取 (模式1)
10. `ChatRequest` 新增 `longMemoryEnabled`
11. 前端：记忆管理页 + 对话记忆开关


## 十、总结：人类记忆模型 vs 传统方案

| 特征 | 传统记忆方案 | 本方案 (人类记忆模型) |
|------|------------|---------------------|
| 记忆提取 | LLM 一次性提取 | LLM 自动静默提取 |
| 默认注入 | 语义搜索 Top-K | **时间倒序最近N条** |
| 深层回忆 | 无 | **用户提问时沿时间轴向后搜索** |
| 遗忘机制 | 无 | **阶梯衰减: FULL → BRIEF → TITLE → 删除** |
| 强化机制 | 无 | **访问即刷新: 被查过的记忆回归清晰期** |
| 存储 | 单层 | **MySQL(生命周期) + ChromaDB(全文向量) 双层** |
| 用户体验 | 被动存储 | **像人的记忆一样自然消退和唤起** |
