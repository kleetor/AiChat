# HanaChat RAG 知识库系统改进方案

> 基于 [rag-knowledge-base-analysis.md](rag-knowledge-base-analysis.md) 第十二~十四章的差距分析，制定具体改进方案。

---

## 一、改进总览

```
优先级排序（投入产出比）：
P0（立即）→ P1（短期）→ P2（中期）→ P3（长期）

P0: 混合检索+Rerank | Prompt 模板可配置
P1: 扩展文档格式   | 查询重写 | PDF 深度解析 | 可配置分块策略
P2: 结构化溯源     | RAG 评估  | 多 Embedding 模型 | 增量更新
P3: 多租户协作     | 工作流引擎 | 知识图谱增强 KB | URL导入
```

---

## 二、P0 — 立即执行（检索精度+可配置性）

### 2.1 混合检索 + Rerank 精排

**目标**：将当前 `纯向量 Top-5 → 注入 prompt` 升级为 `多路召回 → RRF 融合 → Rerank 精排 → Top-N 注入`。

**现状**：
- 知识库：`ChromaDB.query()` 仅做余弦相似度检索，返回 Top-5
- 记忆系统：已实现 Lucene BM25 + ChromaDB 向量 + 知识图谱实体 → RRF 融合 → SiliconFlow BGE-Reranker-v2-m3 精排

**改造方案**：

```
用户问题
  ├─→ ChromaDB.query(向量召回 Top-20)       ──┐
  ├─→ Lucene BM25(关键词召回 Top-20)         ──┤ 已有（记忆系统）
  └─→ RRF 融合排序                            ──┘
       │
       └─→ SiliconFlow Reranker API (精排 Top-5)
            │
            └─→ 注入 system prompt
```

**涉及文件**：

| 文件 | 改动 |
|------|------|
| `ChromaDBService.java` | `query()` 返回值从 Top-K 扩展为 Top-N（如 Top-20），供后续融合 |
| `KnowledgeBaseService.java` | 新增 `hybridSearch()` 方法：编排向量检索 + BM25 检索 + RRF 融合 |
| `RagProperties.java` | 新增 `retrieve.candidate-size=20`（粗排候选数）、`retrieve.rerank-model` 等 |
| `EmbeddingProperties.java` | 新增 Reranker API 配置 |
| `MessageContextBuilder.java` | 调用 `hybridSearch()` 替代原来的 `chromaDBService.query()` |

**Lucene BM25 索引**：

为每个知识库维护独立 BM25 索引目录 `./data/bm25-kb/{kbId}/`，文档上传时同步写入，删除时同步清理。复用 `MemoryChromaService` 中的 Lucene 工具类。

**Reranker API 调用**：

```java
// SiliconFlow Reranker API
POST https://api.siliconflow.cn/v1/rerank
{
  "model": "BAAI/bge-reranker-v2-m3",
  "query": "用户问题",
  "documents": ["chunk1", "chunk2", ...],
  "top_n": 5
}
```

---

### 2.2 Prompt 模板可配置

**目标**：让用户可以为每个知识库自定义回答风格 prompt。

**数据库改动**：

```sql
ALTER TABLE knowledge_bases ADD COLUMN prompt_template VARCHAR(2000) 
  DEFAULT '以下是与用户问题相关的知识库内容，请基于这些内容回答：\n\n{context}\n\n回答时请注明引用来源（文件名）。';
```

| 字段 | 说明 |
|------|------|
| `prompt_template` | 支持占位符 `{context}`（检索结果）和 `{query}`（用户问题） |

**涉及文件**：

| 文件 | 改动 |
|------|------|
| `KnowledgeBase.java` | 新增 `promptTemplate` 字段 |
| `KnowledgeBaseRequest.java` | 创建/编辑时支持传入 `promptTemplate` |
| `KnowledgeBaseService.java` | 保存/读取 `promptTemplate` |
| `MessageContextBuilder.java` | 读取 KB 的 `promptTemplate`，替换 `{context}` 和 `{query}` 占位符 |
| `KBModal.tsx` | 知识库编辑表单新增 Prompt 模板输入框 |
| `V10__add_kb_prompt_template.sql` | Flyway 迁移 |

**用户界面**：

在知识库创建/编辑弹窗中增加"回答风格"文本框，提供预设模板选项：
- 通用问答（默认）
- 法律文档：严格引用条款
- 技术文档：代码示例优先
- 客服场景：简洁友好

---

## 三、P1 — 短期执行（文档处理+检索召回）

### 3.1 扩展文档格式支持

**目标**：从 3 种格式扩展到 8 种。

| 格式 | 解析库 | 说明 |
|------|--------|------|
| `.docx` | Apache POI (XWPF) | 已有 pdfbox，追加 poi-ooxml |
| `.xlsx` | Apache POI (XSSF) | 按 Sheet 转 Markdown 表格 |
| `.pptx` | Apache POI (XSLF) | 按 Slide 提取文本+备注 |
| `.html` | Jsoup | 去除标签，保留文本 |
| `.csv` | 标准 Java | 简单文本解析 |

**Maven 依赖**：

```xml
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>5.3.0</version>
</dependency>
<dependency>
    <groupId>org.jsoup</groupId>
    <artifactId>jsoup</artifactId>
    <version>1.18.1</version>
</dependency>
```

**代码改造**：

```java
// 新增 DocumentParser 接口，替换现有硬编码的 parseDocument()
public interface DocumentParser {
    String parse(Path filePath) throws Exception;
    boolean supports(String fileType);
}

// 实现类
PdfParser implements DocumentParser      // 已有，改造
DocxParser implements DocumentParser     // 新增
ExcelParser implements DocumentParser    // 新增
PptxParser implements DocumentParser     // 新增
HtmlParser implements DocumentParser     // 新增
TxtParser implements DocumentParser      // 新增（从 KnowledgeBaseService 提取）
```

**涉及文件**：

| 文件 | 改动 |
|------|------|
| `service/parser/DocumentParser.java` | 新增接口 |
| `service/parser/PdfParser.java` | 实现接口（重构） |
| `service/parser/DocxParser.java` | 新增 |
| `service/parser/ExcelParser.java` | 新增 |
| `service/parser/PptxParser.java` | 新增 |
| `service/parser/HtmlParser.java` | 新增 |
| `service/parser/TxtParser.java` | 新增 |
| `KnowledgeBaseService.java` | 工厂模式选择 Parser |

---

### 3.2 查询重写（Query Rewriting）

**目标**：用户口语化查询自动转换为更适合检索的表述。

**方案**：使用 LLM 对用户问题做查询改写，生成 2-3 个变体后并行检索。

```
用户: "考勤怎么算？"
  → LLM 改写:
    1. "员工考勤管理制度中的考勤计算方法"
    2. "考勤制度 计算规则"
  → 3 个 query 并行检索 → RRF 融合 → Rerank
```

**涉及文件**：

| 文件 | 改动 |
|------|------|
| `service/QueryRewriterService.java` | 新增：调用 LLM 生成查询变体 |
| `KnowledgeBaseService.java` | `hybridSearch()` 集成多查询并行检索 |

**LLM Prompt**：

```
将以下用户问题改写为 2-3 个更适合文档检索的表述，
每个表述应使用更正式、更具体的词汇：
问题：{userQuery}
输出格式：每行一个改写结果
```

---

### 3.3 可配置分块策略

**目标**：允许用户在创建/编辑知识库时调整分块参数。

**数据库改动**：

```sql
ALTER TABLE knowledge_bases 
  ADD COLUMN chunk_size INT DEFAULT 500,
  ADD COLUMN chunk_overlap INT DEFAULT 50;
```

**涉及文件**：

| 文件 | 改动 |
|------|------|
| `KnowledgeBase.java` | 新增 `chunkSize`、`chunkOverlap` 字段 |
| `KnowledgeBaseService.java` | 创建/处理文档时读取 KB 级分块参数 |
| `ChunkingService.java` | `split(text, chunkSize, chunkOverlap)` 方法签名扩展 |
| `KBModal.tsx` | 创建/编辑表单增加"分块大小"和"重叠字符数"输入 |
| `RagProperties.java` | 保留作为系统默认值 |
| `V11__add_kb_chunk_config.sql` | Flyway 迁移 |

---

### 3.4 PDF 深度解析（OCR + 表格）

**目标**：支持扫描件 PDF 的 OCR 文字识别和表格结构化提取。

> 此项工作量大，建议分两阶段：
> - Phase 1：表格提取（Apache PDFBox 内建能力，额外成本低）
> - Phase 2：OCR 识别（Tesseract，需安装额外依赖）

**Phase 1 — 表格提取**：

PDFBox 已支持 `PDFTableExtractor`，在现有 `PdfParser` 基础上增强：

```
原始 PDF 文本提取
  └─→ 检测表格区域（边框线/对齐检测）
       └─→ 表格转 Markdown Table 格式
            └─→ 与正文文本合并后分块
```

**Phase 2 — OCR**：

```xml
<!-- Tesseract OCR 引擎（需系统安装 tesseract） -->
<dependency>
    <groupId>net.sourceforge.tess4j</groupId>
    <artifactId>tess4j</artifactId>
    <version>5.12.0</version>
</dependency>
```

处理流程：
```
PDF → PDFBox 渲染为图片 → Tesseract OCR → 文本
```

---

## 四、P2 — 中期执行（溯源+评估+灵活性）

### 4.1 检索结果结构化溯源

**目标**：答案中精确标注引用来源，前端支持点击跳转到原文片段。

**改造方案**：

```
检索阶段：返回时附带完整元数据
  {
    chunk_id: "doc_123_chunk_5",
    text: "...",
    file_name: "考勤制度.pdf",
    chunk_index: 5,
    total_chunks: 20,
    char_offset_start: 1024,   // 新增：原文起始字符位置
    char_offset_end: 1524      // 新增：原文结束字符位置
  }

LLM 回答阶段：Prompt 要求引用时标注 [来源: filename#chunk_5]
  对应的 system prompt:
  "引用时请使用格式：【来源: 文件名 第X段】"

前端阶段：解析回答中的引用标记，渲染为可点击链接
  - 点击后展开原文片段（从 API 获取 chunk 内容）
```

**涉及文件**：

| 文件 | 改动 |
|------|------|
| `BaseChromaDBService.java` | `QueryResult` 扩展元数据字段 |
| `MessageContextBuilder.java` | Prompt 模板更新引用格式 |
| `services.ts` | 新增 `getKbChunk(kbId, chunkId)` API |
| `KBModal.tsx` / 新增组件 | 引用卡片渲染 |

---

### 4.2 RAG 质量评估体系

**目标**：建立可量化的 RAG 质量指标，驱动系统迭代优化。

**评估指标**（RAGAS 标准）：

| 指标 | 含义 | 计算方式 |
|------|------|---------|
| Context Recall | 检索结果是否覆盖了参考答案所需信息 | 参考答案中被检索上下文覆盖的句子比例 |
| Faithfulness | 生成答案是否完全基于检索到的上下文 | 答案中可在上下文中验证的陈述比例 |
| Answer Relevance | 生成答案与问题的相关程度 | 答案的语义向量与问题的余弦相似度 |

**评估流程**：

```
1. 准备测试集
   - 每个知识库选择 10-20 个代表性文档
   - 人工标注每个问题的标准答案和预期引用来源

2. 自动评估
   KnowledgeBaseEvalRunner
     ├─→ 对每个测试问题执行混合检索
     ├─→ 记录检索结果（Recall 计算）
     ├─→ LLM 生成答案
     ├─→ 调用 LLM 验证 Faithfulness
     └─→ 汇总指标输出

3. CI 集成
   - 每次 RAG 相关改动后执行评估
   - 指标退步时告警
```

**涉及文件**：

| 文件 | 改动 |
|------|------|
| `service/eval/KnowledgeBaseEvalRunner.java` | 新增 |
| `service/eval/RagasMetricCalculator.java` | 新增 |
| `test/resources/rag-test-dataset.json` | 新增：测试用例 |
| `KnowledgeBaseController.java` | 新增 `POST /api/kb/{id}/eval` 端点 |

---

### 4.3 多 Embedding 模型支持

**目标**：允许用户为不同知识库选择不同 Embedding 模型。

**数据库改动**：

```sql
ALTER TABLE knowledge_bases 
  ADD COLUMN embedding_model VARCHAR(200) DEFAULT 'BAAI/bge-large-zh-v1.5',
  ADD COLUMN embedding_dimension INT DEFAULT 1024;
```

> 注意：不同模型维度不同，需按 KB 隔离 Collection，已满足（`kb_{kbId}`）。

**涉及文件**：

| 文件 | 改动 |
|------|------|
| `KnowledgeBase.java` | 新增 `embeddingModel`、`embeddingDimension` |
| `SiliconFlowEmbeddingService.java` | `embedBatch()` 支持传入 model 参数 |
| `ChromaDBService.java` | 创建 Collection 时使用 KB 级 embedding 配置 |
| `EmbeddingProperties.java` | 新增支持的模型列表配置 |
| `KBModal.tsx` | 创建知识库时提供模型选择下拉框 |

---

### 4.4 增量更新

**目标**：单个文档修改后无需删除全部向量再重建。

**方案**：

```
1. 文档上传 → 记录 chunks 到 kb_document_chunks 表
2. 文档重新上传 → 对比新旧 chunk 差异
   - 新增 chunk → 向量化写入
   - 删除 chunk → 向量删除
   - 不变 chunk → 跳过
3. 统计计数原子更新
```

**数据库改动**：

```sql
CREATE TABLE kb_document_chunks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    doc_id BIGINT NOT NULL,
    chunk_index INT NOT NULL,
    chunk_hash VARCHAR(64) NOT NULL,       -- SHA-256 内容哈希
    chroma_id VARCHAR(100) NOT NULL,
    created_at DATETIME NOT NULL,
    FOREIGN KEY (doc_id) REFERENCES kb_documents(id) ON DELETE CASCADE,
    UNIQUE KEY uk_doc_chunk (doc_id, chunk_index)
);
```

---

## 五、P3 — 长期规划（架构升级）

### 5.1 多租户与协作

**目标**：知识库支持团队共享和权限分级。

**数据库改动**：

```sql
-- 知识库协作者
CREATE TABLE kb_collaborators (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    kb_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,  -- OWNER / EDITOR / VIEWER
    created_at DATETIME NOT NULL,
    FOREIGN KEY (kb_id) REFERENCES knowledge_bases(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id),
    UNIQUE KEY uk_kb_user (kb_id, user_id)
);

-- visibility 扩展
ALTER TABLE knowledge_bases 
  MODIFY COLUMN visibility VARCHAR(20) DEFAULT 'PRIVATE';
-- 允许值: PRIVATE / TEAM / PUBLIC
```

**涉及改造**：
- `KnowledgeBaseRepository` 查询逻辑从 `userId` 扩展为协作者检查
- `KnowledgeBaseController` 权限校验引入角色判断
- 前端新增协作成员管理界面
- 好友系统可能复用（好友 = 潜在协作者）

---

### 5.2 工作流引擎

**目标**：RAG 链路从硬编码管道升级为可配置 DAG。

> 此项工作极大，建议评估是否引入 Camunda / Flowable 轻量流程引擎，或自研简化版。

**最小可行方案**— 不引入流程引擎，仅做 "检索策略预设"：

```
知识库设置 → 检索策略预设:
  □ 向量检索 (top_k: __)
  □ 关键词检索 (top_k: __)
  □ 查询重写 (变体数: __)
  □ Rerank (模型: __)
  □ 上下文压缩 (是/否)
```

**涉及改造**：
- `knowledge_bases` 表新增 `retrieval_strategy` JSON 字段
- `KnowledgeBaseService.hybridSearch()` 根据策略配置动态编排

---

### 5.3 知识图谱增强 KB

**目标**：从 KB 文档中自动抽取实体和关系，支持多跳推理检索。

**方案**：

```
文档 → 分块 → 每块调用 LLM 抽取实体/关系
  → Neo4j / 图嵌入 → 检索时 1 跳/2 跳扩展
  → 实体匹配加权融合向量检索结果
```

**与记忆系统知识图谱的关系**：
- 记忆系统图谱：用户级，从对话中抽取个人信息
- KB 知识图谱：知识库级，从文档中抽取领域知识
- 可共享图谱基础设施，独立存储

---

### 5.4 在线文档 / URL 导入

**目标**：支持输入 URL 自动抓取网页内容导入知识库。

**方案**：使用 Jsoup 抓取网页 + Readability 算法提取正文。

**涉及文件**：

| 文件 | 改动 |
|------|------|
| `service/UrlDocumentImporter.java` | 新增 |
| `KnowledgeBaseController.java` | `POST /api/kb/{id}/docs/import-url` |

---

## 六、实施路线图

```
Phase 1（1-2 周）
  ├─ P0-1: 混合检索 + Rerank  ← 最大 ROI
  └─ P0-2: Prompt 模板可配置

Phase 2（2-4 周）
  ├─ P1-1: 扩展文档格式
  ├─ P1-2: 查询重写
  ├─ P1-3: 可配置分块策略
  └─ P1-4: PDF 表格提取（Phase 1）

Phase 3（4-8 周）
  ├─ P2-1: 结构化溯源
  ├─ P2-2: RAG 质量评估
  ├─ P2-3: 多 Embedding 模型
  └─ P2-4: 增量更新

Phase 4（8-16 周）
  ├─ P3-1: 多租户协作
  ├─ P3-2: 工作流引擎（简化版）
  ├─ P3-3: 知识图谱增强 KB
  └─ P3-4: URL 导入
```

---

## 七、关键风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| Rerank API 延迟增加用户等待时间 | 用户体验下降 | 前端骨架屏 + 检索结果流式注入 |
| 混合检索 BM25 索引膨胀 | 磁盘占用增加 | 定期清理已删除知识库的索引目录 |
| 文档格式解析质量不稳定 | 检索精度波动 | 每种格式编写单元测试 + 异常回退策略 |
| OCR 识别率低（扫描件质量差） | 检索召回率低 | 提供 OCR 质量预览，允许用户手动修正 |
| 多 Embedding 模型维度不一致 | Collection 创建/删除逻辑复杂 | 严格按 `kb_{kbId}` 隔离，创建时记录维度 |
