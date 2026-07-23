# HanaChat 知识库系统分析（更新版）

> 最后更新：2026-07-23
> 基于 P0 + P1 + P1.5 + OCR 专项优化全部完成后的完整架构
> OCR 专项优化：12 项（图像预处理、后处理、缓存、表格提取、页级并行等）

---

## 一、系统概览

HanaChat 知识库系统是一个**内嵌于聊天应用的 RAG 引擎**，通过"文档上传 → 多格式解析 → 智能分块 → 双写向量+关键词索引 → 多路召回 → 精排注入"的全链路，让大模型基于用户私有文档生成精准回答。

### 核心能力

| 维度 | 能力 |
|------|------|
| 文档解析 | 14 种格式：txt / md / pdf / docx / xlsx / pptx / html / csv + **jpg / jpeg / png / tiff / tif / bmp** |
| OCR 识别 | Tesseract 5.x 扫描件识别 + **图像预处理管线**（灰度→二值化→降噪→倾斜校正）+ **OCR 结果缓存** |
| 分块策略 | 递归语义分割 + 按知识库可调 chunk_size / overlap |
| 向量存储 | ChromaDB 嵌入式，每 KB 独立 Collection `kb_{kbId}` |
| 关键词索引 | Lucene BM25（SmartChineseAnalyzer），每 KB 独立索引目录 |
| 嵌入模型 | SiliconFlow BAAI/bge-large-zh-v1.5（1024 维） |
| Rerank 精排 | SiliconFlow BAAI/bge-reranker-v2-m3（Cross-Encoder） |
| 查询优化 | LLM 查询重写（2-3 变体）+ 多查询并行召回 + 跨查询 RRF 融合 |
| 结果注入 | 可自定义 Prompt 模板，支持 `{context}` / `{query}` 占位符 |
| 缓存 | Caffeine：kbList(3min) + kbDocs(3min)，写操作清除 + **OCR 结果 SHA-256 本地缓存** |

---

## 二、技术栈

| 组件 | 技术 | 用途 |
|------|------|------|
| 元数据存储 | MySQL 8.0 + Spring Data JPA + Flyway | 知识库 / 文档元数据、统计计数 |
| 向量存储 | ChromaDB（嵌入式进程） | 语义检索，每知识库独立 Collection |
| 关键词索引 | Apache Lucene 9.12（SmartChineseAnalyzer） | BM25 关键词匹配 |
| 嵌入模型 | SiliconFlow BAAI/bge-large-zh-v1.5 | 1024 维文本向量化 |
| Rerank | SiliconFlow BAAI/bge-reranker-v2-m3 | Cross-Encoder 精排 |
| OCR | Tesseract 5.x（tess4j JNI 封装）+ 图像预处理 | 扫描件 PDF / 图片文字识别 |
| 文档解析 | PDFBox 3.0 / Apache POI 5.3 / Jsoup 1.18 / tabula 1.0 | 多格式文档文本提取 + PDF 表格 |
| 图像预处理 | JDK BufferedImage（纯 Java） | 灰度化 / Otsu 二值化 / 中值滤波 / 倾斜校正 |
| OCR 缓存 | SHA-256 + 本地文件 | 重索引时避免重复 OCR 处理 |
| 配置管理 | Spring Boot ConfigurationProperties | RAG / Embedding / OCR / Rerank 参数 |
| 缓存 | Caffeine | kbList + kbDocs 两级缓存 |

### 与记忆系统的技术栈复用

```
知识库                        记忆系统
  │                             │
  ├─ ChromaDB ──────────── 同一 ChromaDB 实例（不同 Collection）
  ├─ Lucene BM25 ───────── 同一 Lucene 库（不同索引目录）
  ├─ SiliconFlow Embed ─── 同一 Embedding API
  ├─ SiliconFlow Rerank ── 同一 Rerank API
  ├─ Caffeine ──────────── 同一 CacheManager
  └─ BaseChromaDBService ─ 同一泛型抽象基类 T=kbId / T=userId
```

---

## 三、数据存储结构

### 3.1 MySQL 表

#### knowledge_bases

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 知识库 ID |
| name | VARCHAR(100) | 名称 |
| description | VARCHAR(500) | 描述 |
| user_id | BIGINT FK | 所属用户 |
| visibility | VARCHAR(20) | PRIVATE |
| prompt_template | VARCHAR(2000) | 自定义回答风格模板 |
| chunk_size | INT | 分块大小（null 使用系统默认 500） |
| chunk_overlap | INT | 重叠字符数（null 使用系统默认 50） |
| doc_count | INT | 文档数量（原子更新冗余计数） |
| chunk_count | INT | 分块总数（原子更新冗余计数） |
| total_size | BIGINT | 文件总大小（原子更新冗余计数） |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

#### kb_documents

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 文档 ID |
| kb_id | BIGINT FK | 所属知识库 |
| file_name | VARCHAR(255) | 原始文件名 |
| file_type | VARCHAR(10) | pdf / docx / xlsx / pptx / html / txt / md |
| file_size | BIGINT | 字节数 |
| s3_key | VARCHAR(500) | 本地存储路径 |
| status | VARCHAR(20) | PROCESSING / READY / ERROR |
| chunk_count | INT | 分块数 |
| error_msg | TEXT | 错误信息 |
| created_at | DATETIME | 创建时间 |

### 3.2 ChromaDB 向量存储

- Collection 命名：`kb_{kbId}`
- 向量维度：1024
- 每条向量结构：

| 字段 | 说明 |
|------|------|
| id | `doc_{documentId}_chunk_{chunkIndex}` |
| embedding | 1024 维 float 向量 |
| document | 分块文本内容 |
| metadata | `document_id`, `chunk_index`, `kb_id`, `file_name` |

### 3.3 Lucene BM25 索引

- 索引目录：`./data/bm25-kb/{kbId}/`
- 内存懒加载：`ConcurrentHashMap<Long, IndexWriter>` 管理
- 字段：`chunkId(StringField)`, `kbId(LongPoint)`, `docId(LongPoint)`, `text(TextField)`

### 3.4 文件存储

- 本地路径：`./uploads/kb/{UUID}_{原始文件名}`
- 上传时路径穿越防护
- 文件大小上限：10MB

---

## 四、文档处理管道

### 4.1 上传流程

```
POST /api/kb/{kbId}/docs/upload (MultipartFile, ?forceOcr=false)
  │
  ├─ 权限校验：知识库存在且属于当前用户
  ├─ 安全处理：File.getOriginalFilename() 剥离路径、防路径穿越
  ├─ 白名单过滤：pdf / docx / xlsx / pptx / html / htm / txt / md / csv / jpg / jpeg / png / tiff / tif / bmp
  ├─ 大小限制：10MB
  ├─ 本地存储：./uploads/kb/{UUID}_{原文件名}
  │
  ├─ TransactionTemplate 确保 commit：
  │     MySQL 写入 KbDocument (status=PROCESSING)
  │     PDF 上传时可附带 forceOcr=true → 存入 forceOcrFlags
  │
  └─ CompletableFuture.runAsync() 异步处理 ──────┐
                                                   │
     ┌─────────────────────────────────────────────┘
     ▼
  processDocument(docId, filePath)
     │
     ├─ ① 文档解析（DocumentParser 工厂模式分发）
     │      ├─ PdfParser    → PDFBox 提取 → isTextValid() 判断有效性
     │      │                  → 无效时查 OCR 缓存（SHA-256）
     │      │                  → 缓存未命中 → Tesseract OCR（5页以上页级并行）
     │      │                  → 图像预处理（灰度→二值化→降噪→校正）
     │      │                  → OCR 后处理（空格规范化+段落合并）
     │      │                  → 强制 OCR 模式：跳过 PDFBox，直接 OCR
     │      ├─ ImageParser  → ImageIO 读取 → 预处理 → Tesseract OCR → 后处理
     │      ├─ DocxParser   → Apache POI 段落+表格
     │      ├─ ExcelParser  → Apache POI 逐 Sheet 转 Markdown 表格
     │      ├─ PptxParser   → Apache POI 逐 Slide 文本
     │      ├─ HtmlParser   → Jsoup 去标签保留正文
     │      └─ TxtParser    → UTF-8 直接读取（含 md/csv）
     │
     ├─ ② 智能分块（ChunkingService）
     │      读取 KB 级 chunk_size / chunk_overlap 配置
     │      \n\n → \n → 。 → ； → ， 递归语义分割
     │      超长段落按 chunk_size 硬切 + overlap 滑动窗口
     │
     ├─ ③ 双写索引
     │      ├─ ChromaDB：批量向量化（SiliconFlow Embedding）→ 写入 Collection
     │      └─ Lucene BM25：分词索引写入 ./data/bm25-kb/{kbId}/
     │
     ├─ ④ 状态更新
     │      doc.status = "READY"
     │      doc.chunkCount = 分块数
     │      kb.doc_count++, kb.chunk_count += N, kb.total_size += fileSize
     │
     └─ ⑤ 缓存刷新
            事务提交后清除 kbDocs / kbList 缓存，前端立即感知
```

### 4.2 OCR 处理管线（优化后）

```
PdfParser.parse(Path filePath)
  │
  ├─ 强制 OCR 模式? (前端 forceOcr=true)
  │     └─ 是 → 跳过 PDFBox，直接进入 OCR 阶段
  │
  ├─ PDFBox 提取文字层 + tabula 表格提取
  │
  ├─ isTextValid() 判断有效性
  │     └─ 有效？→ 返回文本（PDFBox 成功）
  │
  └─ 无效 → OCR 回退：
          │
          ├─ ① OCR 缓存查询（SHA-256 文件哈希）
          │     └─ 命中 → 直接返回缓存结果
          │
          ├─ ② 页级并行判断（>= 5 页 → doOcrParallel；否则 → doOcr）
          │
          ├─ ③ 逐页 OCR 管线：
          │       │
          │       ├─ PDFRenderer 渲染为 BufferedImage（250 DPI，降低 30% 内存）
          │       │
          │       ├─ ImagePreprocessor 预处理：
          │       │     ├─ 灰度化（TYPE_BYTE_GRAY，4B→1B/像素）
          │       │     ├─ Otsu 自适应二值化
          │       │     ├─ 3×3 中值滤波降噪
          │       │     └─ 霍夫投影倾斜校正（<1°跳过）
          │       │
          │       ├─ Tesseract 识别（chi_sim+eng，Spring Bean 单例复用）
          │       │
          │       └─ OcrPostProcessor 后处理：
          │             ├─ 中英文空格规范化
          │             └─ 段落合并
          │
          ├─ ④ OCR 超时 → 抛出 OcrFailedException → 前端显示 ERROR + errorMsg
          │
          └─ ⑤ 结果写入 OCR 缓存（下次重索引直接命中）
```

#### 识别精度提升路径

```
原始扫描件
    │
    ├─ 改进前：isBlank() 判断 → Tesseract 原图 → 原始输出
    │    识别率：~60-70%（受噪声/倾斜/低对比度影响）
    │
    └─ 改进后：isTextValid() 判断 → 缓存检查 → 预处理 → Tesseract → 后处理
         识别率：~85-95%（灰度化+二值化消除噪声，倾斜校正还原排版）
```

#### 内存占用对比

| 阶段 | 优化前 | 优化后 |
|------|--------|--------|
| 单页 BufferedImage | ~33MB（ARGB, 300 DPI） | ~5.8MB（GRAY, 250 DPI） |
| 100 页峰值 | ~3.3GB（顺序） | ~23MB（4线程并行, 灰度） |

### 4.3 文档删除与重索引

```
删除：chromaDBService.deleteByDocument() → bm25Service.removeByDocument() → DB delete → 文件删除
重索引：删除旧向量+旧BM25 → 重新异步 processDocument()
删除知识库：chromaDBService.deleteCollection() → bm25Service.deleteIndex() → DB cascade
```

---

## 五、检索管线

### 5.1 完整流程

```
用户发送消息（附带 knowledgeBaseId）
  │
  ▼
MessageContextBuilder.buildMessagesArray()
  │
  ├─ [Phase 0] 查询重写（可选）
  │     rag.retrieve.query-rewrite-enabled=true
  │     QueryRewriterService → LLM prompt
  │     "考勤怎么算？" → ["考勤怎么算？", "员工考勤管理制度 计算方法", "考勤制度 计算规则"]
  │
  ├─ [Phase 1] 多查询并行多路召回
  │     对每个 query 变体：
  │       ├─ ChromaDB 语义检索 (top20)      ─┐
  │       └─ Lucene BM25 关键词检索 (top20)   ─┼─ 并行
  │     N 个查询 × 2 路 = 2N 路并行
  │
  ├─ [Phase 2] 跨查询 RRF 融合
  │     score(chunk) = Σ 1/(60 + rank_i)    ← 跨所有查询叠加
  │     候选池降为 topK*3
  │
  ├─ [Phase 3] Cross-Encoder 精排
  │     SiliconFlow BAAI/bge-reranker-v2-m3
  │     用原始查询对候选打分 → 取 topK=5
  │
  └─ [Phase 4] 注入 System Prompt
        读取 KB 的 prompt_template → 替换 {context} / {query}
        注入消息数组第 5 位（系统规则 → 角色 → 记忆 → 摘要 → **KB** → 历史 → 当前）
```

### 5.2 容错降级

| 故障场景 | 降级行为 |
|----------|---------|
| ChromaDB 故障 | 仅 BM25 路检索，ChromaDB 路返回空 |
| BM25 索引故障 | 仅向量路检索，BM25 路返回空 |
| Rerank API 故障 | 降级返回 RRF 融合排序结果 |
| 查询重写 LLM 故障 | 仅用原始查询，跳过重写 |
| OCR 引擎未安装 | PDFBox/isTextValid 失败时抛 OcrFailedException，前端显示 ERROR |
| OCR 超时（5分钟） | 抛 OcrFailedException("OCR 超时")，不阻断上传 |
| OCR 缓存命中 | 跳过 Tesseract，直接返回缓存文本 |
| 图片文件损坏 | ImageParser 抛 IOException("图片文件损坏或格式不支持") |
| tabula 表格提取异常 | 静默跳过，不阻断 PDFBox 文字层提取 |

---

## 六、知识库 CRUD 与缓存

### 6.1 API 端点

| 方法 | 路径 | 功能 | 缓存行为 |
|------|------|------|---------|
| POST | `/api/kb/create` | 创建知识库 + ChromaDB Collection | 清除 kbList |
| GET | `/api/kb/list` | 用户知识库列表（GROUP BY 实时统计） | 读缓存 kbList(3min) |
| PUT | `/api/kb/{id}` | 编辑名称/描述/模板/分块参数 | 清除 kbList |
| DELETE | `/api/kb/{id}` | 删除知识库（Collection + BM25 + MySQL） | 清除 kbList + kbDocs |
| POST | `/api/kb/{kbId}/docs/upload` | 上传文档 | 清除 kbList + kbDocs |
| GET | `/api/kb/{kbId}/docs` | 文档列表 | 读缓存 kbDocs(3min) |
| DELETE | `/api/kb/docs/{docId}` | 删除文档 | 清除 kbList + kbDocs |
| POST | `/api/kb/docs/{docId}/reindex` | 重新索引 | 清除 kbList + kbDocs |

### 6.2 缓存策略

| 缓存名 | Key | TTL | 清除触发 |
|--------|-----|-----|---------|
| kbList | userId | 3 min | 创建/更新/删除/上传/重索引 |
| kbDocs | userId + "_" + kbId | 3 min | 上传/删除/重索引 + processDocument 异步完成后 |

### 6.3 计数一致性

```
写入路径：JPQL @Query UPDATE 原子增减
  incrementCounts(kbId, +Δdoc, +Δchunk, +Δsize)
  decrementCounts(kbId, -Δdoc, -Δchunk, -Δsize)

读取路径：GROUP BY 聚合实时查询（消除 N+1）
  KbDocumentRepository.aggregateByKbIds(List<Long> kbIds)
```

---

## 七、架构全景图

```
                             用户操作
                                │
            ┌───────────────────┼───────────────────┐
            │                   │                   │
            ▼                   ▼                   ▼
      上传文档             选择知识库            管理知识库
            │                   │                   │
            ▼                   │                   ▼
   ┌────────────────┐           │          ┌──────────────────┐
   │DocumentParser  │           │          │KnowledgeBase     │
   │ ┌────────────┐ │           │          │Controller        │
   │ │ PdfParser   │ │◄─ OCR    │          │ (CRUD + Cache)    │
   │ │ DocxParser  │ │           │          └────────┬─────────┘
   │ │ ExcelParser │ │           │                   │
   │ │ PptxParser  │ │           │                   ▼
   │ │ HtmlParser  │ │           │          ┌──────────────────┐
   │ │ TxtParser   │ │           │          │  MySQL           │
   │ └────────────┘ │           │          │  knowledge_bases  │
   └───────┬────────┘           │          │  kb_documents     │
           │                    │          └──────────────────┘
           ▼                    │
   ┌────────────────┐           │
   │ChunkingService │           │
   │ (KB级可配置)    │           │
   └───────┬────────┘           │
           │                    │
     ┌─────┼─────┐              │
     │           │              │
     ▼           ▼              │
┌─────────┐ ┌──────────┐        │
│ChromaDB │ │BM25 索引  │        │
│kb_{id}  │ │bm25-kb/  │        │
└────┬────┘ │{kbId}/   │        │
     │      └────┬─────┘        │
     │           │              │
     └─────┬─────┘              │
           │                    │
           ▼                    ▼
   ┌─────────────────────────────────────┐
   │         KbRetrievalService          │
   │  ┌──────────────────────────────┐   │
   │  │ 0. QueryRewriterService      │   │
   │  │    LLM 查询重写 (2-3 变体)    │   │
   │  │                              │   │
   │  │ 1. 多查询 × 双路并行召回      │   │
   │  │    向量(top20) + BM25(top20)  │   │
   │  │                              │   │
   │  │ 2. 跨查询 RRF 融合            │   │
   │  │    Σ 1/(60+rank)             │   │
   │  │                              │   │
   │  │ 3. Cross-Encoder Rerank      │   │
   │  │    bge-reranker-v2-m3        │   │
   │  └──────────────────────────────┘   │
   └─────────────────┬───────────────────┘
                     │
                     ▼
   ┌─────────────────────────────────────┐
   │      MessageContextBuilder          │
   │  系统规则 → 角色 → 记忆 → 摘要      │
   │  → 知识库(Prompt模板) → 历史 → 当前  │
   └─────────────────┬───────────────────┘
                     │
                     ▼
                  LLM API
```

---

## 八、关键配置

```properties
# 嵌入模型
embedding.model=BAAI/bge-large-zh-v1.5
embedding.api-url=https://api.siliconflow.cn/v1/embeddings
embedding.batch.size=32

# Rerank 精排
rerank.model=BAAI/bge-reranker-v2-m3
rerank.api-url=https://api.siliconflow.cn/v1/rerank

# RAG 检索
rag.chunk.size=500
rag.chunk.overlap=50
rag.retrieve.top-k=5
rag.retrieve.candidate-size=20
rag.retrieve.query-rewrite-enabled=true

# OCR（优化后）
ocr.enabled=true
ocr.tessdata-path=/usr/share/tessdata      # Docker 路径
ocr.language=chi_sim+eng
ocr.dpi=250                                # 300 → 250（内存降低 30%，精度无损）
ocr.timeout-seconds=300

# OCR 缓存
# 自动启用：./uploads/kb/ocr_cache/{sha256}.txt

# 缓存
spring.cache.type=caffeine
# kbDocs TTL=3min, kbList TTL=3min

# ChromaDB
chromadb.url=http://localhost:8000
```

---

## 九、与主流框架对比

| 评分维度 | HanaChat 改造后 | Dify | RAGFlow | FastGPT |
|----------|:---:|:---:|:---:|:---:|
| 文档格式支持 | ⭐⭐⭐⭐⭐ 14种 | ⭐⭐⭐⭐⭐ 10+ | ⭐⭐⭐⭐⭐ 10+ | ⭐⭐⭐⭐ 6+ |
| 检索精度（混合+重排） | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ |
| 查询优化（重写+多查） | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐ |
| 可配置性（分块+模板） | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐ |
| OCR 能力 | ⭐⭐⭐⭐ 预处理+缓存 | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐ |
| 部署运维成本 | ⭐⭐⭐⭐⭐ 4容器 | ⭐⭐ 8+容器 | ⭐⭐⭐ | ⭐⭐⭐⭐ |
| 技术栈一致性 | ⭐⭐⭐⭐⭐ 纯Java | ⭐⭐⭐ Python | ⭐⭐⭐ Python | ⭐⭐⭐ Node.js |
| 代码可控性 | ⭐⭐⭐⭐⭐ 自研 | ⭐⭐⭐ 开源平台 | ⭐⭐⭐ 开源平台 | ⭐⭐⭐ 开源平台 |

**核心优势**：检索引擎经过 P0/P1 改造后达到行业第一梯队（向量+BM25+RRF+Rerank+查询重写）；OCR 从 ⭐⭐ 提升至 ⭐⭐⭐⭐（图像预处理+缓存+表格提取）；纯 Java 技术栈与现有系统无缝融合，无跨语言运维成本；部署极简（仅需 ChromaDB 容器，无需 Python/Node.js 环境）。

**定位**：不是取代 Dify/RAGFlow 的通用平台，而是聊天应用中**深度集成的专用 RAG 引擎**，检索精度、OCR 能力和运维简便是核心差异点。

---

## 十、关键文件清单

| 文件 | 职责 |
|------|------|
| `model/KnowledgeBase.java` | 知识库实体（含 prompt/chunk 配置） |
| `model/KbDocument.java` | 文档实体 |
| `repository/KnowledgeBaseRepository.java` | 知识库 Repository（原子计数更新） |
| `repository/KbDocumentRepository.java` | 文档 Repository（GROUP BY 聚合） |
| `controller/KnowledgeBaseController.java` | REST API 控制器（含 forceOcr 参数） |
| `service/KnowledgeBaseService.java` | 核心编排（CRUD+上传+异步处理+forceOcr+缓存管理） |
| `service/ChunkingService.java` | 递归语义分块（支持 KB 级参数） |
| `service/ChromaDBService.java` | 知识库向量存储操作 |
| `service/KbBm25IndexService.java` | 知识库 Lucene BM25 索引 |
| `service/KbRetrievalService.java` | 混合检索编排（重写→召回→融合→精排） |
| `service/QueryRewriterService.java` | LLM 查询重写 |
| `service/BaseChromaDBService.java` | ChromaDB 泛型抽象基类 |
| `service/MessageContextBuilder.java` | 消息构建（KB 检索+模板注入） |
| `service/parser/PdfParser.java` | PDF 解析（PDFBox → isTextValid → OCR缓存 → 预处理 → Tesseract → 后处理 → 页级并行） |
| `service/parser/ImageParser.java` | **新建** - 图片 OCR 解析（ImageIO → 预处理 → Tesseract → 后处理） |
| `service/parser/DocxParser.java` | Word 文档解析 |
| `service/parser/ExcelParser.java` | Excel 表格解析 |
| `service/parser/PptxParser.java` | PPT 幻灯片解析 |
| `service/parser/HtmlParser.java` | HTML 网页解析 |
| `service/parser/TxtParser.java` | 纯文本/MD/CSV 解析 |
| `service/ocr/ImagePreprocessor.java` | **新建** - 图像预处理（灰度→Otsu→中值滤波→倾斜校正） |
| `service/ocr/OcrPostProcessor.java` | **新建** - OCR 后处理（空格规范化+段落合并） |
| `service/ocr/OcrCacheService.java` | **新建** - OCR 结果 SHA-256 本地缓存 |
| `config/OcrConfig.java` | **新建** - Tesseract Spring Bean 单例注册 |
| `config/OcrFailedException.java` | **新建** - OCR 失败异常（前端 ERROR 状态展示） |
| `config/props/RagProperties.java` | RAG 参数配置 |
| `config/props/EmbeddingProperties.java` | 嵌入模型配置 |
| `config/props/OcrProperties.java` | OCR 参数配置（DPI 300→250） |
| `config/CacheConfig.java` | Caffeine 缓存配置 |
| `db/migration/V1__baseline.sql` | KB 表建表 |
| `db/migration/V11__kb_prompt_template.sql` | Prompt 模板字段 |
| `db/migration/V12__kb_chunk_config.sql` | 分块配置字段 |
| `Dockerfile` | 含 Tesseract OCR 安装 |
| `pom.xml` | Maven 依赖（新增 tabula 1.0.5） |
| `frontend/src/lib/services.ts` | 前端 KB API（uploadKBDocument 新增 forceOcr 参数） |
| `frontend/src/components/modals/KBModal.tsx` | KB 管理弹窗（PDF 强制 OCR checkbox + 图片格式 accept） |

### 新增模块目录结构

```
service/ocr/                        ← 新建 OCR 服务包
├── ImagePreprocessor.java          图像预处理管线
├── OcrPostProcessor.java           OCR 后处理
└── OcrCacheService.java            OCR 结果缓存

config/
├── OcrConfig.java                  ← 新建 Tesseract Bean
└── OcrFailedException.java         ← 新建 OCR 异常类
```
