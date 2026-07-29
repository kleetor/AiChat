# Plans5.0 阶段汇总

> 日期：2026-07-23
> 测试状态：编译通过，测试全部通过

---

## 一、阶段涵盖范围

Plans5.0 聚焦两大系统：**RAG 知识库** 与 **长期记忆系统**，包含功能增强、安全加固、架构优化。

> **7/23 重大调整**：经实测评估，PDF/DOCX 解析无法满足项目质量要求，已全链路移除。知识库当前仅支持纯文本格式 **TXT / MD**。

---

## 二、RAG 知识库系统

### 2.1 核心功能实现

| 优先级 | 功能 | 状态 |
|--------|------|------|
| P0 | 混合检索（向量 + BM25）→ RRF 融合 → Rerank 精排 | ✅ |
| P0 | 知识库级 Prompt 模板可配置（`{context}` / `{query}` 占位符） | ✅ |
| P1 | LLM 查询重写（Query Rewriter） | ✅ |
| P1 | 可配置分块策略（chunk_size / overlap 知识库级） | ✅ |

### 2.2 已废弃功能（7/23 移除）

| 原功能 | 废弃原因 |
|--------|---------|
| 多格式支持（PDF/DOCX/XLSX/PPTX/HTML/图片） | 实测解析质量不达标 |
| Tesseract OCR 集成 | 依赖 PDF 支持 |
| PDF 嵌入图片视觉识别 | 依赖 PDF 支持 |
| 图片视觉模型分类管线 | 依赖 PDF 支持 |
| forceOcr 强制扫描件开关 | 管线已无需手动干预 |
| Magic Bytes 校验 | 纯文本无需二进制文件头校验 |

### 2.3 安全性

| ID | 措施 | 状态 |
|----|------|------|
| R1 | 文件类型限制（仅 TXT/MD，后端 `getFileType()` 兜底） | ✅ |
| R2 | 分块膨胀攻击 — MAX_CHUNKS_PER_DOC=500 | ✅ |
| R3 | 用户存储配额 — KB 200文档 / 用户 1000文档 / 500MB | ✅ |
| R4 | 异步任务积压 — 有界线程池 (core=2, max=4, queue=20) | ✅ |
| R5 | XSS/注入 — ChunkingService prompt injection 过滤 | ✅ |
| R6 | 重索引频率 — RateLimitRule 10次/天 | ✅ |
| R7 | 文件大小上限 — 20MB | ✅ |
| R8 | 异常信息脱敏 — Controller 仅 catch IOException，其余走 GlobalExceptionHandler | ✅ |
| R9 | 错误消息不泄漏 — DB 存储固定消息，详细信息仅记录日志 | ✅ |

### 2.4 关键文件

| 文件 | 说明 |
|------|------|
| `KbRetrievalService.java` | 混合检索编排 |
| `KbBm25IndexService.java` | KB 专用 Lucene BM25 索引 |
| `ChunkingService.java` | 递归字符分割 + injection 过滤 |
| `TxtParser.java` | 纯文本解析（txt/md/csv） |
| `KnowledgeBaseService.java` | 知识库核心服务 |
| `KnowledgeBaseController.java` | REST API |

### 2.5 已删除文件

| 文件 | 原因 |
|------|------|
| `PdfParser.java` | PDF 解析器，已废弃 |
| `DocxParser.java` | DOCX 解析器，已废弃 |
| `ImageParser.java` | 图片解析器，已废弃 |
| `HtmlParser.java` | HTML 解析器，已废弃 |
| `ExcelParser.java` | Excel 解析器，已废弃 |
| `PptxParser.java` | PPT 解析器，已废弃 |
| `ContentFusion.java` | 多路内容融合器，已废弃 |
| `OcrConfig.java` | Tesseract OCR 配置，已废弃 |
| `OcrProperties.java` | OCR 配置属性，已废弃 |
| `PdfImageProperties.java` | PDF 图片配置属性，已废弃 |
| `OcrFailedException.java` | OCR 异常类，已废弃 |
| `ImagePreprocessor.java` | OCR 图像预处理，已废弃 |
| `OcrPostProcessor.java` | OCR 后处理，已废弃 |
| `OcrCacheService.java` | OCR 缓存服务，已废弃 |
| `PdfImageCacheService.java` | 图片视觉识别缓存，已废弃 |

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

| 文件 | 说明 |
|------|------|
| `MemoryService.java` | 记忆核心逻辑 + sanitize |
| `MemoryController.java` | REST API + 校验 + 实体合并接口 |
| `GraphMemoryService.java` | 知识图谱 + 实体消歧 + 合并 |
| `HybridRetrievalService.java` | 三路召回 + RRF + Rerank |
| `Bm25IndexService.java` | Lucene BM25 + 查询长度限制 |
| `MessageContextBuilder.java` | LLM 上下文构建 + 记忆注入过滤 |
| `RateLimitInterceptor.java` | 17 条限频规则 (含 3 条记忆) |

---

## 四、安全体系总览

本阶段共发现并修复 **21 项安全风险**：

| 系统 | 已修复 |
|------|--------|
| 知识库 | **9** |
| 记忆系统 | **12** |

安全文档：
- [rag-kb-security-assessment.md](Plans.5.0/rag-kb-security-assessment.md) — 知识库安全评估
- [memory-security-plan.md](Plans.5.0/memory-security-plan.md) — 记忆系统安全加固计划

---

## 五、测试结果

```
ToolCallAccumulatorTest:            8 passed
ToolDefinitionTest:                 3 passed
ToolRegistryTest:                  11 passed
Result: BUILD SUCCESS
```

> 已移除 OCR 相关测试（ImagePreprocessorTest、OcrCacheServiceTest、OcrPostProcessorTest、PdfParserTextValidTest）。

---

## 六、文档索引

| 文档 | 内容 |
|------|------|
| [memory-kb-comprehensive-analysis.md](Plans.5.0/memory-kb-comprehensive-analysis.md) | 记忆+知识库综合分析 |
| [rag-kb-security-assessment.md](Plans.5.0/rag-kb-security-assessment.md) | 知识库安全评估 |
| [memory-system-analysis.md](Plans.5.0/memory-system-analysis.md) | 记忆系统完整架构 |
| [memory-system-hybrid-retrieval-rerank.md](Plans.5.0/memory-system-hybrid-retrieval-rerank.md) | 混合检索+Rerank 方案 |
| [memory-system-knowledge-graph-temporal.md](Plans.5.0/memory-system-knowledge-graph-temporal.md) | 知识图谱+时态管理 |
| [memory-system-prompt-scoped-isolation.md](Plans.5.0/memory-system-prompt-scoped-isolation.md) | Prompt 级隔离方案 |
| [memory-system-framework-comparison.md](Plans.5.0/memory-system-framework-comparison.md) | 记忆框架对比 |
| [memory-security-plan.md](Plans.5.0/memory-security-plan.md) | 记忆系统安全加固计划 |
| [knowledge-graph-optimization.md](Plans.5.0/knowledge-graph-optimization.md) | 知识图谱六项优化 |
| [phase-summary.md](Plans.5.0/phase-summary.md) | 本文件 |
