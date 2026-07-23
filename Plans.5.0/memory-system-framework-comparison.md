# HanaChat 记忆系统 vs 市面主流框架对比分析

> 生成日期：2026-07-22

## 一、对比框架概览

| 框架 | 定位 | GitHub Stars | 开源协议 | 核心存储 |
|------|------|-------------|----------|----------|
| **HanaChat（本项目）** | 自研仿人类记忆模型 | - | 私有 | MySQL + ChromaDB |
| **Letta (MemGPT)** | OS风内存管理Agent框架 | 23k+ | Apache 2.0 | 向量DB + 关系DB |
| **Mem0** | 即插即用记忆中间层 | 58k+ | Apache 2.0 | 向量DB + 图(可选) |
| **Zep / Graphiti** | 时态知识图谱引擎 | 10k+ | Apache 2.0(Core) | 知识图谱 + 向量 |
| **Cognee** | 多存储融合记忆引擎 | 15k+ | Apache 2.0 | 图 + 向量 + 关系 |
| **CrewAI Memory** | 认知记忆系统 | 25k+ | MIT | LanceDB |
| **LangChain/LangGraph** | 通用框架内置记忆 | 100k+ | MIT | Checkpointer机制 |

---

## 二、架构模式对比

### 2.1 记忆层级

| 维度 | HanaChat | Letta | Mem0 | Zep | Cognee | CrewAI | LangChain |
|------|----------|-------|------|-----|--------|--------|-----------|
| 短期记忆 | 最近30条消息直接注入 | FIFO队列+递归摘要 | Session级KV | Episode子图 | Session Memory | 短期记忆行为 (recency) | Checkpointer持久化 |
| 中期记忆 | 对话摘要(SummaryService) | Recall Storage(对话历史搜索) | 摘要化记忆 | 语义实体子图 | - | - | SummarizationMiddleware |
| 长期记忆 | 结构化事实(MemItem) | Archival Storage(向量库) | Vector Store持久化 | 社区子图+双时态事实 | Permanent Memory | 长期记忆行为 (importance) | Long-term memory Profile |

### 2.2 关键差异分析

**HanaChat 采用的是清晰的三层时间衰减模型（短期消息 → 中期摘要 → 长期记忆事实），这是其与市面上多数框架最大的不同点。**

---

## 三、独有亮点 vs 行业做法

### 3.1 HanaChat 的优势

| 特性 | 本项目的做法 | 行业做法 | 评价 |
|------|-------------|----------|------|
| **懒衰减 (Lazy Decay)** | 读取时检查，无定时任务 | Mem0无衰减/Letta无衰减/Zep依赖图谱裁剪 | 独特设计，零空闲开销 |
| **三态衰减** | FULL→BRIEF→TITLE 阶梯降级 | CrewAI只有recency衰减权重；Letta无衰减 | 领先，模拟人类"遗忘曲线" |
| **手动记忆豁免** | MANUAL来源永不衰减 | 未见同类精细控制 | 独特 |
| **衰减可恢复** | 语义命中后从originalValue恢复 | Zep有版本管理但非同一机制 | 独特 |
| **两阶段压缩** | 先截断(毫秒)+后LLM压缩(异步) | LangChain的SummarizationMiddleware同步全量压缩 | 兼顾实时性和质量 |
| **语义去重** | 写入前ChromaDB搜索≥85%跳过 | Mem0 V3做了类似去重 | 业界水平 |
| **用户独立Collection** | ChromaDB mem_{userId} | Cognee/Zep支持多租户，Mem0按user_id过滤 | 设计合理 |
| **增量摘要** | SummaryService增量LLM合并 | LangChain官方每次都全量重摘要 | 省Token |

### 3.2 HanaChat 的不足

| 维度 | 本项目的不足 | 行业最佳实践 | 影响程度 |
|------|-------------|-------------|----------|
| **知识图谱** | 纯向量检索，无实体/关系建模 | Zep的三层知识图谱(时态)、Cognee的图+向量融合 | 🔴 高 |
| **时态推理** | 仅按lastAccessedAt衰减，无事实变化追踪 | Zep：双时态(validity window+provenance)，可追踪"我换工作了" | 🔴 高 |
| **多信号检索** | 单一语义向量相似度 | Mem0 V3：语义+关键词(BM25)+实体匹配三路融合 | 🟡 中 |
| **重排序(Rerank)** | 无 | Mem0/Cognee/Zep都有Reranker | 🟡 中 |
| **多跳推理** | 不支持（没有图结构） | Cognee 14种检索模式含Chain-of-Thought图遍历 | 🟡 中 |
| **Agent自主记忆管理** | 被动（系统自动提取+注入） | Letta：Agent通过Tool Call自主管理记忆 | 🟡 中 |
| **记忆冲突处理** | 无（依赖LLM去重85%） | CrewAI/Cognee：主动检测并解决矛盾记忆 | 🟡 中 |
| **多租户隔离** | 仅用户级别 Collection | Cognee：group/org多级scope；Zep：user/group/session三级 | 🟢 低 |
| **标准化基准测试** | 无公开评测 | Mem0/LoCoMo 92.5分，Zep/LoCoMo 94.7% | 🟡 中 |
| **可观测性** | 无专用监控 | Letta ADE可视化context window；Mem0有telemetry | 🟢 低 |
| **框架耦合** | 紧耦合在Spring Boot应用中 | Mem0/Zep/Cognee都是独立API/SDK，零框架锁定 | 🟡 中 |

---

## 四、核心架构深度对比

### 4.1 存储架构

```
HanaChat:          MySQL(memory_items) + ChromaDB(mem_{userId})
                   ↑ 元数据+衰减状态     ↑ 语义向量

Letta (MemGPT):    PostgreSQL + pgvector/Qdrant
                   ↑ Recall+Archival        ↑ Archival Memory

Mem0:              Vector Store(Qdrant/ChromaDB/...) + Graph Store(可选)
                   ↑ 主要记忆存储              ↑ Mem0g图记忆

Zep/Graphiti:      知识图谱(Neo4j/Memgraph) + 向量
                   ↑ Episode→Entity→Community 三层      ↑ 语义检索

Cognee:            Kuzu/Neo4j(图) + LanceDB/Qdrant(向量) + SQLite/PG(关系)
                   ↑ 三合一Poly-store

CrewAI Memory:     LanceDB
                   ↑ 统一存储，LLM自动分析分类
```

**分析**：HanaChat 的 MySQL+ChromaDB 双存储是务实选择，但缺少图存储层是一个结构性缺失。Zep和Cognee已经证明，**知识图谱对于事实间关系的建模和时态推理至关重要**。

### 4.2 写入流程

| 框架 | 写入方式 | 特点 |
|------|---------|------|
| HanaChat | LLM从(userMsg, aiReply)提取事实 → 去重 → 双写 | 异步不阻塞对话 |
| Letta | Agent通过Tool Call主动写入 | 依赖Agent判断，可能遗漏 |
| Mem0 | 两阶段：extract + update(ADD/UPDATE/DELETE/NOOP) | V3改为单pass ADD-only |
| Zep | 自动萃取Episode→实体→事实，带时间窗口 | 全自动，被动式 |
| Cognee | ECL管道：Extract→Cognify→Load，Memify压缩精炼 | 流水线式，功能最全 |
| CrewAI | LLM分析内容，推断scope、category、importance | 统一API，智能分类 |

**分析**：HanaChat 的写入流程属于"被动提取"模式，与Zep类似，优于Letta的"依赖Agent判断"方式。但在记忆更新方面弱于Mem0的ADD/UPDATE/DELETE/NOOP四操作模型和Zep的时态版本管理。

### 4.3 检索架构

| 维度 | HanaChat | Mem0 V3 | Zep | Cognee |
|------|----------|---------|-----|--------|
| 向量检索 | single-pass ChromaDB | ✅ | ✅ | ✅ |
| 关键词匹配 | ❌ | BM25 | ✅ | ✅ |
| 图扩展/Traversal | ❌ | Mem0g可选 | ✅ 核心功能 | ✅ 14种模式 |
| Rerank | ❌ | ✅ | ✅ | ✅ |
| 时态过滤 | ❌ | ❌ | ✅ 核心特色 | 自定义 |
| 复合评分 | ❌(仅向量相似度) | semantic+keyword+entity | 图重要性+时间+相似度 | 多信号融合 |
| 检索共识 | - | 92.5 LoCoMo | 94.7% LoCoMo | 未公开 |

**分析**：这是HanaChat最明显的短板。仅靠单一语义向量相似度检索，与Mem0的多信号融合和Zep的图遍历+时态过滤相比，在精确率、召回率和复杂查询上都会有显著差距。

### 4.4 遗忘/衰减机制

| 框架 | 遗忘策略 | 特点 |
|------|---------|------|
| **HanaChat** | 懒衰减：FULL(3天)→BRIEF(7天)→TITLE(14天)→删除 | 🔥 最仿生、精细 |
| Letta | 无内置衰减 | 依赖Agent自行管理 |
| Mem0 | 无衰减，依赖LRU | 可手动删除 |
| Zep | 依赖时态图事实过期 | 双时态追踪validity |
| Cognee | Memify定期裁剪stale节点 | 后台维护 |
| CrewAI | recency_half_life_days指数衰减 | 权重下降，不删除 |
| LangChain | 超出窗口直接丢弃或摘要 | 简单粗暴 |

**分析**：HanaChat的懒衰减+三态模型是**在所有对比框架中最精细的遗忘机制**。CrewAI只有指数衰减（不删除），LangChain直接丢弃。但如果结合Zep的时态事实管理（"用户换工作了，旧事实自动过期"），HanaChat的纯时间衰减在某些场景下可能不够智能——一个3天前刚被确认过的重要事实不应该和3天前的一次性闲聊同速衰减。

---

## 五、改进建议（按优先级排序）

### 🔴 P0 - 结构性缺失

1. **引入知识图谱层**
   - 当前：纯向量检索，无法建模"张三在阿里工作 → 阿里在杭州 → 张三在杭州"的多跳关系
   - 建议：引入轻量级图数据库（如Kuzu/Neo4j），与现有ChromaDB形成"向量+图"混合架构
   - 参考：Cognee的DataPoint模型（Pydantic + 图+向量自动同步）

2. **增加时态事实管理**
   - 当前：只能追踪"上次访问时间"，无法追踪"事实A在时间T1成立，T2被事实B取代"
   - 建议：参考Zep的双时态模型，为memory_items增加`valid_from`/`valid_until`字段
   - 场景：用户说"我搬到上海了"→ 自动标记旧地址失效，而非等待14天衰减

### 🟡 P1 - 检索增强

3. **多信号混合检索**
   - 当前：仅语义相似度
   - 建议：加入BM25关键词匹配 + 实体匹配，三路融合打分
   - 参考：Mem0 V3的multi-signal retrieval

4. **增加Rerank环节**
   - 当前：topK直接返回，无二次排序
   - 建议：对粗排结果用轻量级Cross-Encoder Reranker精排

5. **衰减智能化**
   - 当前：纯时间驱动，所有记忆无差别衰减
   - 建议：引入重要性评分（accessCount/手动标记/LLM评估），重要记忆衰减更慢

### 🟢 P2 - 工程完善

6. **独立为可复用SDK/API**
   - 当前：紧耦合在Spring Boot单体应用中
   - 建议：参考Mem0/Zep的独立API模式，便于多项目复用

7. **标准化基准测试**
   - 当前：无评测数据
   - 建议：用LoCoMo/LongMemEval跑一遍，建立性能基线

8. **可观测性**
   - 当前：无监控仪表盘
   - 建议：至少增加记忆数量、衰减率、命中率的统计面板

---

## 六、总结

| 评分维度 | HanaChat | 行业平均 | 行业最佳 |
|----------|----------|---------|---------|
| 衰减/遗忘机制 | ⭐⭐⭐⭐⭐ | ⭐⭐ | ⭐⭐⭐ (CrewAI) |
| 存储架构 | ⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ (Cognee) |
| 检索能力 | ⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ (Mem0/Zep) |
| 时态推理 | ⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ (Zep) |
| 知识图谱 | ⭐ | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ (Zep/Cognee) |
| 记忆提取自动化 | ⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ (Cognee ECL) |
| 工程成熟度 | ⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ (Mem0) |

**核心结论**：HanaChat的记忆系统在**遗忘机制设计（懒衰减+三态模型）**上独树一帜，是所有对比框架中最接近人类记忆模型的。但在**知识图谱和时态推理**方面存在结构性缺失，**检索能力**也相对单一。建议优先补齐知识图谱和时态管理两个维度，这将使整体架构从"优秀的单体记忆系统"跃升为"有竞争力的记忆框架"。

---

## 参考来源

- Letta (MemGPT) 官方文档: https://docs.letta.com/advanced/memory_management
- Mem0 论文 (ECAI 2025): https://arxiv.org/abs/2504.19413
- Zep 论文: https://arxiv.org/abs/2501.13956
- Cognee 官方博客: https://www.cognee.ai/blog/fundamentals/how-cognee-builds-ai-memory
- CrewAI 官方文档: https://docs.crewai.com/en/concepts/memory
- LangChain 记忆文档: https://docs.langchain.com/oss/python/langchain/short-term-memory
- Agent Memory 2026 基准报告: https://mem0.ai/blog/state-of-ai-agent-memory-2026
- Agent Memory 对比 (Automem): https://automem.ai/blog/agent-memory-in-2026-an-honest-comparison-of-mem0-zep-letta-and-the-rest
