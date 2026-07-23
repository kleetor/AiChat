# Plans5.0 阶段汇总

> 日期：2026-07-23
> 测试状态：50 tests, 0 failures, BUILD SUCCESS

---

## 一、阶段涵盖范围

Plans5.0 聚焦两大系统：**RAG 知识库** 与 **长期记忆系统**，包含功能增强、安全加固、架构优化。

---

## 二、RAG 知识库系统

### 2.1 核心功能实现

| 优先级 | 功能 | 状态 |
|--------|------|------|
| P0 | 混合检索（向量 + BM25）→ RRF 融合 → Rerank 精排 | ✅ |
| P0 | 知识库级 Prompt 模板可配置（`{context}` / `{query}` 占位符） | ✅ |
| P1 | 文档格式扩展至 8 种（PDF/DOCX/XLSX/PPTX/HTML/TXT/MD/图片） | ✅ |
| P1 | LLM 查询重写（Query Rewriter） | ✅ |
| P1 | 可配置分块策略（chunk_size / overlap 知识库级） | ✅ |
| P1.5 | Tesseract OCR 集成 — 扫描件 PDF 自动识别 | ✅ |
| — | OCR 优化方案（12 项中实施核心项，删减 6 项边缘场景） | ✅ |
| **P0 新** | **PDF 嵌入图片视觉识别** — PDImageXObject 提取 → 过滤 → 视觉 API 描述 → 拼入文本 | ✅ |

### 2.2 PDF 图片视觉识别架构

```
文字层 PDF → PDFBox 提取文字
           → 提取嵌入图片 (PDResources → PDImageXObject)
           → 四重过滤 (尺寸/宽高比/纯色/哈希去重)
           → 并行视觉 API 识别 (Base64, 4线程)
           → SHA-256 缓存 (PdfImageCacheService)
           → 格式化为描述文本拼入
           → 文字无效 → Tesseract OCR 回退
```

### 2.3 安全加固

| ID | 风险 | 修复 | 状态 |
|----|------|------|------|
| R1 | 文件类型伪装 | Magic Bytes 校验 (PDF/DOCX/XLSX/PPTX/PNG/JPG) | ✅ |
| R2 | PDF Zip Bomb | MAX_PAGE_PIXELS=100M 像素上限 | ✅ |
| R3 | 分块膨胀攻击 | MAX_CHUNKS_PER_DOC=500 | ✅ |
| R4 | 用户存储配额 | KB 200文档 / 用户 1000文档 / 500MB | ✅ |
| R5 | API 费用放大 | max-images-per-doc=50 + 四重过滤 | ✅ |
| R6 | 异步任务积压 | 有界线程池 (core=2, max=4, queue=20) | ✅ |
| R7 | OCR 缓存投毒 | 读写大小校验 1MB | ✅ |
| R8 | XSS/注入 | ChunkingService prompt injection 过滤 | ✅ |
| R9 | 重索引频率 | RateLimitRule 10次/天 | ✅ |
| — | 文件大小上限 | 5MB → 20MB 统一 | ✅ |

### 2.4 关键文件

| 文件 | 行数 | 说明 |
|------|------|------|
| `KbRetrievalService.java` | — | 混合检索编排 |
| `KbBm25IndexService.java` | — | KB 专用 Lucene BM25 索引 |
| `HybridRetrievalService.java` | — | RRF 融合 + Rerank 精排 |
| `QueryRewriterService.java` | — | LLM 查询重写 |
| `ChunkingService.java` | ~90 | 递归字符分割 + injection 过滤 |
| `PdfParser.java` | ~580 | PDF 解析 + 图片提取 + 视觉识别 + OCR |
| `ImageService.java` | ~245 | S3 上传 + Base64 视觉 API |
| `PdfImageCacheService.java` | ~85 | 图片识别结果缓存 |
| `PdfImageProperties.java` | ~20 | 6 项可配置参数 |

---

## 三、长期记忆系统

### 3.1 核心架构

**记忆模型**：
```
模式1: 自动提取 — LLM 异步提取关键事实 → 向量库
模式2: 默认注入 — 最近N条清晰/模糊期记忆注入上下文
模式3: 按需回溯 — 混合检索（向量+BM25+实体 → RRF → Rerank）
模式4: 懒衰减 — 读取时实时检查 FULL→BRIEF→TITLE→EXPIRED
```

**知识图谱**：
```
三元组提取 → 实体节点 → 实体间关系 → 双向边自动追加
实体消歧建议 (LLM) → 手动合并 → 时态冲突检测
```

**混合检索**：ChromaDB 向量 + Lucene BM25 + 知识图谱实体 → RRF 融合 → SiliconFlow Rerank 精排

**隔离机制**：prompt-scoped — 共享记忆 + 角色专属记忆双轨制

### 3.2 安全加固

| ID | 风险 | 修复 | 状态 |
|----|------|------|------|
| H1 | 记忆 API 无频率限制 | 3 条 RateLimit 规则 (add/search/clear) | ✅ |
| H2 | 无输入校验 + prompt injection | Controller 长度/空值校验 + sanitizeMemoryValue() 三层过滤 | ✅ |
| H3 | 实体合并无用户隔离 | mergeEntities +userId 归属校验 | ✅ |
| M1 | BM25 查询 DoS | MAX_QUERY_LENGTH=500 | ✅ |
| M2 | search query 可为 null | Controller 空值拒绝 | ✅ |
| M3 | add value 无空值 | Controller 空值拒绝 | ✅ |
| M4 | 记忆闭环注入 | extractAndStore 处 sanitize | ✅ |
| M5 | 实体名称外泄 LLM | MAX_ENTITIES_FOR_LLM=50 | ✅ |

**Prompt Injection 防御链路**：
```
用户输入 → Controller 校验(长度+空值)
         → MemoryService.sanitizeMemoryValue()
         → ChromaDB/MySQL 存储
         → MessageContextBuilder → sanitizeMemoryValue() 二次过滤
         → LLM Prompt
```

### 3.3 关键文件

| 文件 | 行数 | 说明 |
|------|------|------|
| `MemoryService.java` | ~460 | 记忆核心逻辑 + sanitize |
| `MemoryController.java` | ~137 | REST API + 校验 + 实体合并接口 |
| `GraphMemoryService.java` | ~375 | 知识图谱 + 实体消歧 + 合并 |
| `HybridRetrievalService.java` | — | 三路召回 + RRF + Rerank |
| `Bm25IndexService.java` | ~155 | Lucene BM25 + 查询长度限制 |
| `MessageContextBuilder.java` | ~250 | LLM 上下文构建 + 记忆注入过滤 |
| `RateLimitInterceptor.java` | ~310 | 17 条限频规则 (含 3 条记忆) |

---

## 四、安全体系总览

本阶段共发现并修复 **23 项安全风险**：

| 系统 | 高危 | 中危 | 低危 | 已修复 |
|------|------|------|------|--------|
| 知识库 | 5 | 1 | 4 | **10** |
| 记忆系统 | 3 | 5 | 3 | **11** |

安全文档：
- [rag-kb-security-assessment.md](plans5.0/rag-kb-security-assessment.md) — 知识库 10 项风险全量分析
- [memory-security-plan.md](plans5.0/memory-security-plan.md) — 记忆系统 13 项风险修复计划

---

## 五、测试结果

```
Tests: 50 total
AichatApplicationTests:             1 passed
ImagePreprocessorTest:              6 passed
OcrCacheServiceTest:                5 passed
OcrPostProcessorTest:               9 passed
PdfParserTextValidTest:             7 passed
ToolCallAccumulatorTest:            8 passed
ToolDefinitionTest:                 3 passed
ToolRegistryTest:                  11 passed
Result: BUILD SUCCESS — 0 failures, 0 errors
```

---

## 六、文档索引

| 文档 | 内容 |
|------|------|
| [rag-knowledge-base-analysis.md](plans5.0/rag-knowledge-base-analysis.md) | 知识库差距分析 |
| [rag-knowledge-base-analysis-v2.md](plans5.0/rag-knowledge-base-analysis-v2.md) | 知识库 v2 评估 |
| [rag-knowledge-base-improvement-plan.md](plans5.0/rag-knowledge-base-improvement-plan.md) | P0-P3 改进方案 |
| [rag-knowledge-base-re-evaluation.md](plans5.0/rag-knowledge-base-re-evaluation.md) | 改进后重新评估 |
| [rag-ocr-optimization-plan.md](plans5.0/rag-ocr-optimization-plan.md) | OCR 18→12 项优化方案 |
| [rag-pdf-image-recognition-plan.md](plans5.0/rag-pdf-image-recognition-plan.md) | PDF 图片视觉识别计划 |
| [rag-kb-security-assessment.md](plans5.0/rag-kb-security-assessment.md) | 知识库安全评估 (10 项风险) |
| [memory-system-analysis.md](plans5.0/memory-system-analysis.md) | 记忆系统完整架构 |
| [memory-system-hybrid-retrieval-rerank.md](plans5.0/memory-system-hybrid-retrieval-rerank.md) | 混合检索+Rerank 方案 |
| [memory-system-knowledge-graph-temporal.md](plans5.0/memory-system-knowledge-graph-temporal.md) | 知识图谱+时态管理 |
| [memory-system-prompt-scoped-isolation.md](plans5.0/memory-system-prompt-scoped-isolation.md) | Prompt 级隔离方案 |
| [memory-system-framework-comparison.md](plans5.0/memory-system-framework-comparison.md) | 记忆框架对比 |
| [memory-security-plan.md](plans5.0/memory-security-plan.md) | 记忆系统安全加固计划 |
| [knowledge-graph-optimization.md](plans5.0/knowledge-graph-optimization.md) | 知识图谱六项优化 |
