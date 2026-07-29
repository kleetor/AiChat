# 记忆系统改造方案：多信号混合检索 + Rerank

> 生成日期：2026-07-22
> 前置依赖：V9 知识图谱 + 时态管理已就绪

## 总览

当前检索流程仅依赖 ChromaDB 单路语义向量匹配（⭐⭐），改造后升级为 **三路召回 + RRF 融合 + Cross-Encoder 精排** 的完整检索管线（目标 ⭐⭐⭐⭐）。

```
当前：
  query → ChromaDB 向量搜索 topK → 返回

改造后：
  query → ChromaDB 语义搜索 ─┐
         → BM25 关键词匹配   ─┼→ RRF 融合 → top30 候选 → Cross-Encoder Rerank → topK 返回
         → 图谱实体匹配      ─┘
```

## 架构总览

```
┌─────────────────────────────────────────────────────────┐
│                    MemoryService                        │
│  searchAndRecall() / searchForContext()                 │
└────────────────────────┬────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────┐
│                HybridRetrievalService                    │
│  ┌──────────┐  ┌───────────┐  ┌──────────────┐         │
│  │ ChromaDB │  │  BM25     │  │  Entity      │         │
│  │ Retriever│  │ Retriever │  │  Retriever   │         │
│  │ (已有)   │  │ (新增)    │  │ (新增)       │         │
│  └────┬─────┘  └─────┬─────┘  └──────┬───────┘         │
│       │              │               │                  │
│       └──────────────┼───────────────┘                  │
│                      │ RRF 融合                         │
│                      ▼                                  │
│              ┌──────────────┐                           │
│              │  Reranker    │                           │
│              │  (新增)      │                           │
│              └──────────────┘                           │
└─────────────────────────────────────────────────────────┘
```

---

## 一、各模块详设

### 1.1 BM25 关键词检索 — `Bm25IndexService`

**数据流**：内存索引，一个 Lucene IndexWriter + 一个用户维度的字段过滤。

```
写入路径（与记忆生命周期同步）：
  MemoryService.extractAndStore()  → bm25IndexService.index(itemId, userId, text)
  MemoryService.delete()           → bm25IndexService.remove(itemId)
  MemoryService.update()           → bm25IndexService.update(itemId, text)
  MemoryService.checkAndApplyDecay → 压缩后异步调 update

检索路径：
  bm25IndexService.search(userId, query, topK) → List<DocHit(id, score)>
```

**依赖**（新增到 pom.xml）：

```xml
<dependency>
    <groupId>org.apache.lucene</groupId>
    <artifactId>lucene-core</artifactId>
    <version>9.12.1</version>
</dependency>
<dependency>
    <groupId>org.apache.lucene</groupId>
    <artifactId>lucene-analysis-smartcn</artifactId>
    <version>9.12.1</version>
</dependency>
```

**设计要点**：
- 使用 `SmartChineseAnalyzer` 做中文分词
- 全局单个索引（文件系统 `MMapDirectory`），通过 `userId` 字段过滤
- `@PreDestroy` 时关闭 IndexWriter
- 索引字段：`itemId` (LongPoint + Stored), `userId` (LongPoint + Stored), `text` (TextField)
- 搜索时用 `BooleanQuery` 组合 `userId` 过滤 + 文本 BM25 查询

### 1.2 图谱实体检索 — `EntityRetrievalService`

复用已有的 `memory_item_entities` + `memory_entities` 表，无需新基础设施。

**流程**：

```
query → LLM 提取 query 中的实体 → 在 memory_entities 中匹配实体
       → 通过 memory_item_entities 找到关联的 memory_item_id
       → 返回 MemoryItem 列表，附带匹配度（匹配实体数 / query 实体数）
```

**关键方法**：

```java
// EntityRetrievalService
public List<ScoredMemory> searchByEntities(Long userId, String query, int topK) {
    // 1. LLM 提取 query 中的实体词
    List<String> queryEntities = extractEntitiesFromQuery(query);
    if (queryEntities.isEmpty()) return List.of();

    // 2. 在 memory_entities 中匹配
    List<MemoryEntity> matched = entityRepo.findByUserIdAndNameIn(userId, queryEntities);

    // 3. 找到关联的记忆，按匹配实体数打分
    Map<Long, Integer> itemScores = new HashMap<>();
    for (MemoryEntity entity : matched) {
        for (Long itemId : itemEntityRepo.findMemoryIdsByEntityId(entity.getId())) {
            itemScores.merge(itemId, 1, Integer::sum);
        }
    }

    // 4. 排序返回
    return itemScores.entrySet().stream()
            .sorted(Map.Entry.<Long, Integer>comparingByValue().reversed())
            .limit(topK)
            .map(e -> new ScoredMemory(e.getKey(), e.getValue() / (double) queryEntities.size()))
            .toList();
}
```

### 1.3 Rerank 精排 — `SiliconFlowRerankService`

与现有 `SiliconFlowEmbeddingService` 同一供应商、同一 API Key，零部署成本。

**API**：

```
POST https://api.siliconflow.cn/v1/rerank
Authorization: Bearer ${SILICONFLOW_API_KEY}
{
    "model": "BAAI/bge-reranker-v2-m3",
    "query": "用户的搜索问题",
    "documents": ["记忆文本1", "记忆文本2", ...],
    "top_n": 10
}

返回：
{
    "results": [
        {"index": 0, "relevance_score": 0.96},
        {"index": 3, "relevance_score": 0.82},
        ...
    ]
}
```

**关键方法**：

```java
@Service
public class SiliconFlowRerankService {
    public List<ScoredMemory> rerank(String query, List<MemoryItem> candidates, int topN) {
        // 1. 构建 documents 数组
        List<String> docs = candidates.stream().map(MemoryItem::getValue).toList();

        // 2. 调 API
        List<RerankResult> results = callRerankApi(query, docs, topN);

        // 3. 按模型返回的 relevance_score 重排序
        return results.stream()
                .map(r -> new ScoredMemory(candidates.get(r.index()).getId(), r.relevanceScore()))
                .toList();
    }
}
```

### 1.4 融合编排 — `HybridRetrievalService`

```java
@Service
public class HybridRetrievalService {

    private final MemoryChromaService chromaService;   // 已有
    private final Bm25IndexService bm25Service;         // 新增
    private final EntityRetrievalService entityService; // 新增
    private final SiliconFlowRerankService rerankService; // 新增
    private final MemoryItemRepository memoryRepo;      // 已有

    /**
     * 混合检索：三路召回 → RRF 融合 → Rerank 精排
     */
    public List<MemoryItem> hybridSearch(Long userId, String query, int finalTopK) {
        // === Phase 1: 三路并行召回 ===
        var chromaFuture = CompletableFuture.supplyAsync(
                () -> chromaService.search(userId, query, 30));
        var bm25Future = CompletableFuture.supplyAsync(
                () -> bm25Service.search(userId, query, 30));
        var entityFuture = CompletableFuture.supplyAsync(
                () -> entityService.searchByEntities(userId, query, 30));

        List<MemoryHit> chromaResults = chromaFuture.join();
        List<DocHit> bm25Results = bm25Future.join();
        List<ScoredMemory> entityResults = entityFuture.join();

        // === Phase 2: RRF 融合（Reciprocal Rank Fusion）===
        Map<Long, Double> fusedScores = rrfFusion(chromaResults, bm25Results, entityResults);

        // === Phase 3: 取 top30 候选做 Rerank ===
        List<Long> candidateIds = fusedScores.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(30)
                .map(Map.Entry::getKey)
                .toList();

        List<MemoryItem> candidates = memoryRepo.findAllById(candidateIds);

        // === Phase 4: Cross-Encoder 精排 ===
        List<ScoredMemory> reranked = rerankService.rerank(query, candidates, finalTopK);

        // === Phase 5: 返回最终 topK ===
        return reranked.stream()
                .map(s -> memoryRepo.findById(s.itemId()).orElse(null))
                .filter(Objects::nonNull)
                .toList();
    }

    /** RRF 公式: score(doc) = Σ 1 / (k + rank_in_signal) */
    private Map<Long, Double> rrfFusion(List<?>... results) {
        final double K = 60; // 经典常数
        Map<Long, Double> scores = new HashMap<>();
        for (var resultList : results) {
            for (int rank = 0; rank < resultList.size(); rank++) {
                Long itemId = getItemId(resultList.get(rank));
                scores.merge(itemId, 1.0 / (K + rank + 1), Double::sum);
            }
        }
        return scores;
    }
}
```

---

## 二、改动清单

### 新增文件（4个）

| 文件 | 用途 |
|------|------|
| `service/Bm25IndexService.java` | Lucene BM25 索引管理（CRUD + 搜索） |
| `service/EntityRetrievalService.java` | 基于知识图谱的实体检索 |
| `service/SiliconFlowRerankService.java` | SiliconFlow Rerank API 封装 |
| `service/HybridRetrievalService.java` | 三路召回 + RRF 融合 + Rerank 编排 |

### 修改文件（3个）

| 文件 | 变更 |
|------|------|
| `pom.xml` | 加 `lucene-core` + `lucene-analysis-smartcn` |
| `application-dev.properties` | 加 Rerank API URL（可复用 embedding 的 Key） |
| `service/MemoryService.java` | `searchAndRecall()` 改为走 `HybridRetrievalService.hybridSearch()`；`extractAndStore()` 末尾调 `bm25Service.index()`；`delete()` 中调 `bm25Service.remove()` |

### 新增依赖

```xml
<dependency>
    <groupId>org.apache.lucene</groupId>
    <artifactId>lucene-core</artifactId>
    <version>9.12.1</version>
</dependency>
<dependency>
    <groupId>org.apache.lucene</groupId>
    <artifactId>lucene-analysis-smartcn</artifactId>
    <version>9.12.1</version>
</dependency>
```

### 新增配置

```properties
# Rerank（共用 SiliconFlow Key）
rerank.api-key=${SILICONFLOW_API_KEY}
rerank.api-url=https://api.siliconflow.cn/v1/rerank
rerank.model=BAAI/bge-reranker-v2-m3
# BM25 索引存储路径
bm25.index-path=./data/bm25-index
```

---

## 三、对现有四种模式的影响

| 模式 | 影响 |
|------|------|
| 模式1（提取） | 写入后追加 `bm25Service.index()` |
| 模式2（注入） | 无需改动（注入走 ChromaDB + 图扩展，不涉及多路检索） |
| 模式3（回溯） | `searchAndRecall()` 切换为 `hybridSearch()` |
| 模式4（衰减） | 压缩/删除后同步 `bm25Service.update()` / `bm25Service.remove()` |

---

## 四、实施步骤

| 步骤 | 内容 | 依赖 |
|------|------|------|
| Step 1 | `pom.xml` 加 Lucene 依赖 | 无 |
| Step 2 | 实现 `Bm25IndexService`（带 @PreDestroy 关闭） | Step 1 |
| Step 3 | 实现 `EntityRetrievalService`（复用 KG 表） | 已完成的 V9 |
| Step 4 | 实现 `SiliconFlowRerankService` | 无 |
| Step 5 | 实现 `HybridRetrievalService`（编排） | Step 2-4 |
| Step 6 | 修改 `MemoryService` 集成 `HybridRetrievalService` | Step 5 |
| Step 7 | 验证：手动测试语义搜索+关键词+实体三路融合效果 | Step 6 |

---

## 五、关键设计决策

### 为什么用 Lucene 而不是内存 TF-IDF？

| 因素 | Lucene | 手写 TF-IDF |
|------|--------|------------|
| BM25 实现 | 内置、调优好的 | 需手写，容易有 bug |
| 中文分词 | SmartChineseAnalyzer 开箱即用 | 需引入分词库 |
| 增量更新 | IndexWriter 支持 | 需自行维护倒排索引 |
| 性能 | 高度优化 | 不定 |
| jar 体积 | ~4MB | ~0 |

Lucene 的 `lucene-core` 仅 4MB，换来的是生产级 BM25 + 增量更新 + 中文分词，选择它无需犹豫。

### 为什么用 SiliconFlow Rerank 而不是本地模型？

- 零部署：复用现有 API Key 和 RestTemplate
- 模型 BGE-Reranker-v2-m3 是中文 Rerank 的 SOTA
- 成本：30 条文档 × 一次调用 ≈ 0.001 元
- 延迟：100-300ms，记忆搜索非实时聊天场景可接受

### 为什么 BM25 不存 ChromaDB metadata？

ChromaDB 的全文搜索依赖其内置的全文索引（基于 SQLite FTS），不支持中文分词。Lucene 的 SmartChineseAnalyzer 是所需的中文分词能力。

### RRF 融合 vs 加权融合？

RRF（Reciprocal Rank Fusion）是无超参数的排序融合，不需要调权重。三个信号天然异构（向量距离 vs BM25 得分 vs 实体匹配数），加权融合需要大量实验调参，RRF 直接可用、效果稳定。
