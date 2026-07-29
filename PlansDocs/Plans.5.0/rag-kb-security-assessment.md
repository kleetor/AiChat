# 知识库文档处理 - 安全体系评估

## 1. 评估范围

- 文档上传 → 解析 → 分块 → 向量/BM25 入库 → 检索 全链路
- 视觉识别 API 新增后的攻击面变化

---

## 2. 现有安全措施

### 2.1 已实现 ✅

| 措施 | 位置 | 说明 |
|------|------|------|
| 认证 | Controller 层 `Authentication auth` | 所有 KB 接口强制登录 |
| 横向越权防护 | Service 层 `kb.getUserId().equals(userId)` | 每个操作校验 KB 归属 |
| 文件大小限制 | Servlet 层 `max-file-size=5MB` | 单文件 5MB |
| 路径穿越防护 | `Paths.get(rawName).getFileName()` | 剥离路径，只取文件名 |
| 上传频率限制 | `RateLimitInterceptor` | 50 次/天/用户 |
| UUID 文件名 | `UUID.randomUUID() + "_" + fileName` | 防止文件覆盖/猜测 |
| 异步处理 | `CompletableFuture.runAsync` | 上传后立即返回，不阻塞 |
| 错误隔离 | processDocument 内 try-catch | 单文档失败不影响其他 |
| 分块上限 | ChunkingService | 递归字符分割有上限 |
| Log 脱敏 | API key 不记录在日志 | 仅记录 API URL |

### 2.2 已有但需注意 ⚠️

| 措施 | 问题 |
|------|------|
| 文件大小 5MB（servlet） | Service 层还检查了 10MB，但 servlet 先拦截，10MB 检查永不到达 |
| 文件类型检测 | 仅通过扩展名判断，未验证 Magic Bytes |

---

## 3. 已识别风险与缺口

### 风险 1：文件类型伪装 🔴 高

**描述**：`getFileType()` 仅检查扩展名，攻击者可上传 `.pdf` 扩展名的任意文件（exe、zip bomb 等），PDFBox 解析时会抛异常进入 OCR 回退，浪费 CPU。

**当前状态**：无 Magic Bytes 校验。

**建议**：
```java
// 读取文件头 Magic Bytes 验证
private static final Map<String, byte[]> MAGIC_BYTES = Map.of(
    "pdf", new byte[]{0x25, 0x50, 0x44, 0x46},  // %PDF
    "docx", new byte[]{0x50, 0x4B, 0x03, 0x04}, // PK..
    "png", new byte[]{(byte)0x89, 0x50, 0x4E, 0x47},
    "jpg", new byte[]{(byte)0xFF, (byte)0xD8, (byte)0xFF}
);
```
**优先级**：P0

### 风险 2：PDF Zip Bomb 🔴 高

**描述**：一个 100KB 的 PDF 经过 PDFBox 解析可膨胀到数 GB 内存，导致 OOM 崩溃。

**当前状态**：仅限制文件大小 5MB，未限制页面数（`MAX_PAGES=100`），未限制图片数。

**缓解**：PDFBox 本身对解压有保护，但需额外限制：
- 已有 `MAX_PAGES=100` 限制
- 新增图片数量限制 `max-images-per-doc=50`

**优先级**：P1

### 风险 3：分块膨胀攻击 🔴 高

**描述**：攻击者上传包含大量重复文字（如复制粘贴 10000 次 "hello"）的文档，分块后产生海量向量数据，撑爆 ChromaDB。

**当前状态**：无单文档分块数量上限。

**建议**：
```java
private static final int MAX_CHUNKS_PER_DOC = 500;
// 在 processDocument 中校验
if (chunks.size() > MAX_CHUNKS_PER_DOC) {
    throw BusinessException.badRequest("文档分块数超过上限");
}
```
**优先级**：P0

### 风险 4：无用户级存储配额 🟡 中

**描述**：用户可在知识库中无限上传文档，累积存储成本。

**当前状态**：仅频率限制 50 次/天，无总量限制。

**建议**：
- 单知识库文档上限（如 200 个）
- 单用户总存储上限（如 500MB）
- 单用户总文档数上限（如 1000 个）

**优先级**：P1

### 风险 5：视觉 API 费用放大 🔴 高（新增功能引入）

**描述**：新增图片视觉识别后，攻击者可上传含有大量图片的 PDF 恶意消耗 API 额度。

**缓解措施（已在计划中）**：
- `max-images-per-doc=50` 限制
- 尺寸过滤（<150px 跳过）
- 哈希去重

**额外建议**：
- 单用户每日视觉 API 调用上限（如 500 张图片/天）
- API 调用计数 + 成本上限熔断

**优先级**：P0

### 风险 6：异步任务无限积压 🟡 中

**描述**：`CompletableFuture.runAsync` 使用 `ForkJoinPool.commonPool()`，无界队列。大量上传可能导致线程池耗尽。

**当前状态**：超时机制存在（OCR 300s），但无任务队列上限。

**建议**：使用有界线程池：
```java
private final ExecutorService processExecutor = 
    new ThreadPoolExecutor(2, 4, 60L, TimeUnit.SECONDS, 
        new LinkedBlockingQueue<>(20), 
        new ThreadPoolExecutor.CallerRunsPolicy());
```
**优先级**：P1

### 风险 7：OCR 缓存投毒 🟡 中

**描述**：`OcrCacheService` 以 SHA-256 为 key 存到本地文件系统。如果攻击者能猜到 key 或获得文件系统访问权，可注入恶意文本。

**当前状态**：缓存目录 `./uploads/kb/ocr_cache`，本地文件系统。

**建议**：
- 限制缓存目录权限
- 添加缓存文件大小校验（不应超过原始文本合理上限）
- 考虑缓存文件签名校验

**优先级**：P2

### 风险 8：XSS 向量注入 🟡 中

**描述**：文档内容被解析后存入向量库，检索时拼入 LLM prompt。恶意文档可包含指令注入文本（prompt injection）。

**当前状态**：无内容过滤。

**建议**：
- 分块时过滤可疑模式（如 `[system]`, `ignore previous`, `DAN` 等已知注入向量）
- 检索结果拼入 prompt 时添加明确边界分隔符
- 不自动执行文档中提取的"指令"

**优先级**：P2

### 风险 9：重索引无频率限制 🟢 低

**描述**：`POST /api/kb/docs/{docId}/reindex` 无频率限制，用户可反复触发消耗资源。

**当前状态**：重索引会先清理再重建，状态设为 PROCESSING，但无防抖/限频。

**建议**：添加 RateLimitRule 或状态检查（PROCESSING 时拒绝重复请求）。

**优先级**：P2

### 风险 10：并发上传文件名冲突 🟢 低

**描述**：UUID 命名已解决冲突问题。但 `UPLOAD_DIR` 使用相对路径 `./uploads/kb`，与运行目录相关，部署时需注意。

**当前状态**：已使用 UUID，低风险。

---

## 4. 风险汇总

| ID | 风险 | 等级 | 新增/已有 | 修复成本 |
|----|------|------|-----------|---------|
| R1 | 文件类型伪装 | 🔴 高 | 已有 | 低（Magic Bytes 校验） |
| R2 | PDF Zip Bomb | 🔴 高 | 已有 | 中（多层限制） |
| R3 | 分块膨胀攻击 | 🔴 高 | 已有 | 低（数量上限） |
| R4 | 用户存储配额 | 🟡 中 | 已有 | 中（DB 统计 + 校验） |
| R5 | API 费用放大 | 🔴 高 | **新增** | 低（配置限制） |
| R6 | 异步任务积压 | 🟡 中 | 已有 | 中（线程池改造） |
| R7 | OCR 缓存投毒 | 🟡 中 | 已有 | 低 |
| R8 | XSS/注入 | 🟡 中 | 已有 | 低（文本过滤） |
| R9 | 重索引频率 | 🟢 低 | 已有 | 低（限频规则） |
| R10 | 文件名冲突 | 🟢 低 | 已有 | 已解决 |

---

## 5. 实施建议

### P0 - 本次应修复
- **R1** 文件类型 Magic Bytes 校验
- **R3** 分块数量上限（500/文档）
- **R5** 视觉 API 调用上限（包含在本次图片识别实现中）

### P1 - 下个迭代
- **R2** PDF 安全解析加固（资源限制）
- **R4** 用户存储配额
- **R6** 异步处理线程池有界化

### P2 - 后续评估
- R7 / R8 / R9
