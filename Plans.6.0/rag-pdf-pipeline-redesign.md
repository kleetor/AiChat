# PDF 识别管线重构方案 — 内容分类 · 并行识别 · 乱码回收

> 创建日期：2026-07-23
> 基于 rag-pdf-image-recognition-plan.md 和 rag-ocr-optimization-plan.md 的实践反馈  
> 目标：用内容分类 + 模型路由替代当前的串行拼接，解决 OCR 乱码污染和识图结果覆盖丢失问题

---

## 零、当前管线问题诊断

### 0.1 当前流程

```
forceOcr 路径：
  doOcrWithCache()  ──→  extractAndRecognizeImages()  ──→  简单拼接
     (串行阻塞)              (串行阻塞)

正常路径：
  extractText()  ──→  extractAndRecognizeImages()  ──→  文本无效 → OCR 回退
                                                              ↑
                                                   识图描述被覆盖丢失
```

### 0.2 三个核心缺陷

| # | 缺陷 | 根因 | 影响 |
|---|------|------|------|
| 1 | **OCR 与识图串行** | `doOcrWithCache` 返回后才调用 `extractAndRecognizeImages` | 耗时 = OCR + 识图，长文档+多图片场景可能超时 |
| 2 | **OCR 图片区乱码无过滤** | Tesseract 整页识别，图表/照片区域产生 `??? II ## ██` 等乱码，`OcrPostProcessor` 无乱码检测 | 乱码文本混入分块，污染检索索引，降低召回精度 |
| 3 | **OCR 回退路径丢弃识图结果** | `isTextValid() → false` 时 `text = doOcrWithCache()` 直接覆盖，之前 `extractAndRecognizeImages()` 的结果丢失 | 扫描件 PDF 完全丢失图片信息 |

### 0.3 为什么当前简单拼接不work

```
OCR 整页输出（含图片区域乱码）：
  "公司营收增长20%  ???  IIII  ##  ████  预计明年增长35%"

识图模型描述（同一张图片）：
  "[第3页图片描述：柱状图显示2025年营收增长20%，2026年预计增长35%]"

拼接后：
  "公司营收增长20%  ???  IIII  ##  ████  预计明年增长35%
   [第3页图片描述：柱状图显示2025年营收增长20%，2026年预计增长35%]"

结果：同一信息出现两次，一次乱码一次准确，LLM/Rerank 都会受到干扰
```

---

## 一、新管线架构

### 1.1 核心思想：视觉模型前置 · OCR 按需调用

```
                    ┌─ 元数据预过滤（免费，<1ms）
                    │   尺寸/宽高比/纯色 → 过滤 icon、装饰图
                    │
PDF 嵌入图片 ──→ 视觉模型（分类 + 描述）
                    │
                    ├─ chart ─────────→ 视觉描述 ✓（OCR 跳过）
                    ├─ diagram ───────→ 视觉描述 ✓（OCR 跳过）
                    ├─ photo ─────────→ 视觉描述 ✓（OCR 跳过）
                    │
                    ├─ table ─────────→ 视觉描述 + OCR 高DPI ✚
                    └─ text_document ──→ 视觉描述 + OCR 高DPI ✚
```

**关键逻辑**：视觉模型一次性完成两个任务——**分类**（决定要不要 OCR）+ **描述**（产出文本）。OCR 只在视觉模型判定为需要精确提取的类型时才触发，从"主力"降级为"补充角色"。

### 1.2 新管线全景

```
PdfParser.parse()
│
├─ Phase 0: 元数据提取 + 尺寸级预过滤（< 100ms，零像素加载）
│   ├─ extractImageMeta(): 只读 PDImageXObject 尺寸/页码
│   ├─ 元数据级过滤: 尺寸 < 150px / 宽高比 > 10 / < 0.1 → 直接丢弃
│   └─ 输出: List<ImageMeta>  ← 每条 ~100B
│
├─ Phase 1: PDFBox 文字层提取（独立，快速）
│   └─ TextStripper 全文档 → Map<Integer, String>
│
├─ Phase 2: 视觉模型前置（分类 + 描述，逐张流式）
│   │
│   │  for each ImageMeta:
│   │    ├─ getImage() 加载单张
│   │    ├─ 纯色检测（采样检测，< 16 唯一色 → 跳过 API 调用）
│   │    ├─ 视觉 API: 分类 + 描述（一次调用，prompt 同时要求两者）
│   │    │     → ImageResult { type, description }
│   │    ├─ image.flush() 释放
│   │    │
│   │    └─ if type ∈ {table, text_document}:
│   │         └─ 入队 OCR 任务
│   │
│   └─ 输出: Map<Integer, List<ImageResult>>
│
├─ Phase 3: OCR 按需执行（仅 table / text_document 类型）
│   │  与 Phase 2 共享线程池，Phase 2 产出 OCR 候选后立即提交
│   │  每张图: 高 DPI 渲染(300) → 预处理 → Tesseract → 后处理
│   │
│   └─ 输出: Map<String, String>  imageHash → ocrText
│
├─ Phase 4: 智能融合
│   ├─ PDFBox 文字 + 所有视觉描述（chart/diagram/photo/table/text）
│   ├─ table 类型: 视觉描述作为概述 + OCR 数据作为精确值
│   ├─ text_document 类型: OCR 为主，视觉描述作为元信息
│   └─ 按页码 + 类型标注格式化
│
└─ Phase 5: 输出 → ChunkingService
```

### 1.3 视觉模型的分类 Prompt

```java
static final String CLASSIFY_AND_DESCRIBE_PROMPT = """
    请分析这张图片：

    1. 判断类型（严格选择一项）：
       - chart: 统计图表（柱状图、折线图、饼图、散点图等数据可视化）
       - diagram: 流程图、架构图、思维导图、示意图
       - photo: 照片、自然图像、非文字为主的截图
       - table: 表格、数据表、价目表、清单（以行列结构呈现）
       - text_document: 文本文档截图、扫描件（以文字内容为主）

    2. 描述内容：
       - 如果是 chart: 描述图表类型、关键数据趋势和数值
       - 如果是 diagram: 描述流程/架构的关键节点和关系
       - 如果是 photo: 描述画面内容
       - 如果是 table: 概述表格结构和主要数据类别
       - 如果是 text_document: 概述文档主题和关键信息

    请用中文，按以下格式回答：
    TYPE: <类型>
    DESC: <描述内容>""";
```

### 1.4 并行策略

```
时间线：

Phase0 元数据  ██ (<100ms)
Phase1 PDFBox  ███ (2s)
Phase2 视觉模型 ████████████ (按图片数量，与Phase3并行)
               ├─ 图1: chart    → 描述，跳过OCR
               ├─ 图2: diagram  → 描述，跳过OCR
               ├─ 图3: table    → 描述 + 提交OCR任务 ──┐
               ├─ 图4: photo    → 描述，跳过OCR       │
               └─ 图5: text_doc → 描述 + 提交OCR任务 ──┤
                                                       │
Phase3 OCR按需  ────────────────────────────────────────┘
                ████████ (仅2张，30%的图片)

总耗时 ≈ Phase2 时间（视觉模型是瓶颈，OCR 在等待期内完成）
```

**与上一版方案的核心区别**：

| 维度 | 上一版（OCR并行） | 新版（视觉前置） |
|------|------|------|
| OCR 触发条件 | 所有扫描页整页 OCR | 仅视觉模型判定为 table/text_document |
| 乱码问题 | 需要 GarbledTextFilter | **不存在**（图表/照片从不进 OCR） |
| 视觉模型角色 | 仅描述 | 分类 + 描述（一次调用双重产出） |
| API 调用量 | OCR全页 + 视觉全图 | OCR 按需 + 视觉全图 |
| OCR 误用风险 | 高（图表→乱码） | 零（分类把关） |

---

## 二、图片类型分类（Phase 2 核心）

### 2.1 视觉模型分类 Prompt

```java
static final String CLASSIFY_AND_DESCRIBE_PROMPT = """
    请分析这张图片：

    1. 判断类型（严格选择一项）：
       - chart: 统计图表（柱状图、折线图、饼图、散点图等数据可视化）
       - diagram: 流程图、架构图、思维导图、示意图
       - photo: 照片、自然图像、非文字为主的截图
       - table: 表格、数据表、价目表、清单（以行列结构呈现）
       - text_document: 文本文档截图、扫描件（以文字内容为主）

    2. 描述内容：
       - 如果是 chart: 描述图表类型、关键数据趋势和数值
       - 如果是 diagram: 描述流程/架构的关键节点和关系
       - 如果是 photo: 描述画面内容
       - 如果是 table: 概述表格结构和主要数据类别
       - 如果是 text_document: 概述文档主题和关键信息

    请用中文，按以下格式回答：
    TYPE: <类型>
    DESC: <描述内容>""";
```

### 2.2 响应解析

```java
record ImageResult(ImageType type, String description) {}

enum ImageType {
    CHART, DIAGRAM, PHOTO,      // → 仅视觉描述，跳过 OCR
    TABLE, TEXT_DOCUMENT;        // → 视觉描述 + OCR

    static ImageType from(String label) {
        return switch (label.strip().toLowerCase()) {
            case "chart" -> CHART;
            case "diagram" -> DIAGRAM;
            case "photo" -> PHOTO;
            case "table" -> TABLE;
            case "text_document" -> TEXT_DOCUMENT;
            default -> PHOTO; // 未识别默认按 photo 处理，不触发 OCR
        };
    }

    boolean needsOcr() {
        return this == TABLE || this == TEXT_DOCUMENT;
    }
}
```

### 2.3 分类精度保障

| 风险 | 缓解措施 |
|------|---------|
| 视觉模型误判 table 为 chart | OCR 漏掉的数据仍由视觉描述覆盖；描述中提示了"主要数据类别" |
| 视觉模型误判 chart 为 table | 多余的 OCR 调用成本可控（每张仅一次 Tesseract）；OCR 结果会经后处理，不会产生乱码（表格型图片 OCR 可正常识别） |
| TYPE 行解析失败 | `default -> PHOTO`，保守策略：不触发 OCR，至少视觉描述可用 |

### 2.4 OCR 调用量估算

典型 PDF 图片类型分布：

| 图片类型 | 占比 | 是否触发 OCR |
|---------|:---:|:---:|
| chart（图表） | ~30% | 否 |
| diagram（流程图） | ~20% | 否 |
| photo（照片） | ~20% | 否 |
| table（表格） | ~15% | **是** |
| text_document（扫描件） | ~15% | **是** |

**约 70% 的图片跳过 OCR**，OCR 调用量降低到原来的 30%。

---

## 三、页面级 OCR 回退（扫描件 PDF）

视觉模型分类针对的是**嵌入图片**（PDImageXObject）。对于完全没有文字层的扫描件 PDF（整页都是图片），仍需页面级 OCR：

```
PDFBox 提取文字 → isTextValid() ?
  ├─ true  → 文字层可用，跳过页面 OCR
  └─ false → 页面级 OCR 回退
              每页渲染 → 预处理 → Tesseract → 后处理
              注意：此时嵌入图片已由 Phase2 视觉模型处理，页面 OCR 仅补充纯文字区域
```

### 3.1 避免重复：页面 OCR 与图片 OCR 的分工

| 内容来源 | 由谁处理 | 输出 |
|---------|---------|------|
| PDF 文字层 | PDFBox TextStripper | 全文文字 |
| 嵌入图片（chart/diagram/photo） | 视觉模型（Phase 2） | 描述文本 |
| 嵌入图片（table/text_document） | 视觉模型 + OCR（Phase 2+3） | 描述 + OCR 文字 |
| 扫描件页面纯文字部分 | 页面级 OCR（Phase 3） | OCR 文字 |

---

## 四、融合策略（Phase 4）

### 4.1 新组件：`ContentFusion`

```
位置: service/parser/ContentFusion.java
职责: 将 PDFBox 文字、视觉描述、OCR 结果按内容类型智能融合
```

### 4.2 融合规则

```java
// 每种图片类型有不同的融合方式
Map<ImageType, FusionRule> rules = Map.of(
    CHART,          FusionRule.VISION_ONLY,        // 仅视觉描述
    DIAGRAM,        FusionRule.VISION_ONLY,        // 仅视觉描述
    PHOTO,          FusionRule.VISION_ONLY,        // 仅视觉描述
    TABLE,          FusionRule.VISION_OVERVIEW_OCR_DETAIL,  // 视觉概述 + OCR 数据
    TEXT_DOCUMENT,  FusionRule.OCR_PRIMARY_VISION_META       // OCR 为主 + 视觉元信息
);
```

### 4.3 输出格式

```
[第1页 - 文字]
PDFBox 提取的文字内容...

[第3页 - 图表: 柱状图]
2025年各季度营收数据：Q1 120万、Q2 185万、Q3 210万、Q4 260万，
呈逐季上升趋势，Q4同比增长32%。

[第5页 - 表格: 员工考核表]
该表为部门员工绩效考核汇总，包含姓名、部门、季度评分、排名四列。
[精确数据]
| 姓名   | 部门   | Q2评分 | 排名 |
|--------|--------|--------|------|
| 张三   | 研发部 | 95.2   | 1    |
| 李四   | 市场部 | 92.8   | 2    |
...

[第7页 - 扫描件: 合同文本]
该文档为技术服务合同，约定了甲乙双方的权利义务、付款方式及违约责任。
[OCR 识别全文]
甲方（委托方）：XX科技有限公司
乙方（服务方）：YY信息技术有限公司
...
```

---

## 五、GarbledTextFilter 降级

在视觉模型前置方案中，OCR 仅处理已被分类为 table/text_document 的图片。Tesseract 对这两类内容的识别不会产生 `??? ###` 等典型乱码（那是图表/照片的特征）。

**GarbledTextFilter 降为 P2**，仅在以下边缘场景启用以防万一：
- 视觉模型误分类（极少发生）
- 扫描件页面级 OCR 覆盖了混合内容

P2 时作为安全网保留，但不阻塞 P0 交付。

---

## 六、实现计划

### 6.1 新增文件

| 文件 | 职责 | 优先级 |
|------|------|--------|
| `service/parser/ContentFusion.java` | 按图片类型智能融合输出 | **P0** |
| `service/parser/ImageClassifier.java` | 调用视觉 API 分类+描述，解析 TYPE/DESC | **P0** |
| `service/ocr/GarbledTextFilter.java` | 乱码检测安全网 | P2 |

### 6.2 重构文件

| 文件 | 改动 | 优先级 |
|------|------|--------|
| `PdfParser.java` | `extractImages()` → `extractImageMeta()`；`parse()` 改为视觉前置管线 | **P0** |
| `PdfParser.java` | 新增 `classifyAndDescribe()` 调用视觉 API 获取 TYPE+DESC | **P0** |
| `PdfParser.java` | 新增 `ocrIfNeeded()` 仅对 TABLE/TEXT_DOCUMENT 类型触发 OCR | **P0** |

### 6.3 P0 任务

| # | 任务 | 说明 |
|---|------|------|
| P0-1 | `extractImageMeta()` 元数据提取 | 替换 `extractImages()`，只读尺寸不加载像素；元数据级过滤 |
| P0-2 | `ImageClassifier.classifyAndDescribe()` | 调用视觉 API，prompt 要求分类+描述，解析 TYPE/DESC 响应 |
| P0-3 | 视觉前置主流程 | Phase0(元数据) → Phase1(PDFBox) → Phase2(视觉分类+描述) → Phase3(条件OCR) → Phase4(融合) |
| P0-4 | `ContentFusion.merge()` | 按 ImageType 实现 5 种融合规则 |
| P0-5 | OCR 回退路径修复 | 回退时保留视觉描述，合并而非覆盖 |
| P0-6 | 单元测试 | ImageClassifier 响应解析、ContentFusion 融合规则、PdfParser 集成测试 |

### 6.4 P1 任务

| # | 任务 | 说明 |
|---|------|------|
| P1-1 | 视觉模型响应缓存 | TYPE+DESC 按图片 SHA-256 缓存，重新索引直接命中 |
| P1-2 | 表格 OCR 高 DPI | TABLE 类型以 300 DPI 渲染，保留空格结构 |

### 6.5 P2 任务

| # | 任务 | 说明 |
|---|------|------|
| P2-1 | GarbledTextFilter 安全网 | 对页面级 OCR 输出做兜底过滤 |
| P2-2 | 图片位置精确融合 | 利用 `PDImageXObject.getMatrix()` 按坐标插入描述 |

---

## 七、PdfParser 重构后核心结构

```java
@Component
public class PdfParser implements DocumentParser {

    private record ImageMeta(int pageNum, int width, int height, PDImageXObject xobj) {}
    private record ImageResult(ImageType type, String description) {}

    @Override
    public String parse(Path filePath) throws IOException {
        byte[] bytes = Files.readAllBytes(filePath);
        PDDocument doc = Loader.loadPDF(bytes);
        boolean forceOcr = Boolean.TRUE.equals(forceOcrFlag.get());

        // === Phase 0: 元数据提取（不加载像素） ===
        List<ImageMeta> metas = pdfImageProps.isEnabled()
            ? extractImageMeta(doc) : List.of();
        metas = filterByMetadata(metas);  // 尺寸/宽高比，纯元数据级

        // === Phase 1: PDFBox 文字提取 ===
        String pdfBoxText = forceOcr ? "" : extractText(doc);

        // === Phase 2: 视觉模型前置（分类 + 描述，逐张流式） ===
        List<ImageResult> imageResults = new ArrayList<>();
        List<ImageMeta> ocrCandidates = new ArrayList<>();

        for (ImageMeta meta : metas) {
            BufferedImage image = meta.xobj.getImage();  // 单张加载
            try {
                if (isSolidColor(image)) continue;       // 纯色块跳过
                ImageResult result = imageClassifier.classifyAndDescribe(image);
                imageResults.add(result);
                if (result.type().needsOcr()) {
                    ocrCandidates.add(meta);
                }
            } finally {
                image.flush();  // 立即释放
            }
        }

        // === Phase 3: OCR 按需执行 ===
        Map<String, String> ocrResults = new HashMap<>();
        for (ImageMeta meta : ocrCandidates) {
            BufferedImage image = meta.xobj.getImage();
            try {
                BufferedImage processed = preprocessor.preprocess(image);
                String text = tesseract.doOCR(processed);
                ocrResults.put(imageCacheService.sha256(imageToPngBytes(image)),
                              postProcessor.postProcess(text));
                processed.flush();
            } finally {
                image.flush();
            }
        }

        // === Phase 4: 页面级 OCR 回退（扫描件 PDF） ===
        String pageOcrText = "";
        if (!isTextValid(pdfBoxText) && ocrProps.isEnabled()) {
            pageOcrText = doOcrWithCache(bytes, doc, false);
        }

        // === Phase 5: 智能融合 ===
        return contentFusion.merge(pdfBoxText, imageResults, ocrResults, pageOcrText);
    }
}
```

---

## 八、方案评估

### 8.1 收益评估

| 维度 | 当前 | v2 视觉前置 | 收益 |
|------|------|------|:---:|
| 乱码产生 | 图表进 OCR，必产生乱码 | 图表不进 OCR | ✅ 根源消除 |
| OCR 调用量 | 100%（全页） | ~30%（仅 table/text + 扫描页） | ✅ 降低 70% |
| 内存峰值 | O(n×图) 可达 600MB | O(1×图) ~12MB | ✅ 消除 OOM |
| 识图结果丢失 | OCR 回退时覆盖 | 保留并融合 | ✅ 修复 Bug |
| 代码复杂度 | 简单但有 Bug | 新增 2 个组件 | ⚠️ 适度增加 |
| 视觉 API 调用 | 仅描述 | 分类 + 描述 | — 不变（一次调用） |

### 8.2 成本评估（API 调用）

| 场景 | 图片数 | 视觉 API 调用 | OCR 触发 | 增量成本 |
|------|:---:|:---:|:---:|:---:|
| 纯文字 PDF（0 图） | 0 | 0 | 0（仅 PDFBox） | 无 |
| 报告 PDF（5 图，含 1 表格） | 5 | 5 次 | 1 次 OCR | 与当前持平 |
| 画册 PDF（30 图，0 表格） | 30 | 30 次 | **0** | 与当前持平 |
| 技术手册（20 图，5 表格+3 扫描） | 20 | 20 次 | 8 次 OCR | 与当前持平 |

视觉 API 调用次数与当前完全一致——原来每张图片也调一次视觉 API 做描述，v2 只是 prompt 里多要求了一个 TYPE，不增加额外 API 开销。

### 8.3 延迟评估

**瓶颈分析**：

```
Phase0 元数据  <100ms  ← 可忽略
Phase1 PDFBox   2s     ← 固定开销
Phase2 视觉模型  逐张 2-5s/张  ← 主要瓶颈（网络 I/O）
Phase3 OCR      2-5s/张  ← 仅 30% 图片触发
Phase4 融合     <100ms  ← 可忽略
```

**典型场景耗时**（10 张图片，3 张触发 OCR，单线程流式）：

```
Phase2: 10 × 3s = 30s
Phase3: 3 × 3s = 9s（在 Phase2 开始后陆续提交，与 Phase2 部分重叠）

总耗时 ≈ 30-35s（Phase2 是瓶颈）
```

**改进空间**：Phase2 改为 4 路并行视觉 API 调用，图片流式分批：

```
Phase2: 10/4 × 3s = 8s（4 并发）
Phase3: 3 × 3s = 9s（在 Phase2 分类后触发，几乎完全与剩余 Phase2 重叠）

总耗时 ≈ 12s（4 并发后视觉 API 不再是绝对瓶颈）
```

> 此项留作 P1 优化，P0 先保证正确性，后续根据实测延迟决定是否加并发。

---

## 九、延伸风险分析

### 9.1 高影响风险

#### R1: 视觉模型误分类导致关键数据丢失 ⚠️ 中

```
场景：一份财务 PDF，核心数据在表格截图里
视觉模型将其误判为 "chart" → 跳过 OCR → OCR 精确数据丢失
用户检索"Q3营收"时只能命中视觉描述，查不到具体数字
```

| 评估 | 说明 |
|------|------|
| 概率 | 低（视觉模型对表格的识别准确率较高，实测 >90%） |
| 影响 | 中（视觉描述中仍包含关键数值概述，不会完全丢失信息） |
| 当前缓解 | chart 类型的描述 prompt 要求"描述关键数据趋势和数值" |
| **建议加强** | Phase2 输出日志记录每张图的分类结果，上线后监控误分类率；考虑加一个规则层：元数据显示图片长宽比接近 A4 比例 + 尺寸 >2000px → 强行标记为 text_document |

#### R2: 图片密集 PDF 导致视觉 API 耗时 ⚠️ 低（降级）

```
20MB 文件限制下，50 张嵌入图片意味着 PDF 完全是"画册"类型（零文字）。
知识库典型场景（报告/手册/论文）实际仅 10-20 张图。

极端场景：50 张图 × 3s = 150s + Phase1 2s + Phase3 ~20s = ~170s
远低于 300s 超时，单线程流式即可安全处理。
```

| 评估 | 说明 |
|------|------|
| 概率 | 低（20MB 下 50 张嵌入图要求 PDF 纯图片零文字，知识库场景罕见） |
| 影响 | 低（极端场景也仅 170s <<< 300s） |
| 当前缓解 | maxImagesPerDoc=50 + 20MB 文件上限双重约束 |
| **建议调整** | 单张 15s 超时兜底 + 4 路并行降为 P2，P0 不需要处理 |

#### R3: Tesseract 表格识别精度不足 ⚠️ 中

```
场景：复杂表格（合并单元格、嵌套表头）
视觉模型准确分类为 table → OCR 触发
但 Tesseract 对合并单元格的还原很差，列对齐错乱
OCR 输出："姓名部门评分张三研发95李四市场92"（丢失列结构）
```

| 评估 | 说明 |
|------|------|
| 概率 | 高（复杂表格非常常见） |
| 影响 | 中（视觉描述的概述仍可用，但精确数据丢失） |
| 当前缓解 | TABLE 类型的融合策略是"视觉概述 + OCR 数据"，视觉概述作为兜底 |
| **建议加强** | 对 OCR 输出的表格型文本做结构检测：如果连续行缺少规律的分隔符或对齐，标记为"表格结构可能丢失"；在输出中对 table 类型优先使用视觉描述中提取的数值 |

### 9.2 中等影响风险

#### R4: 视觉 API 错误累积 ⚠️ 低

```
场景：某张图片视觉 API 调用失败
当前: 单张异常被吞掉，继续下一张
但如果是 API 密钥过期、配额耗尽 → 50 张图片全部失败
→ 整个 PDF 只剩下 PDFBox 文字，所有图片信息静默丢失
```

| 评估 | 说明 |
|------|------|
| 概率 | 低（API 故障通常是偶发的，系统性故障少见） |
| 影响 | 高（丢失全部图片信息） |
| **建议加强** | 连续 3 张失败时全局标志置位，剩余图片降级为"不调视觉 API，全部走 OCR 回退"；文档处理结果中标注降级原因 |

#### R5: 扫描件 PDF 的页级 OCR 与嵌入图 OCR 重复 ⚠️ 低

```
场景：纯扫描件 PDF，每页是一个整页图片
PDFBox 无文字 → 触发页级 OCR（整页渲染 → Tesseract）
但同时 PDF 中可能有嵌入图片（PDImageXObject）
页级 OCR 已经覆盖了整页内容，嵌入图 OCR 是多余的
```

| 评估 | 说明 |
|------|------|
| 概率 | 中（扫描件 PDF 常见） |
| 影响 | 低（多余 OCR 调用浪费 CPU，但不会产生错误输出） |
| **建议加强** | Phase0 检测纯扫描件 PDF（全部页 PDFBox 无效）时，跳过 Phase2 的嵌入图片分支，直接走页级 OCR |

#### R6: 视觉描述与 PDFBox 文字重叠 ⚠️ 低

```
场景：PDF 中有图表标题"图3：2025年营收趋势"
PDFBox 提取了该标题
视觉模型描述中也提到"该图表展示了2025年营收趋势..."
信息重复，分块后 LLM 可能收到冗余内容
```

| 评估 | 说明 |
|------|------|
| 概率 | 高（图表标题几乎必然重复） |
| 影响 | 低（5 个分块的 System Prompt 上下文中，冗余一两句不影响回答质量；Rerank 也能处理） |
| 缓解 | 当前无需处理，LLM 对上下文冗余有天然容错；P2 可加入简单去重（Jaccard 相似度 < 0.8 的去重合并） |

### 9.3 低影响风险

#### R7: Prompt 被图片内容干扰

强对抗图片（如包含"ignore previous instructions"文字的截图）被视觉模型处理后，可能产生意外的 TYPE 输出。概率极低（需要用户恶意上传），影响低（仅影响单张图片分类），当前 Prompt 要求严格格式输出（`TYPE: ...`）已提供基本防护。

#### R8: 日志敏感信息泄露

视觉 API 请求和响应的日志中可能包含图片 Base64 编码。当前日志级别为 DEBUG，生产环境应使用 INFO。建议在 `ImageClassifier` 中对日志做截断处理。

---

## 十、不做的事情

| 事项 | 原因 |
|------|------|
| 接入视觉大模型做"页面理解" | 费用高，LLM 在回答阶段已有上下文理解能力 |
| 实现专业版面分析（LayoutParser） | 引入 Python 依赖破坏纯 Java 技术栈 |
| OCR 结果字典纠错 | LLM 纠错能力远强于规则纠错 |
| PDF 矢量图形识别 | 边缘场景，投入产出比低 |
| GarbledTextFilter 作为 P0 | 视觉前置方案已从根源消除乱码问题，仅保留为 P2 安全网 |

---

## 十一、总结

### 11.1 三次迭代对比

| 维度 | 当前代码 | v1（三路并行） | v2（视觉前置） |
|------|------|------|------|
| 执行模式 | 串行 OCR→识图 | 三路并行 | 视觉前置，OCR 按需 |
| 图片处理 | 全量加载到 List | 流式逐张 | 流式逐张 |
| 图片内存 | O(n) 可达 600MB | O(1) 12MB | O(1) 12MB |
| OCR 触发 | 全页 OCR | 策略分类页级 OCR | 视觉模型分类后仅 table/text 触发 |
| 乱码来源 | 图表进 OCR | 图表页进 OCR | **不存在**（图表不进 OCR） |
| 视觉模型角色 | 仅描述 | 仅描述 | **分类 + 描述** |
| OCR 调用量 | 100% 页面 | ~60% 页面 | ~30% 嵌入图 + 仅扫描件页面 |
| 核心新增组件 | — | GarbledTextFilter | ImageClassifier + ContentFusion |

### 11.2 待决策事项

| # | 决策点 | 建议 | 优先级 |
|---|--------|------|:---:|
| D1 | Phase2 单线程流式 vs 4 路并行 | 20MB 限制下极端场景也仅 170s，P0 单线程即可；4 路并行降为 P2 | P2 |
| D2 | 扫描件 PDF 是否跳过嵌入图分支 | Phase0 全扫描件检测，跳过图片处理 | P0 |
| D3 | 连续视觉 API 失败降级 | 连续 3 次失败 → 剩余图片走 OCR 回退 | P0 |
| D4 | GarbledTextFilter 安全网 | P2 保留实现，不阻塞 P0 交付 | P2 |
| D5 | 表格 OCR 结构检测 | 输出中标注"结构可能丢失"，优先用视觉描述数值 | P1 |

### 11.3 一句话总结

> v2 用视觉模型的一次 API 调用同时完成"这是什么类型的图"和"图里有什么"，让 OCR 只处理它擅长的表格和扫描文本，从根源上消灭了乱码问题。额外成本为零（与当前方案 API 调用数一致），内存降低 98%，OCR 调用减少 70%。
