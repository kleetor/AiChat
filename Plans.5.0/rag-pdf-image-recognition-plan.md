# PDF 嵌入图片视觉识别 - 改进计划

## 1. 背景与问题

当前 [PdfParser](src/main/java/com/example/aichat/service/parser/PdfParser.java) 的解析策略：

```
文字层 PDF  → PDFBox 提取文字 + Tabula 提取表格  → 结果
            → 文字无效 → Tesseract OCR 整页识别  → 结果
```

**缺失**：PDF 中嵌入的图片（架构图、截图、流程图、图表）在文字层提取时被完全忽略，导致这些信息静默丢失，无法进入知识库索引。

## 2. 方案总览

在文字层提取和 OCR 回退之间，插入图片提取+视觉识别阶段：

```
                    文字有效
PDF  → PDFBox 提取文字  ───→  提取嵌入图片 → 过滤 → 视觉 API 描述 → 拼合文本 → 输出
          │                                                    ↑
          └── 文字无效 ──→ Tesseract OCR 整页识别 ──────────────┘
```

## 3. 详细设计

### 3.1 图片提取（P0）

使用 PDFBox 现有依赖，通过 `PDResources.getXObjectNames()` 遍历每页的 `PDImageXObject`，提取为 `BufferedImage`。

```java
// 伪代码
for (PDPage page : doc.getPages()) {
    PDResources resources = page.getResources();
    for (COSName name : resources.getXObjectNames()) {
        PDXObject xobj = resources.getXObject(name);
        if (xobj instanceof PDImageXObject) {
            BufferedImage image = ((PDImageXObject) xobj).getImage();
            // 进入过滤+识别流程
        }
    }
}
```

### 3.2 图片过滤策略（P0）

减少无效 API 调用，按以下优先级过滤：

| 过滤条件 | 阈值 | 原因 |
|---------|------|------|
| 最小尺寸 | 宽 < 150px 或 高 < 150px | 过滤 icon、装饰元素 |
| 哈希去重 | MD5 / SHA-256 | 同一图片多处引用只识别一次 |
| 宽高比极端 | 比值 > 10 或 < 0.1 | 过滤分割线、banner 条 |
| 纯色检测 | 唯一颜色数 < 16 | 过滤纯色背景块 |

### 3.3 视觉识别（P0）

复用现有 [ImageService](src/main/java/com/example/aichat/service/ImageService.java)，但需要新增一个不依赖 S3 上传的本地识别方法：

- 现有 `recognizeImage(String imageUrl)` 需要先将图片上传到 S3
- 新增 `recognizeImage(BufferedImage)` 或 `recognizeImage(byte[])` 直接发送 Base64 编码图片到视觉 API
- 提示词针对 RAG 场景优化：`"请详细描述这张图片中的内容，包括图表类型、关键数据、流程步骤、架构关系等。如果是文字截图，请提取其中的文字。"`

### 3.4 结果拼合格式（P0）

图片描述插入到每页文字之后，使用分隔标记：

```
[第1页文字内容...]

[图片 1-1 描述：系统架构图，展示三层架构：前端层（React）、后端层（Spring Boot）、数据层（MySQL + Redis）...]

[图片 1-2 描述：数据库 ER 图，包含 users、orders、products 三张表...]

[第2页文字内容...]
```

### 3.5 缓存机制（P1）

按 `PDF文件SHA-256 + 图片序号` 作为缓存 key，存储视觉 API 返回的描述文本。与现有 [OcrCacheService](src/main/java/com/example/aichat/service/ocr/OcrCacheService.java) 复用相同的文件缓存模式。

### 3.6 并行处理（P1）

多图片文档使用线程池并行调用视觉 API，与 OCR 并行共用 `ocrExecutor`（4 线程），但需注意：
- 视觉 API 调用是网络 IO 密集型，OCR 是 CPU 密集型
- 建议独立的图片识别线程池，线程数可配置（默认 4）

### 3.7 配置项（P1）

在 `application.yml` 中新增：

```yaml
pdf:
  image:
    recognition:
      enabled: true           # 是否启用图片视觉识别
      min-width: 150          # 最小图片宽度（px）
      min-height: 150         # 最小图片高度（px）
      max-images-per-doc: 50  # 单文档最多识别图片数
      timeout-seconds: 30     # 单张图片识别超时（秒）
      # API 配置复用现有 image.* 配置（api-key, api-url, model）
```

## 4. 实现步骤

### P0 - 核心功能
| 步骤 | 文件 | 变更 |
|------|------|------|
| 1 | `PdfParser.java` | 新增 `extractImages()` 方法，遍历 PDResources 提取 PDImageXObject |
| 2 | `PdfParser.java` | 新增 `filterImages()` 方法，实现尺寸/去重/纯色过滤 |
| 3 | `ImageService.java` | 新增 `recognizeImage(byte[] imageBytes, String contentType)` Base64 识别方法 |
| 4 | `PdfParser.java` | 新增 `recognizeImages()` 方法，调用 ImageService 批量识别 |
| 5 | `PdfParser.java` | 修改 `extractText()` 或新增入口，将图片描述拼入文本 |

### P1 - 优化
| 步骤 | 文件 | 变更 |
|------|------|------|
| 6 | `PdfImageCacheService.java` | 新增缓存服务，复用 OcrCacheService 模式 |
| 7 | `PdfParser.java` | 图片识别并行化 |
| 8 | `PdfImageProperties.java` | 新增配置属性类 |
| 9 | `application.yml` | 新增 `pdf.image.recognition.*` 配置项 |

### P2 - 可选增强
| 步骤 | 变更 |
|------|------|
| 10 | 识别失败降级：记录日志但不阻塞文档处理 |
| 11 | 图表类型检测：根据图片内容自动标注"架构图""数据图表""截图"等 |
| 12 | 与上下文关联：结合图片附近的文字（如"图1. 系统架构"）增强描述 |

## 5. 依赖关系

- **复用**：PDFBox（已引入）、ImageService（已存在）、视觉 API（已配置）
- **新增依赖**：无，全部基于现有基础设施

## 6. 风险评估

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| API 调用超时 | 文档处理变慢 | 单图片 30s 超时，失败跳过不阻塞 |
| 图片过多导致费用 | 成本增加 | `max-images-per-doc=50` 上限 + 过滤策略 |
| 描述质量不稳定 | RAG 检索效果差 | 优化提示词，结合上下文 |
| 内存占用 | 大图 OOM | 限制图片最大分辨率，缩放后再识别 |

## 7. 测试策略

- 单元测试：`extractImages()` / `filterImages()` 方法覆盖各种边界
- 集成测试：准备含 5-10 张嵌入图片的测试 PDF
- 回归测试：确保纯文字 PDF 行为不受影响
