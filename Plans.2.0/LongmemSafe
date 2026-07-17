LongMemory 方案安全审查 & 可优化分析
=========================================
> 审查日期: 2026-06-20
> 审查对象: Plans.2.0/LongMemory (v3 人类记忆模型)
> 范围: 方案可行性、现有代码适配、潜在风险、优化建议


## 一、严重问题 (4项) — 实施前必须解决

### 1.1 ChatService.chatSync() 不存在

**现状**: 方案核心依赖 `chatService.chatSync()` 用于记忆提取 (extractAndStore) 和阶梯压缩 (compressWithLLM)，但 ChatService 中**没有这个方法**。

现有 ChatService 方法:
- `chatAndSave()` — 同步调用但会保存消息 + 扣费，不应复用
- `chatStream()` — 流式 SSE 返回，不适用
- `callDeepSeekAsyncWithUsage()` — 私有方法
- `validateAndGetConfig()` — 仅校验

**修复方案**:
```java
// 在 ChatService 中新增纯净同步调用（不存消息、不扣费）
public String chatSync(String prompt) {
    // 使用默认/廉价 ModelConfig
    ModelConfig config = modelConfigRepository.findById(defaultMemoryModelId)
        .orElseThrow(() -> new RuntimeException("记忆提取模型未配置"));
    ArrayNode msgs = objectMapper.createArrayNode();
    msgs.addObject()
        .put("role", "user")
        .put("content", prompt);
    TokenUsageResult result = callDeepSeekAsyncWithUsage(msgs, config).join();
    return result.getReply();
}
```

或提取独立工具方法 `callLLM(prompt, modelConfig)` 供多处复用。

---

### 1.2 ChromaDB 无单文档更新能力

**现状**: 现有 `ChromaDBService` 只支持:
- `addChunks(kbId, chunks)` — 批量写入分块（包含向量化）
- `query(kbId, text, topK)` — 语义搜索
- `deleteByDocument(kbId, docId)` — 按 metadata.document_id 删

方案中 MemoryChromaService 需要的 `updateMemory()` 无法直接实现：
- ChromaDB 原生不支持 update 单文档
- 需要「先删后加」，但现有 API 不支持按 `chroma_id` 删除
- 现有 `ChromaDBService` 强绑定 `kbId` 和 `kb_` 前缀，与 `mem_` 前缀冲突

**修复方案**: MemoryChromaService **不应复用 ChromaDBService**，而是直接调 ChromaDB V2 HTTP API：
```
updateMemory(userId, chromaId, newText):
  1. GET /collections/mem_{userId}/get?ids=[chromaId]  → 获取旧 metadata
  2. POST /collections/mem_{userId}/delete  {"ids": [chromaId]}
  3. embed(newText) → POST /collections/mem_{userId}/add  (新向量 + 旧 metadata)
```

理由: 记忆是单文档操作，不是分块操作；Collection 命名体系不同；语义完全独立，不应混用。

---

### 1.3 压缩导致原文永久丢失

**现状**: 阶梯衰减流程:
```
FULL: "用户叫张三，28岁，住在北京朝阳区..."
  ↓ compressWithLLM()
BRIEF: "张三，28岁，北京互联网后端开发"  ← value 被覆盖
  ↓ 用户问起 → 模式3 "恢复"
"FULL": "张三，28岁，北京互联网后端开发"  ← 原文已不可逆
```

`detailLevel` 回到 FULL，但内容是 BRIEF 级别的，无法还原细节。

**修复方案**: 
- memory_items 表增加 `original_value TEXT` 列，始终保留首次提取的原文
- 压缩时只更新 `value`，不动 `original_value`
- 模式3 恢复时，从 `original_value` 回写 `value`，ChromaDB 重新向量化原文
- ChromaDB metadata 标记 `original_available: true`

---

### 1.4 模式3 "按需回溯" 无自动触发机制

**现状**: 
- 模式2（时间倒序注入）在 `buildMessagesArray()` 中实现 ✓
- 模式3（语义搜索）只在 API `/api/memory/search` 中暴露 ✗
- `buildMessagesArray()` 中没有判断"用户是否在问历史内容"的逻辑
- 方案描述 "触发: 用户提问涉及历史内容时" 无代码支撑

**修复方案**:

选项A（推荐，最简单）: 每次对话同时做模式2 + 模式3
```
模式2: 注入最近20条清晰期/模糊期记忆
模式3: 用当前消息语义搜索 ChromaDB Top-5，标注"相关记忆"注入
```
两条独立注入，互不干扰。

选项B（轻量触发）: 关键词判断 + 语义搜索
```java
private static final Pattern RECALL_PATTERN = 
    Pattern.compile("之前|上次|你还记得|那个|以前|说过|聊过|再.*一下");
if (RECALL_PATTERN.matcher(userMessage).find()) {
    // 触发模式3 语义搜索
}
```


## 二、重要问题 (4项) — 影响实现质量

### 2.1 @EnableAsync 未配置

**现状**: `AichatApplication` 有 `@EnableScheduling`，但 **没有 `@EnableAsync`**。
`@Async` 注解在 `extractAndStore()` 和 `checkAndGenerate()` 上会**静默失效**，方法在请求线程中同步执行，会显著增加对话延迟。

**修复**: 
```java
@SpringBootApplication
@EnableScheduling
@EnableAsync          // ← 新增
public class AichatApplication { ... }
```

并在 AppConfig 或独立配置类中提供 TaskExecutor:
```java
@Bean
public Executor taskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(5);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("memory-");
    return executor;
}
```

---

### 2.2 记忆提取/压缩无指定模型，无法控制成本

**现状**: 方案中 `chatService.chatSync()` 无论用什么模型配置都是隐式的。记忆提取和压缩应该用**廉价模型**（如 deepseek-chat），不能用用户当前对话的高级模型（如 deepseek-reasoner）。

每次对话后提取成本:
- 提取: 1 次 LLM 调用 (input ~500 tokens + output ~200 tokens)
- 阶梯压缩: 按衰减高峰期可能每天数十次

**修复**: 
```properties
# 记忆专用模型配置 ID (在 ModelConfig 表中创建一条低价模型记录)
memory.model.config.id=2
```

```java
@Value("${memory.model.config.id}")
private Long memoryModelConfigId;

// extractAndStore 和 compressWithLLM 中显式指定模型
String result = chatService.chatSync(prompt, memoryModelConfigId);
```

---

### 2.3 衰减任务 LLM 调用成本爆炸

**现状**: `decayMaintenance()` 对每条衰减记忆单独调用一次 LLM 压缩。
假设场景:
- 100 个用户，每人新增 3 条/天
- 3 天后: 100 × 9 = 900 条待压缩 (FULL → BRIEF)
- 每条压缩: ~500 input + ~150 output tokens
- 总成本: 900 × (500+150) = 585K tokens / 天

如果用户量和活跃度增长，凌晨 3 点可能并发数百次 LLM 调用。

**修复**: 
1. 限制每次任务最大处理量: `max(100, 每个用户最多20条)`
2. 多条记忆合并压缩（batch prompt）:
```
请分别压缩以下记忆，每行输出一条压缩结果:
1. [原文]
2. [原文]
...
```
3. 短记忆（≤50字）不调 LLM，直接字符串截断
4. 分页处理，每批间隔 2s，避免 API 限流:
```java
Pageable pageable = PageRequest.of(0, 50);
while (true) {
    var page = memoryRepo.findByDetailLevelAndLastAccessedBefore(
        DetailLevel.FULL, threshold, pageable);
    if (page.isEmpty()) break;
    // 批量压缩...
    pageable = pageable.next();
    Thread.sleep(2000);
}
```

---

### 2.4 buildMessagesArray 中逐条 save 阻塞请求线程

**现状**: 注入记忆后:
```java
for (var m : recentMemories) {
    m.setLastAccessedAt(LocalDateTime.now());
    m.setAccessCount(m.getAccessCount() + 1);
    memoryRepo.save(m);  // 同步写库 × 20次
}
```

20 条记忆 = 20 次 UPDATE，在请求线程中执行。

**修复**: 批量更新:
```java
// 收集 ID
List<Long> ids = recentMemories.stream().map(MemoryItem::getId).toList();
// 一条 SQL 批量更新
memoryRepo.batchUpdateLastAccessed(ids, LocalDateTime.now());
```

Repository:
```java
@Modifying
@Query("UPDATE MemoryItem m SET m.lastAccessedAt = :now, m.accessCount = m.accessCount + 1 WHERE m.id IN :ids")
int batchUpdateLastAccessed(@Param("ids") List<Long> ids, @Param("now") LocalDateTime now);
```


## 三、中等问题 (4项) — 影响效果和健壮性

### 3.1 ChromaDB 搜索未过滤 detail_level

**现状**: 模式3 搜索全库时不区分 FULL / BRIEF / TITLE。
- TITLE 级别（一行 ~50 字）参与语义匹配，可能因向量相似度不够而被漏掉
- 但 TITLE 级别反而可能是"最核心"的信息（经过了两次 LLM 抽象）

**建议**: 搜索时不过滤（包含所有层级），但结果排序时给 FULL 级别的命中加权重 boost (×1.2)。

---

### 3.2 多实例部署时 @Scheduled 重复执行

**现状**: `@Scheduled` 在所有实例上同时触发衰减任务。当前是单实例，但方案应注明。

**建议**: 如果未来扩展部署，使用 ShedLock 或在 SQL 中添加分布式锁:
```java
// 用 MySQL GET_LOCK 做简易分布式锁
@Scheduled(cron = "0 0 3 * * ?")
public void decayMaintenance() {
    if (!acquireLock("memory_decay_lock", 300)) return; // 另一实例已在执行
    try { /* 衰减逻辑 */ }
    finally { releaseLock("memory_decay_lock"); }
}
```

---

### 3.3 无相似记忆去重

**现状**: 用户在不同对话中表达相似信息:
- 对话1: "我喜欢用 React"
- 对话2: "我偏好 React 框架"
→ 两条独立记忆，内容高度重复

**建议**: `extractAndStore` 在写入前先 ChromaDB 搜索 Top-3 相似记忆:
```java
var existing = chromaService.search(userId, line, 3);
if (!existing.isEmpty() && existing.get(0).score() > 0.85) {
    // 相似度 > 85%，更新已有记忆而非新增
    String existingId = existing.get(0).chromaId();
    memoryRepo.findByChromaId(existingId).ifPresent(item -> {
        item.setValue(mergeValues(item.getValue(), line));
        item.setLastAccessedAt(LocalDateTime.now());
    });
    continue;
}
```

---

### 3.4 模式3 搜索结果 → MySQL 查询存在 N+1 问题

**现状**: 
```java
for (var hit : hits) {                           // K 次
    memoryRepo.findByChromaId(hit.chromaId())    // K 次 SQL 查询
}
```

**修复**: 收集 IDs 后一次批量查询:
```java
List<String> chromaIds = hits.stream().map(MemoryHit::chromaId).toList();
List<MemoryItem> items = memoryRepo.findByChromaIdIn(chromaIds);
Map<String, MemoryItem> itemMap = items.stream()
    .collect(toMap(MemoryItem::getChromaId, Function.identity()));
for (var hit : hits) {
    MemoryItem item = itemMap.get(hit.chromaId());
    // ...
}
```

Repository:
```java
List<MemoryItem> findByChromaIdIn(List<String> chromaIds);
```


## 四、边界场景 & 遗漏

### 4.1 异常场景未覆盖

| 场景 | 影响 | 建议 |
|------|------|------|
| ChromaDB 服务挂了 | 记忆提取/搜索全部失败，日志 WARN 但对话继续 | ✅ 已覆盖 |
| Embedding API 挂了 | ChromaDB write/search 失败 | ❌ 需在 catch 中区分异常类型 |
| LLM 压缩返回乱码 | value 被污染，无法还原 | ❌ 需校验压缩结果长度范围 |
| 用户 ID 不存在 | ensureCollection 可能重复创建 | ✅ 用 userId 作 Collection name |
| 记忆积累到 1000+ 条 | 注入全量撑爆 token | ❌ 方案中 mode2 已限20条 ✅ |
| memory_items 和 ChromaDB 不一致 | 双写一方失败 | ❌ 需考虑补偿/重试机制 |

### 4.2 配置硬编码

方案中多处硬编码：
- `topK = 10`（模式3 搜索）
- `n = 20`（模式2 注入）
- `0 0 3 * * ?`（衰减任务 cron）

已通过 application.properties 部分解决（memory.inject.recent-count, memory.search.top-k），但衰减 cron 未配置化。

### 4.3 用户手动添加的记忆不受衰减影响

`addManual()` 创建的记忆 `detailLevel = FULL`，同样会在 3 天后被压缩。手动记忆是否应该豁免衰减？方案未说明。

**建议**: 手动记忆 `source = "MANUAL"`，衰减时跳过：
```java
List<MemoryItem> toBrief = memoryRepo.findByDetailLevelAndSourceAndLastAccessedBefore(
    DetailLevel.FULL, "AUTO", now.minusDays(3));
```

### 4.4 记忆提取时机

方案仅在 AI 回复后提取，但用户可能在一轮对话中说多条有价值信息（长消息），LLM 只看到一轮 userMessage + aiReply，可能遗漏上下文。

**建议**: 累积 3-5 轮对话交换后批量提取，而非每轮都提取：
```java
if (buffer.size() >= 5 || lastExtract > 10min) {
    // 批量提取
}
```


## 五、优化建议

### 5.1 简化 ChromaDB 设计

MemoryChromaService 可以直接复用现有 `ChromaDBService` 的模式，但以 userId 替代 kbId：

```java
// 直接复用 ChromaDBService 的 createCollection/addChunks/query/deleteCollection
// 用 userId 传入
chromaDBService.createCollection(userId);     // Collection: mem_{userId}
chromaDBService.addChunks(userId, chunks);    // 写入单条记忆
chromaDBService.query(userId, text, topK);    // 语义搜索
chromaDBService.deleteCollection(userId);     // 清空
```

**前提**: ChromaDBService 的方法签名从 `Long kbId` 改为 `String collectionName` 参数化前缀。
或新增重载方法支持自定义 prefix。

### 5.2 记忆"唤醒"机制可视化

模式3 搜索结果标注是否来自"冷记忆"（TITLE 级别），让用户感知 AI 翻出了久远的记忆。

### 5.3 记忆热力排名

MySQL 中用 `access_count DESC` 排序展示，"经常被想起的事 → 用户最关心的事"。

### 5.4 记忆快照/导出

定期（或手动）将当前所有清晰期记忆导出为 JSON，作为备份和可移植的用户档案。


## 六、总评

| 维度 | 评价 | 说明 |
|------|------|------|
| 模型设计 | ★★★★★ | 时间衰减+访问强化+阶梯抽象 三层模型合理 |
| 现有代码适配 | ★★★☆☆ | ChromaDB API 能力有缺口，chatSync 需新增 |
| 实施可行性 | ★★★★☆ | 解决4个严重问题后可实施，难度适中 |
| 成本控制 | ★★★☆☆ | 衰减LLM调用需批次化，否则高峰期费用不可控 |
| 健壮性 | ★★★☆☆ | 异常场景覆盖不足，双写一致性需补偿 |
| 扩展性 | ★★★★☆ | Collection 按 userId 隔离天然支持多租户 |

**实施建议**: 先解决 1.1-1.4 四个严重问题，再按 2.1-2.4 优化，3.1-3.4 可在二期迭代。
