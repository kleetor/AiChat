# HanaChat 记忆系统分析（更新版）

> 最后更新：2026-07-22
> 基于 V9 知识图谱 + 六项优化完成后的完整架构

## 一、系统概览

HanaChat 记忆系统是一个**仿人类记忆模型**，实现三层记忆架构 + 四种操作模式，并在此基础上扩展了知识图谱、时态管理、多信号混合检索和提示词级隔离。

### 技术栈

| 组件 | 技术 | 用途 |
|------|------|------|
| 关系存储 | MySQL 8.0 + Spring Data JPA | 记忆元数据、实体、关系、衰减状态 |
| 向量存储 | ChromaDB（嵌入式） | 语义检索，每用户独立 Collection `mem_{userId}` |
| 关键词检索 | Apache Lucene 9.12（SmartChineseAnalyzer） | BM25 关键词匹配 |
| 嵌入模型 | SiliconFlow BAAI/bge-large-zh-v1.5 | 1024 维文本向量化 |
| Rerank | SiliconFlow BAAI/bge-reranker-v2-m3 | Cross-Encoder 精排 |
| 记忆 LLM | DeepSeek（可配置） | 事实提取、实体抽取、摘要压缩、冲突判定、三元组提取、消歧建议 |

### 记忆空间模型

```
记忆空间 = userId × (promptId | NULL=共享)

promptId=NULL  → 所有角色可见（姓名、偏好等通用信息）
promptId=X     → 仅角色 X 可见（角色特定信息）
```

---

## 二、三层记忆架构

```
第一层（短期）: chat_messages 表
    最近 30 条原始对话消息，直接注入 LLM 上下文
         |
         v  LLM 增量压缩（SummaryService）
第二层（中期）: conversation_summaries 表
    对话摘要，超过 20 轮时 LLM 增量生成
         |
         v  LLM 提取关键事实（MemoryService 模式1）
第三层（长期）: memory_items (MySQL) + ChromaDB + Lucene BM25
    结构化事实条目，支持混合检索 + 时间衰减 + 知识图谱关联
```

---

## 三、四种记忆模式

| 模式 | 名称 | 触发时机 | 功能 |
|------|------|----------|------|
| 模式1 | 自动提取 | 每次对话完成后异步 | LLM 提取事实 → 去重 → 双写 ChromaDB+MySQL → 构建知识图谱（含反向边、去重、LLM 类型标注） → 时态冲突检测（级联关系过期） → BM25 索引 |
| 模式2 | 默认注入 | 每次发送消息时同步 | 取最近 20 条 ACTIVE 记忆 + 1 跳图扩展注入 System Prompt |
| 模式3 | 按需回溯 | 用户主动搜索 | 三路混合检索（含图扩展实体匹配） → RRF 融合 → Cross-Encoder Rerank → 恢复衰减记忆 |
| 模式4 | 懒衰减 | 读取记忆时实时检查 | FULL(3天)→BRIEF(7天)→TITLE(14天)→删除，手动记忆豁免，SUPERSEDED 免衰减 |

---

## 四、存储结构

### 4.1 memory_items 表

```sql
CREATE TABLE memory_items (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id           BIGINT       NOT NULL,
    chroma_id         VARCHAR(100) NOT NULL,       -- ChromaDB 关联
    value             TEXT         NOT NULL,       -- 当前文本（可能已被压缩）
    original_value    TEXT         NULL,           -- 原始全文（恢复用）
    detail_level      VARCHAR(20)  NOT NULL,       -- FULL / BRIEF / TITLE
    source            VARCHAR(30)  NOT NULL,       -- AUTO / MANUAL
    status            VARCHAR(20)  NOT NULL,       -- ACTIVE / SUPERSEDED / EXPIRED
    valid_from        DATETIME     NOT NULL,       -- 事实生效时间
    valid_until       DATETIME     NULL,           -- 事实失效时间
    superseded_by_id  BIGINT       NULL,           -- 被哪条新记忆取代
    prompt_id         BIGINT       NULL,           -- 角色隔离（NULL=共享）
    last_accessed_at  DATETIME     NOT NULL,       -- 驱动懒衰减的时间锚点
    access_count      INT          NOT NULL DEFAULT 0,
    conversation_id   BIGINT       NULL,
    created_at        DATETIME     NOT NULL,
    enabled           TINYINT(1)   NOT NULL DEFAULT 1,
    ...
);
```

### 4.2 知识图谱三表

```
memory_entities          -- 用户级命名实体（张三/阿里/杭州），含 LLM 输出的实体类型
memory_item_entities     -- 记忆↔实体关联（带 SUBJECT/OBJECT 角色）
memory_relations         -- 实体间关系（张三-[工作于]->阿里，含 valid_from/until 时态控制）
```

关系表支持以下优化：
- **去重**：`(subject_id, predicate, object_id)` 查重后更新而非重复插入
- **时态**：记忆被取代时级联设 `valid_until`，图遍历仅走有效边
- **反向边**：正向关系自动追加反向边（工作于↔拥有员工等 6 对），图遍历方向完整

### 4.3 BM25 索引

Lucene 内存索引，存储 `(itemId, userId, promptId, text)`，`SmartChineseAnalyzer` 中文分词。

### 4.4 ChromaDB

- Collection 命名：`mem_{userId}`
- 向量维度：1024
- 文档结构：`(id, embedding, document, metadata)`

---

## 五、检索管线（模式3 完整流程）

```
用户搜索 query
  │
  ├── ChromaDB 语义搜索 (top30)       ─┐
  ├── Lucene BM25 关键词 (top30)       ─┼── 三路并行召回
  └── 知识图谱实体匹配 (top30)          ─┘
         │
         ├── LLM 提取 query 中的专有名词
         ├── memory_entities 精确匹配
         ├── 直接关联记忆召回（权重 1.0）
         └── 1 跳图扩展：沿关系找邻接实体，召回其关联记忆（权重 0.5）
  │
  ▼
  RRF 融合（Reciprocal Rank Fusion, K=60）
  score(doc) = Σ 1/(K + rank_i)
  │
  ▼
  取 top30 候选 → Cross-Encoder Rerank (SiliconFlow bge-reranker-v2-m3)
  │
  ▼
  返回 top10 精排结果 → 衰减恢复 → lastAccessedAt 刷新
```

**容错**：任一路召回失败不影响其他路；Rerank API 失败则降级返回 RRF 融合结果。

---

## 六、记忆生命周期

### 6.1 写入管道（模式1 完整流程）

```
对话完成 (userMessage, aiReply)
  │
  ▼ [@Async 异步]
LLM 提取事实列表（每条一行）
  │
  ├── 跳过 "NONE"
  ├── 去格式前缀（- / 1. / ·）
  │
  ├── 去重：ChromaDB 语义搜索 top3，最高相似度 > 0.85 → 跳过
  │
  ├── ChromaDB 写入 → chromaId
  ├── MySQL 写入 → MemoryItem(id, chromaId, value, promptId, validFrom)
  │
  ├── 知识图谱构建 (GraphMemoryService.linkMemory)：
  │     ├── LLM 三元组提取（few-shot prompt，5 列输出：主语|谓词|宾语|主语类型|宾语类型）
  │     ├── 校验：主语≠宾语、谓词≤50 字
  │     ├── 实体 upsert（LLM 类型优先，硬编码规则 fallback）
  │     ├── 记忆-实体关联（SUBJECT/OBJECT 角色）
  │     ├── 关系去重：查 (subject_id, predicate, object_id)，存在则重新激活
  │     └── 反向边：自动追加反向关系（已去重）
  │
  ├── 时态冲突检测：
  │     ├── ChromaDB 搜索相似旧记忆 (0.75 < score < 0.90)
  │     ├── LLM 判定是否冲突
  │     └── 是冲突 → 旧记忆 SUPERSEDED → 级联 expireRelations()（设其关系的 valid_until=now）
  │
  └── BM25 索引同步
```

### 6.2 衰减（模式4）

```
FULL (清晰期) ──3天未访问──→ BRIEF (模糊期)
      ↑                          │
      │ LLM 智能压缩(200字)       │ 7天未访问
      │                          ↓
      │                    TITLE (轮廓期)
      │                          │
      │ LLM 智能压缩(50字)        │ 14天未访问
      │                          ↓
      │                     遗忘（物理删除）
      │                          ↑
      └──── 语义命中时从 originalValue 恢复 ──┘

豁免规则：
  - source=MANUAL → 永不衰减
  - status=SUPERSEDED → 免衰减（已被新事实主动取代）
  - 遗忘时先设 status=EXPIRED 再删除
```

### 6.3 时态取代

```
新事实 "用户调到上海"
  │
  ├── ChromaDB 搜索相似旧记忆 (0.75 < score < 0.90)
  ├── LLM 判定是否冲突
  │
  └── 是冲突 → 旧记忆 status=SUPERSEDED, validUntil=now, supersededById=新记忆.id
              → 级联 expireRelations()：旧记忆关联的所有关系设 valid_until=now
```

---

## 七、知识图谱优化（六项已完成）

| # | 优化项 | 说明 | 影响 |
|---|--------|------|------|
| 1 | 关系去重与合并 | `saveRelation()` 插入前查重，存在则重新激活 | 减少冗余边，`valid_from`/`valid_until` 语义生效 |
| 2 | 关系层时态管理 | 记忆被取代时级联 `expireRelations()` | 图遍历只走当前有效边，避免矛盾结果 |
| 3 | 图遍历用于模式3搜索 | `EntityRetrievalService` 实体匹配后 1 跳图扩展，邻接记忆 0.5 权重召回 | 关联信息不再遗漏，搜索召回率提升 |
| 4 | LLM 三元组提取质量 | few-shot prompt + LLM 输出实体类型 + 校验 | 实体分类更准确，减少格式错误 |
| 5 | 反向关系推断 | 6 对高频谓词反向映射，自动追加反向边 | 图遍历方向完整，无需全表扫描 `object_id` |
| 6 | 实体消歧 | `suggestMerges()` + `mergeEntities()`，LLM 识别同人异名后合并实体 | 图连通性提升，消除别名导致的子图断裂 |

---

## 八、提示词级隔离

| 场景 | promptId | 可见记忆 |
|------|----------|---------|
| 无角色选择 | null | 仅 `prompt_id IS NULL`（共享） |
| 角色 X 对话 | X | `prompt_id IS NULL`（共享）+ `prompt_id = X`（专属） |
| 手动搜索 | 可选 | 不传则搜共享，传 X 则搜共享+X |

三条路径全部隔离：

| 路径 | 隔离方式 |
|------|---------|
| 模式2 注入 | MySQL `WHERE prompt_id IS NULL OR prompt_id = :promptId` |
| 模式3 搜索 | BM25 路过滤；MySQL 候选加载时过滤 |
| 模式1 提取 | 写入时记录 `promptId`，后续检索自然对齐 |

---

## 九、架构全景图

```
┌─────────────────────────────────────────────────────────────┐
│                      ChatService                            │
│  chatStream() / chatAndSave()                               │
│       │                                                     │
│       ├──► MessageContextBuilder                            │
│       │      ├── 系统规则                                   │
│       │      ├── 角色 Prompt (promptId)                     │
│       │      ├── 长期记忆注入 (模式2: 20条 + 1跳图扩展)     │ ← promptId 过滤
│       │      ├── 对话摘要                                   │
│       │      ├── 知识库 RAG                                 │
│       │      └── 历史消息 (30条)                            │
│       │                                                     │
│       └──► ChatStreamService ──► LLM API                    │
│                │                                             │
│                └──► ChatPostProcessor (@Async)               │
│                       ├── MemoryService.extractAndStore()    │ ← promptId 传递
│                       │    ├── LLM 提取事实                  │
│                       │    ├── ChromaDB 双写                 │
│                       │    ├── GraphMemoryService 建图       │
│                       │    │    ├── LLM 三元组提取+类型       │
│                       │    │    ├── 关系去重                 │
│                       │    │    └── 反向边追加               │
│                       │    ├── 时态冲突检测                  │
│                       │    │    └── 级联 expireRelations()   │
│                       │    └── BM25 索引                     │
│                       └── SummaryService 摘要               │
│                                                              │
│  ┌───────────────────────────────────────────────────────┐  │
│  │              记忆检索管线 (模式3)                      │  │
│  │                                                       │  │
│  │  MemoryController.search() ──► MemoryService           │  │
│  │       │                          .searchAndRecall()    │  │
│  │       │                              │                 │  │
│  │       │    ┌─────────────────────────┼─────────────┐  │  │
│  │       │    │  HybridRetrievalService              │  │  │
│  │       │    │   ├── ChromaDB 语义          (并行)   │  │  │
│  │       │    │   ├── Lucene BM25 关键词      (并行)   │  │  │
│  │       │    │   └── 知识图谱实体匹配       (并行)   │  │  │
│  │       │    │        ├── LLM 实体提取                │  │  │
│  │       │    │        ├── 精确匹配                    │  │  │
│  │       │    │        └── 1跳图扩展（邻接记忆0.5权重）│  │  │
│  │       │    │         ↓                            │  │  │
│  │       │    │   RRF 融合 → top30                   │  │  │
│  │       │    │         ↓                            │  │  │
│  │       │    │   Cross-Encoder Rerank → top10       │  │  │
│  │       │    └──────────────────────────────────────┘  │  │
│  └───────────────────────────────────────────────────────┘  │
│                                                              │
│  ┌───────────────────────────────────────────────────────┐  │
│  │              实体消歧（手动/定时触发）                 │  │
│  │  GraphMemoryService                                    │  │
│  │   ├── suggestMerges(userId)  LLM 扫描候选             │  │
│  │   └── mergeEntities(from,to) 转移关系+关联 → 删实体   │  │
│  └───────────────────────────────────────────────────────┘  │
│                                                              │
│  ┌───────────────────────────────────────────────────────┐  │
│  │              存储层                                    │  │
│  │                                                       │  │
│  │  MySQL                    ChromaDB       Lucene       │  │
│  │  ├── memory_items         嵌入服务       内存索引      │  │
│  │  ├── memory_entities      mem_{userId}   (BM25)      │  │
│  │  ├── memory_item_entities                             │  │
│  │  ├── memory_relations (去重+反向边+时态)              │  │
│  │  ├── conversation_summaries                           │  │
│  │  └── chat_messages                                    │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

---

## 十、关键配置

```properties
# 衰减参数
memory.decay.fresh-days=3       # FULL → BRIEF: 3 天未访
memory.decay.brief-days=7       # BRIEF → TITLE: 7 天未访
memory.decay.forget-days=14     # TITLE → 删除: 14 天未访

# 注入参数
memory.inject.recent-count=20   # 模式2 注入最近 N 条

# 搜索参数
memory.search.top-k=10          # 模式3 最终返回条数

# 嵌入
embedding.model=BAAI/bge-large-zh-v1.5
embedding.api-url=https://api.siliconflow.cn/v1/embeddings

# Rerank
rerank.model=BAAI/bge-reranker-v2-m3
rerank.api-url=https://api.siliconflow.cn/v1/rerank

# BM25
bm25.index-path=./data/bm25-index
```

---

## 十一、与主流框架对比

| 评分维度 | HanaChat | 行业最佳 |
|----------|----------|---------|
| 衰减/遗忘机制 | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ (CrewAI) |
| 知识图谱 | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ (Zep/Cognee) |
| 时态推理 | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ (Zep) |
| 检索能力 | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ (Mem0/Zep) |
| 存储架构 | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ (Cognee) |
| 记忆提取自动化 | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ (Cognee ECL) |
| 角色隔离 | ⭐⭐⭐⭐ | ⭐⭐⭐ (Mem0 session scoping) |

**核心优势**：懒衰减+三态模型+衰减恢复是行业领先的遗忘机制设计；知识图谱经六项优化后覆盖去重、时态、反向边、图扩展搜索和实体消歧；提示词级角色隔离为多角色 AI 应用提供了开箱即用的记忆隔离。
