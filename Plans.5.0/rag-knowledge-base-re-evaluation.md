# HanaChat RAG 知识库系统重新评估

> 本文件在 P0 + P1 改造全部完成后，对知识库系统做一次全面的能力重评估。  
> 前置文档：[rag-knowledge-base-analysis.md](rag-knowledge-base-analysis.md)、[rag-knowledge-base-improvement-plan.md](rag-knowledge-base-improvement-plan.md)

---

## 一、已完成改造总览

### P0（已交付）— 检索精度 + 可配置性

| 序号 | 改造项 | 实现方式 |
|------|--------|---------|
| P0-1 | 混合检索 + Rerank | `KbBm25IndexService`（按 KB 隔离 Lucene BM25）+ `KbRetrievalService`（向量+BM25 双路→RRF 融合→SiliconFlow Reranker 精排） |
| P0-2 | Prompt 模板可配置 | `knowledge_bases.prompt_template` 字段，支持 `{context}` / `{query}` 占位符，前端可编辑 |

### P1（已交付）— 文档处理 + 检索召回

| 序号 | 改造项 | 实现方式 |
|------|--------|---------|
| P1-1 | 扩展文档格式 | `DocumentParser` 接口体系，支持：txt / md / pdf / docx / xlsx / pptx / html / csv（8 种） |
| P1-2 | 查询重写 | `QueryRewriterService` 调用 LLM 生成 2-3 个检索变体，`KbRetrievalService` 多查询并行召回+跨查询 RRF 融合 |
| P1-3 | 可配置分块 | `knowledge_bases.chunk_size` / `chunk_overlap`，`ChunkingService.split(text, cs, ol)` 参数化 |
| P1-4 | PDF 表格提取 | `PdfParser.extractTables()` 预留接口，待后续引入 tabula-java |

---

## 二、当前能力矩阵（与市面主流框架对照）

| 对比维度 | 改造前 | 改造后 | Dify | RAGFlow | FastGPT |
|----------|--------|--------|------|---------|---------|
| **文档格式** | 3 种 | **8 种** | 10+ | 10+ | 6+ |
| **混合检索** | 纯向量 | **向量+BM25+RRF** | 可选 | **内建** | 可选 |
| **Rerank 精排** | 无 | **BGE-Reranker-v2-m3** | 可选 | **内建** | 可选 |
| **查询重写** | 无 | **LLM 多变体** | 工作流可编排 | 支持 | 无 |
| **分块策略** | 固定 | **按 KB 可调** | 多策略可选 | 可视化 | 可配 |
| **Prompt 模板** | 硬编码 | **可自定义** | Prompt IDE | 可配 | 可配 |
| **PDF 深度解析** | 纯文本 | 预留表格接口 | 基础 | **业界最强** | 基础 |
| **OCR 识别** | 无 | 待定（本方案） | 基础 | **内置** | 无 |

> 改造后，HanaChat 在 **检索精度、文档处理、可配置性** 三个核心维度已追平或接近 Dify/RAGFlow。  
> 剩余差距主要在 **OCR / PDF 深度解析**，其中 OCR 为本文件规划重点。

---

## 三、待解决的核心短板

### 3.1 扫描件 PDF 完全不可用（0→1 问题）

**现状**：`PdfParser` 使用 PDFBox 提取文本。扫描件 PDF 是图片，无文字层，返回空字符串 → 0 条 chunk → 知识盲区。

**影响**：企业内部的大量历史文档（合同、报告、书籍扫描件）完全无法检索。

**结论**：这是当前最大短板，优先级从 P2 提升至 **P1.5（P1 已完，但不是 P2 的工程复杂度）**。

### 3.2 PDF 表格未实际提取

**现状**：`PdfParser.extractTables()` 为空实现，有注释标记待后续增强。

**影响**：PDF 中的表格数据（财务报表、技术参数表）在 chunk 中丢失结构，检索精度打折。

---

## 四、OCR 模型引入方案

### 4.1 决策结论

```
性价比最优方案：Tesseract + tess4j
  - 部署成本 ≈ 零（2 行 Dockerfile + 1 个 Maven 依赖）
  - 中文识别率 ≈ 80-85%
  - 开发工作量 ≈ 0.5 天
  - 无额外运维负担

不引入 PaddleOCR（当前阶段）
  - 识别率提升的 10% 不足以覆盖独立 Python 服务的运维成本
  - 等待 Tesseract 上线后收集真实数据再决策
```

### 4.2 技术方案

#### 依赖

```xml
<!-- pom.xml 追加 -->
<dependency>
    <groupId>net.sourceforge.tess4j</groupId>
    <artifactId>tess4j</artifactId>
    <version>5.12.0</version>
</dependency>
```

```dockerfile
# Dockerfile 追加
RUN apt-get update && apt-get install -y \
    tesseract-ocr \
    tesseract-ocr-chi-sim \
    tesseract-ocr-eng \
    && rm -rf /var/lib/apt/lists/*
```

#### PdfParser 改造

```java
// service/parser/PdfParser.java 核心逻辑

@Component
public class PdfParser implements DocumentParser {

    // 保留 PDFBox 提取逻辑...

    private String extractTables(PDDocument doc) {
        // 待 Phase 2 引入 tabula-java
        return "";
    }

    /**
     * OCR 回退：PDFBox 提取为空时，将每页渲染为图片，调用 Tesseract 识别。
     */
    private String ocrFallback(byte[] pdfBytes) {
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            StringBuilder sb = new StringBuilder();
            PDFRenderer renderer = new PDFRenderer(doc);
            Tesseract tesseract = new Tesseract();
            tesseract.setDatapath(ocrDataPath);       // tessdata 目录
            tesseract.setLanguage("chi_sim+eng");
            tesseract.setVariable("user_defined_dpi", "300");

            for (int i = 0; i < doc.getNumberOfPages(); i++) {
                // 逐页渲染为 BufferedImage，不残留磁盘文件
                BufferedImage image = renderer.renderImageWithDPI(i, 300);
                String pageText = tesseract.doOCR(image);
                sb.append(pageText).append("\n");
                image.flush();  // 立即释放内存
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("OCR 识别失败", e);
            throw new RuntimeException("OCR 识别失败: " + e.getMessage(), e);
        }
    }
}
```

#### 调用时机

```java
// PdfParser.parse() 核心流程
String text = pdfBoxExtract(pdfBytes);   // 优先 PDFBox 提取
if (text.isBlank()) {
    text = ocrFallback(pdfBytes);        // 为空时回退 OCR
    ocrUsed = true;                      // 标记，前端展示用
}
return text;
```

### 4.3 安全与容错

| 机制 | 说明 |
|------|------|
| **超时** | `CompletableFuture.runAsync()` 加 5 分钟超时，超时标记 `ERROR: OCR 处理超时` |
| **内存** | 逐页渲染+逐页释放 `image.flush()`，避免 100 页 PDF 全部加载到内存 |
| **降级** | OCR 引擎未安装时回退到纯 PDFBox，不阻断上传流程 |
| **开关** | 环境变量 `OCR_ENABLED=true`，设为 false 完全关闭 OCR |
| **来源标注** | OCR 提取的 chunk 在 metadata 中标记 `ocr_source=true`，注入 prompt 时显示"（扫描件 OCR 识别）" |
| **线程隔离** | OCR 任务使用独立的小线程池（2 线程），避免阻塞文档处理主线程 |

### 4.4 配置项

```properties
# application.properties 追加
ocr.enabled=true
ocr.tessdata-path=./tessdata              # 语言包路径
ocr.language=chi_sim+eng
ocr.dpi=300
ocr.timeout-seconds=300
```

### 4.5 风险与缓解

| 风险 | 等级 | 缓解 |
|------|------|------|
| 识别率不足导致检索偏差 | 中 | chunk 标注 OCR 来源，prompt 提示 AI 交叉验证 |
| 超大 PDF 处理超时 | 中 | 逐页处理+超时保护+页数上限（100页） |
| 中英混排误识别 | 低 | 使用 `chi_sim+eng` 混合语言模型 |
| Tesseract 二进制未安装 | 低 | 启动时检测，未安装则跳过 OCR 并打 WARN |
| 临时图片占用磁盘 | 低 | 使用 `BufferedImage` 内存模式，不写磁盘 |

---

## 五、重新排序的优先级矩阵

> 基于当前改造完成后的真实状态重新评估。

| 优先级 | 改进项 | 工作量 | 价值 | 说明 |
|--------|--------|--------|------|------|
| **P1.5** | **Tesseract OCR 集成** | 0.5 天 | 高 | 解决扫描件完全不可检索的 0→1 问题，成本极低 |
| P2 | 检索结果结构化溯源 | 1 天 | 中 | 前端高亮引用来源，提升用户信任度 |
| P2 | PDF 表格提取（tabula-java） | 0.5 天 | 中 | 填充 `extractTables()` 的空实现 |
| P2 | 增量更新 | 1.5 天 | 中 | 减少重复向量化成本，避免全量重建 |

---

## 六、更新后的架构全貌

```
                            上传文档
                               │
                               ▼
                    ┌──────────────────────┐
                    │   DocumentParser     │  ← 工厂模式分发
                    │   ┌────────────────┐ │
                    │   │ PdfParser       │ │  PDFBox → OCR 回退
                    │   │ DocxParser      │ │  Word 段落+表格
                    │   │ ExcelParser     │ │  xlsx→Markdown 表格
                    │   │ PptxParser      │ │  幻灯片文本
                    │   │ HtmlParser      │ │  Jsoup 去标签
                    │   │ TxtParser       │ │  纯文本/Markdown
                    │   └────────────────┘ │
                    └──────────┬───────────┘
                               │
                               ▼
                    ┌──────────────────────┐
                    │   ChunkingService    │  按 KB 级 chunk_size / overlap 分块
                    └──────────┬───────────┘
                               │
                 ┌─────────────┼─────────────┐
                 │                           │
                 ▼                           ▼
        ┌─────────────────┐        ┌──────────────────────┐
        │  ChromaDB        │        │  KbBm25IndexService   │
        │  向量存储         │        │  Lucene BM25 关键词    │
        │  Collection:      │        │  索引: ./data/        │
        │  kb_{kbId}        │        │       bm25-kb/{kbId}/ │
        └────────┬──────────┘        └──────────┬───────────┘
                 │                              │
                 └──────────┬───────────────────┘
                            │
                            ▼
                 ┌──────────────────────┐
                 │  KbRetrievalService  │
                 │  ┌─────────────────┐ │
                 │  │ 1. 查询重写     │ │  QueryRewriterService → LLM 变体
                 │  │ 2. 多查询并行   │ │  N 个 query × 双路召回
                 │  │ 3. RRF 跨查询   │ │  score = Σ 1/(K+rank)
                 │  │ 4. Rerank 精排  │ │  SiliconFlow BGE-Reranker-v2-m3
                 │  └─────────────────┘ │
                 └──────────┬───────────┘
                            │
                            ▼
                 ┌──────────────────────┐
                 │  MessageContextBuilder│
                 │  ┌─────────────────┐ │
                 │  │ 读取 KB 模板    │ │  {context} / {query} 占位符
                 │  │ 格式化注入      │ │  来源标注（含 OCR 标记）
                 │  └─────────────────┘ │
                 └──────────────────────┘
```

---

## 七、里程碑

| 阶段 | 内容 | 状态 |
|------|------|------|
| P0 | 混合检索+Rerank + Prompt 模板 | 已完成 |
| P1 | 8 种文档格式 + 查询重写 + 可配置分块 + 表格预留 | 已完成 |
| **P1.5** | **Tesseract OCR 集成** | **本文档规划，待实施** |
| P2 | 结构化溯源 / PDF 表格 / 增量更新 | 规划中 |
