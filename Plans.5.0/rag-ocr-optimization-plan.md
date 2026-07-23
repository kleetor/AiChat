# HanaChat 知识库 OCR 优化方案

> 创建日期：2026-07-23
> 基于 rag-knowledge-base-analysis-v2.md 中 OCR 部分分析
> 共 18 项方案 → 实践审视后保留 12 项（3 项简化、6 项删除）

---

## 零、实践审视与取舍

### 0.1 知识库在项目中的实际使用方式

分析整个检索管线后，有几个关键事实决定了优化方向：

**事实一：检索结果仅注入 5 个分块作为 System Prompt**

`MessageContextBuilder` (L161-196) 将 topK=5 的检索结果拼接到一条 system 消息中：`"以下是与用户相关的知识库内容，请基于这些内容回答..."`。LLM 基于这 5 个文本片段生成回答。

这意味着：**LLM 是最终的"纠错器"**。即使 OCR 有 20% 的误识率，LLM 通常也能正确理解并回答。OCR 精度从 85% 提升到 95% 对最终回答质量的影响远小于检索召回率。

**事实二：前端无来源引用展示**

聊天界面仅显示 LLM 的回答文本，不展示检索了哪些分块、来自哪些文档。用户无法直接感知 OCR 质量——他们只看到最终回答是否准确。

**事实三：文档处理是异步的，用户等待状态轮询**

上传 → PROCESSING(4s 轮询) → READY/ERROR。用户期望文档尽快可用。处理速度直接影响使用体验。

**事实四：典型使用场景是短文本文档**

用户上传的文档以报告、手册、笔记为主，通常 5-30 页。100 页以上的大文档是少数。页级并行和内存优化的收益被稀释。

### 0.2 取舍原则

| 原则 | 说明 |
|------|------|
| **用户可见价值优先** | 能直接改善用户体验的 > 技术指标提升的 |
| **低成本高收益优先** | 纯代码改动（零 API 费用）> 需要持续 API 调用 |
| **解决痛点优先** | 修复"完全无法使用"的场景 > 优化"不够好"的场景 |
| **LLM 容错前提** | 承认 LLM 本身就是最好的 OCR 后纠错器 |

### 0.3 取舍决策

| 方案 | 决策 | 理由 |
|------|:---:|------|
| P0-1 图片格式支持 | **保留 (P0)** | 用户确实会上传截图/照片，当前完全无法处理 |
| P0-2 改进 PDFBox 判断 | **保留 (P0)** | 扫描件静默失败是真实痛点 |
| P0-3 强制 OCR 模式 | **保留 (P0)** | 零成本，用户自行判断是否需要强制 OCR |
| P1-4 图像预处理 | **保留 (P1)** | 纯代码改动，提升 Tesseract 识别率最有效的手段 |
| P1-5 OCR 后处理 | **简化保留 (P2)** | 去掉字典纠错（LLM 做得更好），仅保留空格/段落清理 |
| P1-6 Tesseract 复用 | **保留 (P1)** | 一行 Bean 注册，消除每次 new 的 IO 开销 |
| P1-7 页级并行 | **降级保留 (P2)** | 收益集中在长文档，而长文档是少数场景 |
| VISION-1 VL 低质量回退 | **删除** | API 费用高 + LLM 本身能纠错 + Tesseract 预处理后已够用 |
| VISION-2 图表视觉增强 | **删除** | 边缘场景，大多数 KB 文档是纯文本，投入产出比低 |
| VISION-3 VL 后纠错 | **删除** | LLM 在回答时已具备上下文纠错能力，额外 VL 调用多余 |
| VISION-4 封面元数据 | **删除** | 用户已知自己上传了什么文档，元数据对检索无帮助 |
| P2-8 内存优化 | **简化保留 (P2)** | 仅做 DPI 250 + 灰度转换（P1-4 已含），不做复杂并发控制 |
| P2-9 语言按需加载 | **删除** | chi_sim+eng 覆盖 99% 场景，~15MB 内存差异可忽略 |
| P2-10 异常传递 | **保留 (P2)** | ERROR 状态 + 错误信息对用户排查有用 |
| P2-11 OCR 缓存 | **保留 (P2)** | 重索引场景直接受益，纯本地文件无额外成本 |
| P2-12 表格提取 | **保留 (P2)** | tabula-java 零 API 成本，对表格类文档有明显提升 |
| P2-13 质量评估 | **删除** | 仅有技术指标意义，VISION 方案已删除则无需此依赖 |

### 0.4 精简后的方案清单

```
P0（必做 - 3项）：解决"完全用不了"的问题
  P0-1  图片格式支持      ─── ImageParser + 白名单扩展 + 前端 accept 扩展
  P0-2  改进判断逻辑       ─── isTextValid() 替换 isBlank()
  P0-3  强制 OCR 模式      ─── 上传时 checkbox [✓] 强制OCR（扫描件）

P1（应做 - 3项）：低成本的显著改善
  P1-4  图像预处理       ─── 灰度→二值化→降噪→倾斜校正（纯代码）
  P1-6  Tesseract 复用   ─── Spring Bean 单例（一行配置）
  P1-5  后处理（简化版） ─── 仅空格/段落清理，去掉字典纠错

P2（可做 - 6项）：锦上添花的优化
  P2-8  内存优化（简化版）─── DPI 250 + 灰度渲染
  P2-10 异常传递         ─── OcrFailedException → 前端 statusBadge 已有错误显示
  P2-11 OCR 缓存         ─── SHA-256 本地文件缓存
  P2-12 表格提取         ─── tabula-java
  P1-7  页级并行（降级） ─── 长文档才启用
```

### 前端 UI 改动总览

| 方案 | 前端改动 | 组件 |
|------|---------|------|
| P0-1 图片格式 | `accept` 属性扩展 + 提示文字 | `KBModal.tsx` L179 |
| P0-3 强制 OCR | PDF 选择确认栏 + forceOcr checkbox | `KBModal.tsx` 上传区 |
| P2-10 异常传递 | 已有 `statusBadge(ERROR)` + `errorMsg` 展示（无需改） | `KBModal.tsx` L166 |
| P1-4~P2-12 | 均为后端/系统级改动，前端无需变更 | — |

---

## 目录

- [零、实践审视与取舍](#零实践审视与取舍)
- [P0-1 支持图片格式上传与 OCR](#p0-1-支持图片格式上传与-ocr)
- [P0-2 改进 PDFBox 提取结果判断逻辑](#p0-2-改进-pdfbox-提取结果判断逻辑)
- [P0-3 新增强制 OCR 模式](#p0-3-新增强制-ocr-模式)
- [P1-4 增加图像预处理管线](#p1-4-增加图像预处理管线)
- [P1-5 OCR 结果后处理（简化版）](#p1-5-ocr-结果后处理简化版)
- [P1-6 Tesseract 实例复用](#p1-6-tesseract-实例复用)
- [P1-7 页级并行处理（降级至 P2）](#p1-7-页级并行处理降级至-p2)
- [P2-8 内存峰值优化（简化版）](#p2-8-内存峰值优化简化版)
- [P2-10 OCR 失败信息显式传递](#p2-10-ocr-失败信息显式传递)
- [P2-11 OCR 结果缓存](#p2-11-ocr-结果缓存)
- [P2-12 PDF 表格提取实现](#p2-12-pdf-表格提取实现)

---

## 附录：已删除方案及原因

| 方案 | 删除原因 |
|------|---------|
| VISION-1 VL 回退 | API 费用高；Tesseract + 预处理已覆盖大部分扫描件；LLM 本身能纠正 OCR 错误 |
| VISION-2 图表增强 | 边缘场景，知识库文档 95%+ 为纯文本 |
| VISION-3 VL 纠错 | LLM 回答时已具备上下文纠错能力，额外 VL 调用是重复劳动 |
| VISION-4 元数据提取 | 用户已知上传了什么文档，元数据不能提升检索质量 |
| P2-9 语言按需加载 | chi_sim+eng 覆盖所有场景，15MB 内存节省无实际意义 |
| P2-13 质量评估 | 仅为 VISION 方案提供触发条件，VISION 方案已删除则无存在必要 |

---

## P0-1 支持图片格式上传与 OCR

### 现状

文件白名单仅支持 `pdf / docx / xlsx / pptx / html / htm / txt / md / csv`，图片格式（jpg / png / tiff / bmp）无法上传。扫描件常以图片形式存在，完全无法处理。

### 方案

#### 1.1 文件白名单扩展

`KnowledgeBaseService.java` 上传校验处，白名单新增：

```
jpg, jpeg, png, tiff, tif, bmp
```

#### 1.2 新增 ImageParser

新建 `service/parser/ImageParser.java`：

```java
@Component
public class ImageParser implements DocumentParser {

    private final Tesseract tesseract; // 复用 Tesseract Bean（P1-6）

    @Override
    public String parse(Path filePath) throws IOException {
        BufferedImage image = ImageIO.read(filePath.toFile());
        // 可选：走 P1-4 预处理管线
        try {
            return tesseract.doOCR(image);
        } catch (TesseractException e) {
            throw new OcrFailedException("图片 OCR 识别失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean supports(String fileType) {
        return Set.of("jpg", "jpeg", "png", "tiff", "tif", "bmp").contains(fileType);
    }
}
```

#### 1.3 文件元数据

- `KbDocument.file_type` 存储原始扩展名（变小写）
- PNG 透明通道在预处理中转为白色背景

### 涉及文件

| 文件 | 改动 |
|------|------|
| `service/KnowledgeBaseService.java` | 白名单扩展 |
| `service/parser/ImageParser.java` | **新建** |
| `model/KbDocument.java` | 无需改，file_type VARCHAR 已有 |

#### 前端 UI

`KBModal.tsx` 上传区域的 `accept` 属性扩展图片格式：

```tsx
// L179 — accept 属性新增加图片格式
<input type="file"
  accept=".txt,.md,.pdf,.docx,.xlsx,.pptx,.html,.htm,.csv,.jpg,.jpeg,.png,.tiff,.tif,.bmp"
  className="hidden" onChange={handleUpload} />
```

上传标签文字同步更新：`上传文档 (TXT / MD / PDF / DOCX / XLSX / PPTX / HTML / CSV / 图片)`

---

## P0-2 改进 PDFBox 提取结果判断逻辑

### 现状

```java
// PdfParser.java L46
if (isBlank(text) && ocrProps.isEnabled()) {
```

PDFBox 可能提取出少量噪音字符（乱码、空格），导致 `isBlank()` 返回 false 而跳过 OCR。

### 方案

替换简单的 `isBlank()` 为有效性检查：

```java
/**
 * 判断提取文本是否有效（非空且有足够的中文/英文/数字字符）。
 * 阈值：有效字符 >= 10 且有效比例 >= 30%
 */
private boolean isTextValid(String text) {
    if (text == null || text.isBlank()) return false;

    // 统计 CJK + 字母 + 数字 字符数
    long validChars = text.codePoints()
            .filter(cp -> Character.isLetterOrDigit(cp)
                    || Character.isIdeographic(cp))
            .count();

    long totalNonSpace = text.codePoints()
            .filter(cp -> !Character.isWhitespace(cp))
            .count();

    // 有效字符 >= 10 且占比 >= 30%
    return validChars >= 10
            && (totalNonSpace == 0 || (double) validChars / totalNonSpace >= 0.3);
}
```

调用处：

```java
// 替换 L46
if (!isTextValid(text) && ocrProps.isEnabled()) {
```

### 涉及文件

| 文件 | 改动 |
|------|------|
| `service/parser/PdfParser.java` | 新增 `isTextValid()`，修改判断逻辑 |

---

## P0-3 新增强制 OCR 模式

### 现状

无此功能。部分场景需要跳过 PDFBox 直接走 OCR（如文字层有错位、文字层与扫描层不对齐）。

### 方案

#### 3.1 改为上传级参数（非全局开关）

原方案为 `application.properties` 全局开关，实用性差——用户需要在上传时针对特定文档开启。改为上传 API 接收 `forceOcr` 参数。

#### 3.2 后端：上传接口接收 forceOcr

`KnowledgeBaseController.uploadDocument()` 新增 `@RequestParam`：

```java
@PostMapping("/{kbId}/docs/upload")
public ResponseEntity<?> uploadDocument(
        @PathVariable Long kbId,
        @RequestParam("file") MultipartFile file,
        @RequestParam(value = "forceOcr", defaultValue = "false") boolean forceOcr,
        Authentication auth) {
    // ...
}
```

`KnowledgeBaseService.uploadDocument()` 方法签名新增参数，存入临时上下文供异步处理使用：

```java
// ConcurrentHashMap 存放上传级 OCR 配置，处理完毕后清除
private final ConcurrentHashMap<Long, Boolean> forceOcrFlags = new ConcurrentHashMap<>();

public KbDocument uploadDocument(Long kbId, MultipartFile file, Long userId, boolean forceOcr) {
    // ... 上传逻辑 ...
    forceOcrFlags.put(doc.getId(), forceOcr);
    return doc;
}
```

`processDocument()` 中解析时读取标志：

```java
Boolean forceOcr = forceOcrFlags.remove(docId);
String text = parser.parse(filePath, forceOcr); // PdfParser 接收此参数
```

#### 3.3 PdfParser 适配

```java
public String parse(Path filePath) throws IOException {
    return parse(filePath, false);
}

public String parse(Path filePath, boolean forceOcr) throws IOException {
    // ... 加载 PDF ...
    if (forceOcr && ocrProps.isEnabled()) {
        return ocrWithTimeout(bytes, doc);
    }
    // 原有逻辑不变
}
```

### 前端 UI

在 `KBModal.tsx` 上传区域增加"强制 OCR"复选框，**仅在选择 PDF 文件时显示**：

```tsx
// 新增 state
const [forceOcr, setForceOcr] = useState(false);
const [pendingFile, setPendingFile] = useState<File | null>(null);

// 文件选择时判断是否为 PDF
const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
  const file = e.target.files?.[0];
  if (!file) return;
  setPendingFile(file);
  // 仅 PDF 显示强制 OCR 选项
  if (!file.name.toLowerCase().endsWith('.pdf')) {
    doUpload(file, false);
    e.target.value = "";
  }
};

// 确认上传
const doUpload = async (file: File, forceOcr: boolean) => {
  if (!currentKb) return;
  try {
    await uploadKBDocument(currentKb.id, file, forceOcr);
    loadDocs(currentKb.id);
  } catch { /* ignore */ }
  setPendingFile(null);
  setForceOcr(false);
};
```

上传区域 UI 布局：

```tsx
<div className="p-4 border-t border-border shrink-0 space-y-2">
  {/* 强制 OCR 确认栏 — 仅选择 PDF 时显示 */}
  {pendingFile && (
    <div className="flex items-center justify-between px-3 py-2 rounded-lg bg-amber-50 border border-amber-200 text-xs">
      <span className="text-amber-800">
        已选择 <strong>{pendingFile.name}</strong>
      </span>
      <div className="flex items-center gap-3">
        <label className="flex items-center gap-1.5 cursor-pointer">
          <input type="checkbox" checked={forceOcr} onChange={e => setForceOcr(e.target.checked)}
            className="w-3.5 h-3.5 rounded accent-amber-600" />
          <span className="text-amber-700">强制 OCR 识别（扫描件）</span>
        </label>
        <button onClick={() => doUpload(pendingFile, forceOcr)}
          className="px-3 py-1 rounded-md bg-amber-600 text-white text-xs font-medium">
          确认上传
        </button>
        <button onClick={() => setPendingFile(null)}
          className="px-2 py-1 text-amber-600 hover:text-amber-800">
          取消
        </button>
      </div>
    </div>
  )}
  {/* 原有上传按钮 — 选择非 PDF 时直接上传 */}
  {!pendingFile && (
    <label className="w-full flex items-center justify-center gap-2 py-2 rounded-lg border border-dashed border-border text-xs text-muted-foreground cursor-pointer hover:border-primary/50 hover:text-primary transition-colors">
      <Upload size={14} />上传文档 (TXT / MD / PDF / DOCX / XLSX / PPTX / HTML / CSV / 图片)
      <input type="file" accept="..." className="hidden" onChange={handleFileSelect} />
    </label>
  )}
</div>
```

#### 交互流程

```
选择文件 → 是 PDF？
              ├─ 否 → 直接上传
              └─ 是 → 显示确认栏，列出文件名 + [✓] 强制OCR（扫描件）+ [确认上传] [取消]
                         └─ 确认 → uploadKBDocument(kbId, file, forceOcr)
                         └─ 取消 → 清除选择，恢复上传按钮
```

### services.ts 更新

```typescript
export function uploadKBDocument(kbId: number, file: File, forceOcr?: boolean): Promise<KbDocument> {
  const fd = new FormData();
  fd.append("file", file);
  if (forceOcr) fd.append("forceOcr", "true");
  return apiPostForm(`/api/kb/${kbId}/docs/upload`, fd);
}
```

### 涉及文件

| 文件 | 改动 |
|------|------|
| `config/props/OcrProperties.java` | 无需改（删去 forceOcr 字段，改用请求级参数） |
| `service/parser/PdfParser.java` | 新增 `parse(Path, boolean)` 重载 |
| `service/KnowledgeBaseService.java` | `uploadDocument()` 新增 forceOcr 参数 + ConcurrentHashMap |
| `controller/KnowledgeBaseController.java` | 上传接口新增 `@RequestParam forceOcr` |
| `frontend/src/lib/services.ts` | `uploadKBDocument()` 新增 forceOcr 参数 |
| `frontend/src/components/modals/KBModal.tsx` | 新增 PDF 选择确认栏 + forceOcr checkbox |

---

## P1-4 增加图像预处理管线

### 现状

PDF 页面直接渲染为 `BufferedImage` 送入 Tesseract，无任何预处理。扫描件质量参差不齐时识别率大幅下降。

### 方案

新建 `service/ocr/ImagePreprocessor.java`，实现可插拔的预处理管线：

```java
@Component
public class ImagePreprocessor {

    /**
     * 预处理管线（按顺序执行）：
     *   1. 转灰度
     *   2. 自适应二值化（Otsu）
     *   3. 降噪（中值滤波 3x3）
     *   4. 倾斜检测与校正
     */
    public BufferedImage preprocess(BufferedImage src) {
        // 如果已是灰度图，跳过第1步
        BufferedImage gray = toGrayscale(src);
        BufferedImage binary = adaptiveThreshold(gray);
        BufferedImage denoised = medianFilter(binary, 3);
        return deskew(denoised);
    }

    // --- 各步骤 ---

    /** 转灰度图：TYPE_BYTE_GRAY，内存降低 75% */
    private BufferedImage toGrayscale(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_BYTE_GRAY) return src;
        BufferedImage gray = new BufferedImage(
                src.getWidth(), src.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = gray.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        src.flush(); // 释放原图
        return gray;
    }

    /** Otsu 自适应二值化 */
    private BufferedImage adaptiveThreshold(BufferedImage gray) {
        // 计算 Otsu 阈值
        int threshold = computeOtsuThreshold(gray);
        // 应用二值化
        BufferedImage binary = new BufferedImage(
                gray.getWidth(), gray.getHeight(), BufferedImage.TYPE_BYTE_BINARY);
        // ... 逐像素应用 threshold
        return binary;
    }

    /** 3x3 中值滤波降噪 */
    private BufferedImage medianFilter(BufferedImage src, int kernelSize) {
        // 标准中值滤波实现
        // ...
    }

    /** 倾斜校正：霍夫变换检测倾斜角 → 旋转 */
    private BufferedImage deskew(BufferedImage src) {
        double angle = detectSkewAngle(src);
        if (Math.abs(angle) < 1.0) return src; // 倾斜 < 1° 不处理
        return rotate(src, -angle);
    }
}
```

#### 使用方式

`PdfParser.doOcr()` 和 `ImageParser.parse()` 中统一注入 `ImagePreprocessor`：

```java
BufferedImage raw = renderer.renderImageWithDPI(i, ocrProps.getDpi());
BufferedImage processed = preprocessor.preprocess(raw);
String pageText = tesseract.doOCR(processed);
```

#### 配置控制

各步骤可单独开关：

```properties
ocr.preprocess.grayscale=true
ocr.preprocess.binarize=true
ocr.preprocess.denoise=true
ocr.preprocess.deskew=true
```

### 依赖

无需额外 Maven 依赖，纯 JDK `BufferedImage` 实现。

### 涉及文件

| 文件 | 改动 |
|------|------|
| `service/ocr/ImagePreprocessor.java` | **新建** |
| `config/props/OcrProperties.java` | 新增 `preprocess` 嵌套配置 |
| `service/parser/PdfParser.java` | 注入 `ImagePreprocessor` |
| `service/parser/ImageParser.java` | 注入 `ImagePreprocessor` |

---

## P1-5 OCR 结果后处理（简化版）

### 现状

OCR 原始输出直接入库，存在多余空格、硬换行打断段落等问题。

### 精简说明

**原方案包含**：空格规范化 + 段落合并 + 字典纠错（如 "已"→"己"）。

**删除字典纠错**：LLM 在回答时具备极强的上下文纠错能力。形近字误识别（"已"/"己"）在 sentence-level context 中 LLM 能自动纠正，固定字典纠错反而可能引入新错误（如正确用法的"己"被误改为"已"）。

**保留**：空格规范化和段落合并，这两项是结构性修复，且零成本。

### 方案

新建 `service/ocr/OcrPostProcessor.java`：

```java
@Component
public class OcrPostProcessor {

    public String postProcess(String rawText) {
        String result = normalizeSpaces(rawText);
        result = mergeParagraphs(result);
        return result.trim();
    }

    /** 中文间去空格，中英文/数字间加半角空格 */
    private String normalizeSpaces(String text) {
        text = text.replaceAll("([\\u4e00-\\u9fff])([a-zA-Z])", "$1 $2");
        text = text.replaceAll("([a-zA-Z])([\\u4e00-\\u9fff])", "$1 $2");
        text = text.replaceAll("([\\u4e00-\\u9fff])\\s+([\\u4e00-\\u9fff])", "$1$2");
        text = text.replaceAll(" {2,}", " ");
        return text;
    }

    /** OCR 产出的单行换行合并为段落 */
    private String mergeParagraphs(String text) {
        return text.replaceAll("(?<![。！？.!?])\\n", "")
                   .replaceAll("\\n{3,}", "\n\n");
    }
}
```

### 涉及文件

| 文件 | 改动 |
|------|------|
| `service/ocr/OcrPostProcessor.java` | **新建** |
| `service/parser/PdfParser.java` | 注入，`doOcr()` 返回前调用 |
| `service/parser/ImageParser.java` | 同上 |

---

## P1-6 Tesseract 实例复用

### 现状

```java
// PdfParser.java L107 — 每次 doOcr 都 new Tesseract()
Tesseract tesseract = new Tesseract();
```

`new Tesseract()` 会加载语言模型文件（chi_sim ~30MB），每次创建有 IO 开销。

### 方案

新建 `config/OcrConfig.java`，将 Tesseract 注册为 Spring Bean（单例）：

```java
@Configuration
public class OcrConfig {

    @Bean
    public Tesseract tesseract(OcrProperties props) {
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath(props.getTessdataPath());
        tesseract.setLanguage(props.getLanguage());
        tesseract.setVariable("user_defined_dpi", String.valueOf(props.getDpi()));
        return tesseract;
    }
}
```

修改 `PdfParser.java`：

```java
// 注入 Bean，删除 new Tesseract() 代码
private final Tesseract tesseract;

public PdfParser(OcrProperties ocrProps, Tesseract tesseract) {
    this.ocrProps = ocrProps;
    this.tesseract = tesseract;
}

// doOcr() 中直接使用 this.tesseract
```

### 线程安全说明

Tesseract 的 `doOCR()` 方法是线程安全的（Tess4J 内部使用 `synchronized` 保护原生调用），因此单例 Bean 可安全地在多线程中使用。

### 涉及文件

| 文件 | 改动 |
|------|------|
| `config/OcrConfig.java` | **新建** |
| `service/parser/PdfParser.java` | 注入 `Tesseract` Bean |
| `service/parser/ImageParser.java` | 注入 `Tesseract` Bean |

---

## P1-7 页级并行处理

### 现状

```java
// PdfParser.java L112-120 — 逐页 for 循环，单线程
for (int i = 0; i < totalPages; i++) {
    BufferedImage image = renderer.renderImageWithDPI(i, ocrProps.getDpi());
    String pageText = tesseract.doOCR(image);
    sb.append(pageText).append("\n");
}
```

100 页 PDF 完全串行，即使有 2 线程的 `ocrExecutor` 也未利用。

### 方案

改为页级并行，使用 `CompletableFuture` + 有序聚合：

```java
private String doOcrParallel(byte[] pdfBytes, int totalPages) {
    try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
        PDFRenderer renderer = new PDFRenderer(doc);

        // 分批控制并发数（每批 maxConcurrency 页）
        int maxConcurrency = ocrProps.getMaxConcurrency(); // 默认 4
        List<CompletableFuture<IndexedPage>> futures = new ArrayList<>();

        for (int i = 0; i < totalPages; i++) {
            final int pageIdx = i;
            CompletableFuture<IndexedPage> future = CompletableFuture.supplyAsync(() -> {
                BufferedImage raw = renderer.renderImageWithDPI(pageIdx, ocrProps.getDpi());
                BufferedImage processed = preprocessor.preprocess(raw);
                String text;
                try {
                    text = tesseract.doOCR(processed);
                } catch (TesseractException e) {
                    text = "";
                } finally {
                    processed.flush();
                }
                return new IndexedPage(pageIdx, text);
            }, ocrExecutor);
            futures.add(future);
        }

        // 聚合：按页码排序
        String[] pages = new String[totalPages];
        for (CompletableFuture<IndexedPage> f : futures) {
            IndexedPage page = f.get(ocrProps.getTimeoutSeconds(), TimeUnit.SECONDS);
            pages[page.index] = page.text;
        }

        return String.join("\n", pages);
    }
}

private record IndexedPage(int index, String text) {}
```

配置新增：

```properties
ocr.max-concurrency=4
```

### 涉及文件

| 文件 | 改动 |
|------|------|
| `config/props/OcrProperties.java` | 新增 `maxConcurrency` 字段 |
| `service/parser/PdfParser.java` | 重构 `doOcr()` 为并行版本 |

---

## P2-8 内存峰值优化

### 现状

300 DPI A4 页面渲染为 `BufferedImage.TYPE_INT_ARGB` 约 33MB/页（2480×3508×4），100 页峰值可达 3.3GB。

### 方案

#### 8.1 渲染类型改为灰度

`PDFRenderer.renderImageWithDPI()` 默认返回 `TYPE_INT_ARGB`（4 字节/像素），改为 `TYPE_BYTE_GRAY`（1 字节/像素），单页从 33MB 降至 8.3MB。

可通过 `PDFRenderer.renderImage()` 的 `ImageType` 参数控制：

```java
// 灰度渲染
BufferedImage raw = renderer.renderImage(pageIdx, 2.0f, ImageType.GRAY);
```

但 DPI 精度控制用 `renderImageWithDPI` 更方便。替代方案：渲染后用 `ImagePreprocessor.toGrayscale()` 立即转灰度 + flush 原图。

#### 8.2 DPI 降为 250

300 DPI vs 250 DPI 对中文识别率几乎无差异，但像素量降低 30%：

| DPI | 像素 (A4) | 内存 (灰度) |
|-----|----------|------------|
| 300 | 2480×3508 | 8.3 MB     |
| 250 | 2067×2923 | 5.8 MB     |

```properties
ocr.dpi=250
```

#### 8.3 分批渲染+识别，控制并发数

不一次性提交所有页面，用 Semaphore 或分批次控制同时在处理的页面数等于 `maxConcurrency`（默认 4），确保峰值内存 = 4 × 8.3MB ≈ 33MB。

### 涉及文件

| 文件 | 改动 |
|------|------|
| `config/props/OcrProperties.java` | DPI 默认值改为 250 |
| `service/parser/PdfParser.java` | `doOcrParallel()` 中灰度渲染 |
| Docker `.env` / `Dockerfile` | 无改动 |

---

## P2-10 OCR 失败信息显式传递

### 现状

```java
// PdfParser.java L123-125
} catch (IOException | TesseractException e) {
    log.error("OCR 识别失败", e);
    return "";
}
```

上层无法区分"PDFBox 提取为空 + OCR 也失败"和"PDFBox 提取为空 + OCR 跳过"，UI 无法给用户有意义的提示。

### 方案

#### 10.1 新建异常类

```java
package com.example.aichat.exception;

public class OcrFailedException extends RuntimeException {
    public OcrFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

#### 10.2 修改 PdfParser

```java
private String ocrWithTimeout(byte[] pdfBytes, PDDocument doc) throws IOException {
    // ...
    Future<String> future = ocrExecutor.submit(() -> doOcr(pdfBytes, pages));
    try {
        String result = future.get(ocrProps.getTimeoutSeconds(), TimeUnit.SECONDS);
        if (result.isEmpty()) {
            throw new OcrFailedException("OCR 返回空结果", null);
        }
        return result;
    } catch (TimeoutException e) {
        future.cancel(true);
        throw new OcrFailedException(
                "OCR 超时 (" + ocrProps.getTimeoutSeconds() + "s)，页数=" + pages, e);
    } catch (ExecutionException e) {
        throw new OcrFailedException("OCR 执行异常: " + e.getCause().getMessage(), e.getCause());
    }
}
```

#### 10.3 上层处理

`KnowledgeBaseService.processDocument()` 中捕获 `OcrFailedException`，写入 `KbDocument.error_msg` 并设置 `status=ERROR`。

### 涉及文件

| 文件 | 改动 |
|------|------|
| `exception/OcrFailedException.java` | **新建** |
| `service/parser/PdfParser.java` | 异常传递 |
| `service/KnowledgeBaseService.java` | 捕获 `OcrFailedException` |

---

## P2-11 OCR 结果缓存

### 现状

同一文件重索引时，重新执行完整的 OCR 流程（最耗时环节）。

### 方案

对文件内容计算 SHA-256，以 hash 为 key 缓存 OCR 结果：

```java
@Component
public class OcrCacheService {

    private final Path cacheDir = Path.of("./uploads/kb/ocr_cache");

    @PostConstruct
    void init() throws IOException {
        Files.createDirectories(cacheDir);
    }

    public Optional<String> get(byte[] fileBytes) {
        String hash = sha256(fileBytes);
        Path cacheFile = cacheDir.resolve(hash + ".txt");
        if (Files.exists(cacheFile)) {
            try {
                return Optional.of(Files.readString(cacheFile));
            } catch (IOException e) {
                log.warn("OCR 缓存读取失败: {}", cacheFile, e);
            }
        }
        return Optional.empty();
    }

    public void put(byte[] fileBytes, String ocrResult) {
        String hash = sha256(fileBytes);
        try {
            Files.writeString(cacheDir.resolve(hash + ".txt"), ocrResult);
        } catch (IOException e) {
            log.warn("OCR 缓存写入失败", e);
        }
    }

    private String sha256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(data);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
```

#### 使用

```java
// PdfParser.parse() 中
if (ocrProps.isEnabled()) {
    Optional<String> cached = ocrCacheService.get(bytes);
    if (cached.isPresent()) {
        log.info("命中 OCR 缓存: {}", filePath.getFileName());
        return cached.get();
    }
    String ocrResult = ocrWithTimeout(bytes, doc);
    if (!ocrResult.isBlank()) {
        ocrCacheService.put(bytes, ocrResult);
    }
    return ocrResult;
}
```

### 涉及文件

| 文件 | 改动 |
|------|------|
| `service/ocr/OcrCacheService.java` | **新建** |
| `service/parser/PdfParser.java` | 注入 `OcrCacheService` |
| `service/parser/ImageParser.java` | 同上 |

---

## P2-12 PDF 表格提取实现

### 现状

```java
// PdfParser.java L73-76 — 空实现
private String extractTables(PDDocument doc) throws IOException {
    return "";
}
```

扫描件 PDF 经过 OCR 后，表格内容变成乱序文本流，丢失行列关系。

### 方案

#### 12.1 文字层表格（PDFBox 路径）

引入 `technology.tabula:tabula:1.0.5` 提取文字层 PDF 中的表格：

```xml
<dependency>
    <groupId>technology.tabula</groupId>
    <artifactId>tabula</artifactId>
    <version>1.0.5</version>
</dependency>
```

```java
private String extractTables(PDDocument doc) throws IOException {
    StringBuilder sb = new StringBuilder();
    ObjectExtractor extractor = new ObjectExtractor(doc);
    PageIterator pages = extractor.extract();

    while (pages.hasNext()) {
        Page page = pages.next();
        BasicExtractionAlgorithm bea = new BasicExtractionAlgorithm();

        // 先尝试 Lattice（有线框表格），再回退 Stream
        if (page.getTableAreas().isEmpty()) {
            continue;
        }

        for (Table table : bea.extract(page)) {
            sb.append(toMarkdownTable(table)).append("\n\n");
        }
    }
    return sb.toString();
}

/** Tabula Table → Markdown 表格 */
private String toMarkdownTable(Table table) {
    StringBuilder md = new StringBuilder();
    for (int r = 0; r < table.getRowCount(); r++) {
        md.append("|");
        for (int c = 0; c < table.getColCount(); c++) {
            md.append(table.getCell(r, c).getText().trim()).append("|");
        }
        md.append("\n");
        // 表头分隔线
        if (r == 0) {
            md.append("|");
            for (int c = 0; c < table.getColCount(); c++) {
                md.append("---|");
            }
            md.append("\n");
        }
    }
    return md.toString();
}
```

#### 12.2 扫描件表格（OCR 路径）

扫描件 OCR 后的表格恢复较复杂，需要布局分析。作为 P2 优化，先实现文字层表格提取，扫描件表格识别留到后续版本。

### 涉及文件

| 文件 | 改动 |
|------|------|
| `pom.xml` | 新增 `tabula` 依赖 |
| `service/parser/PdfParser.java` | 实现 `extractTables()` |

---

## 实施优先级总览

```
阶段一（P0 - 必做 - 3项）：解决"完全用不了"的问题
├── P0-1  图片格式支持  ─── ImageParser + 白名单扩展
├── P0-2  改进判断逻辑  ─── isTextValid() 替换 isBlank()
└── P0-3  强制 OCR 模式 ─── forceOcr 配置开关

阶段二（P1 - 应做 - 3项）：低成本的显著改善
├── P1-4  图像预处理    ─── 灰度→二值化→降噪→倾斜校正（纯代码，零 API 费用）
├── P1-6  Tesseract 复用 ─── Spring Bean 单例
└── P1-5  后处理(简化)  ─── 仅空格/段落清理（去掉字典纠错，LLM 已能纠错）

阶段三（P2 - 可做 - 6项）：锦上添花的优化
├── P2-8  内存优化(简化) ─── DPI 250 + 灰度渲染
├── P2-10 异常传递       ─── OcrFailedException
├── P2-11 OCR 缓存       ─── SHA-256 本地文件缓存
├── P2-12 表格提取       ─── tabula-java
└── P1-7  页级并行(降级) ─── 长文档才启用
```

### 关键取舍说明

| 删除的方案 | 理由 |
|-----------|------|
| VISION-1~4 视觉模型协同 | API 费用高；LLM 本身已是最终纠错器；预处理后的 Tesseract 对扫描件已足够 |
| P2-9 语言按需加载 | chi_sim+eng 覆盖 99% 场景，15MB 内存差异可忽略 |
| P2-13 质量评估 | 仅为 VISION 触发条件，VISION 删除后无依赖 |

## 预估改动量

| 阶段 | 新建文件 | 修改文件 | DDL |
|------|---------|---------|-----|
| P0 | 1 (`ImageParser`) | 2 | 无 |
| P1 | 3 (`ImagePreprocessor`, `OcrPostProcessor`, `OcrConfig`) | 2 | 无 |
| P2 | 4 (`OcrCacheService`, `OcrFailedException`, 表格提取为现有 PdfParser 内实现) | 4 | 无 |
| **合计** | **8** | **8** | **0** |
