# PDF / 图片 OCR 堆内存溢出修复记录

> 时间：2026-07-23  
> 问题1：PDF 强制 OCR 模式下 java.lang.OutOfMemoryError: Java heap space  
> 问题2：图片 OCR 识别再次触发 OOM  
> 触发文件：2.7MB / 3页 PDF（含 3 张风景照 + 描述文本）、高分辨率图片

---

## 1. 问题现象

```
14:32:36  开始处理 PDF（强制OCR模式）：sample-a4.pdf
14:33:02  OCR 完成（3页，1654字符）
14:34:37  批量爆发 OOM 错误
14:34:46  HikariCP 连接池报告线程饥饿（时钟跳跃1分26秒）
```

影响线程：http-nio-8080-Acceptor/Poller → 服务完全不可用。

## 2. 根因分析

### 2.1 PDDocument 重复加载（P0）

`parse()` 已从 bytes 加载 PDDocument，而 `doOcr()` 内部又调用 `Loader.loadPDF(pdfBytes)` 再次加载。

两份 PDDocument 同时在堆中，每份都将嵌入图片解压到内存（~72MB × 2 = ~144MB 浪费）。

### 2.2 DPI 无必要过高（P1）

OCR 渲染使用配置的 300 DPI，单页 BufferedImage ~34MB（A4）。Tesseract 对 200~300 DPI 不敏感，200 DPI 即可获得相同识别率，内存减半。

### 2.3 并行 OCR 线程数过多（P1）

原有固定 4 线程并行 OCR，4 页 × 34MB = ~136MB 仅渲染图，加上预处理中间对象可达 ~200MB+。

### 2.4 强制 OCR 跳过了图片视觉识别（P1）

`forceOcrFlag` 为 true 时直接调用 `doOcrWithCache()`，完全跳过了 `extractAndRecognizeImages()`（提取嵌入图片 → 视觉 API 识别）。对于含风景照的 PDF，Tesseract 无法识别图片内容，而 Gemini 视觉 API 本应能描述图片。

### 2.5 OCR 缓存命中脏数据（P2）

用户多次上传同一 PDF 测试，SHA-256 缓存命中了首次 Tesseract 对风景照 OCR 的乱码结果（1654 字符），导致重新处理时跳过了 OCR，且乱码文本经 ChunkingService 分块后全部被过滤，分块数为 0。

## 3. 修复内容

### 3.1 entrypoint.sh — JVM 堆参数

```diff
- java -Duser.dir=/app -jar /app/app.jar
+ java -Xms512m -Xmx1024m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -Duser.dir=/app -jar /app/app.jar
```

### 3.2 docker-compose.yml — 容器内存限制

```yaml
mem_limit: 1536m
mem_reservation: 512m
```

### 3.3 PdfParser — 消除 PDDocument 重复加载

`doOcr()` / `doOcrParallel()` 改为接收 `PDDocument doc` 参数，复用 `parse()` 已加载的实例。

### 3.4 PdfParser — OCR 渲染 DPI 降至 200

```java
private static final int OCR_RENDER_DPI = 200;  // 300 → 200，内存减半
```

### 3.5 PdfParser — 并行线程数限制为 2

```java
int threads = Math.min(2, Runtime.getRuntime().availableProcessors());
```

### 3.6 PdfParser — 强制 OCR 模式下仍识别嵌入图片

```java
// 修复后：OCR + 视觉 API 双路
String text = doOcrWithCache(bytes, doc, true);      // Tesseract OCR
String imageDesc = extractAndRecognizeImages(...);     // Gemini 视觉 API
text = text + "\n\n" + imageDesc;                     // 合并
```

### 3.7 PdfParser — 强制 OCR 跳过缓存

```java
doOcrWithCache(bytes, doc, forceOcr=true) → 跳过缓存，重新 OCR
```

### 3.8 ImagePreprocessor — 显式释放中间对象

每个管线步骤后增加 `= null`，帮助 GC 更早回收。

### 3.9 日志追踪

- `[MEM]` 标签：关键节点打印堆内存 used/total/max
- `[PDF]` / `[OCR]` / `[图片]` 标签：分阶段耗时和字节数
- `application.properties`：`com.example.aichat.service.parser=DEBUG`、`.ocr=DEBUG`、`.KnowledgeBaseService=DEBUG`

### 3.10 ImageParser — 全面内存保护（二次 OOM 修复）

| 措施 | 效果 |
|---|---|
| 100M 像素上限 | 超过直接拒绝，防止超大规模图片 OOM |
| 3000px 降采样 | 4000×6000 → 3000×4500，内存从 96MB → 54MB |
| OCR 缓存集成 | 重复上传同一图片跳过 OCR，直接返回缓存 |
| 内存追踪日志 | 加载前/后、OCR 前/后打印 used/max |
| `System.gc()` | OCR 完成后主动提示 GC |
| BufferedImage flush | 原始图、缩放图、预处理图用完即释放 |

### 3.11 全局防御性清理（GC 回收）

| 模块 | 措施 |
|---|---|
| `KnowledgeBaseService.processDocument` | 解析后 `text=null` → 分块后 `chunks=null` → 索引后 `dataList=null`，每步 `System.gc()` |
| `ChromaDBService.addChunks` | 写入 ChromaDB 后 `allEmbeddings/texts/ids/metadatas=null` + `System.gc()` |
| `TxtParser` / `HtmlParser` | 5MB 文本上限，超出自动截断 + warn 日志 |
| `DocxParser` / `PptxParser` | 5MB 文本上限，超出自动截断 + 解析日志 |
| `ExcelParser` | 10,000 行上限 + 5MB 文本上限，双重保护 |

## 4. 预期效果

| 指标 | 修复前 | 修复后 |
|---|---|---|
| PDDocument 内存 | ×2 重复 ≈ 144MB | ×1 ≈ 72MB |
| 单页渲染内存 | ~34MB (300DPI) | ~15MB (200DPI) |
| 并行 OCR 峰值 | 4页 × 34MB ≈ 136MB | 2页 × 15MB ≈ 30MB |
| JVM 堆上限 | 默认(不确定) | 明确 1024MB |
| 图片识别 | 强制模式跳过 | 强制模式也执行 |
| OCR 缓存 | 命中脏数据 | 强制模式跳过 |
| 图片 OCR | 无保护，4000×6000≈96MB | 3000px 降采样，上限 100M 像素 |
| 图片 OCR 缓存 | 无 | SHA-256 缓存 + OcrCacheService |

## 5. 涉及文件

- `entrypoint.sh`
- `docker-compose.yml`
- `src/main/java/.../service/parser/PdfParser.java`
- `src/main/java/.../service/parser/ImageParser.java`
- `src/main/java/.../service/parser/TxtParser.java`
- `src/main/java/.../service/parser/HtmlParser.java`
- `src/main/java/.../service/parser/DocxParser.java`
- `src/main/java/.../service/parser/PptxParser.java`
- `src/main/java/.../service/parser/ExcelParser.java`
- `src/main/java/.../service/ocr/ImagePreprocessor.java`
- `src/main/java/.../service/KnowledgeBaseService.java`
- `src/main/java/.../service/ChromaDBService.java`
- `src/main/java/.../config/CacheConfig.java`
- `src/main/resources/application.properties`
