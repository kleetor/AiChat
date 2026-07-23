# 记忆系统安全加固计划

## 1. 风险汇总

审查覆盖 MemoryController → MemoryService → GraphMemoryService → ChromaDB → LLM 上下文注入 全链路。

| # | 风险 | 等级 | 修复成本 |
|---|------|------|---------|
| H1 | 记忆 API 无频率限制 | 🔴 高 | 低 |
| H2 | 记忆内容无校验 + prompt injection 无防御 | 🔴 高 | 低 |
| H3 | GraphMemoryService 实体合并无用户隔离 | 🔴 高 | 低 |
| M1 | BM25 QueryParser 查询无长度限制 | 🟡 中 | 低 |
| M2 | `/search` 接口 query 可为 null | 🟡 中 | 低 |
| M3 | `/add` 接口 value 无空值校验 | 🟡 中 | 低 |
| M4 | 记忆提取→注入闭环 prompt injection | 🟡 中 | 低 |
| M5 | 实体名称/记忆事实原样发外部 LLM | 🟡 中 | 中 |
| L1 | ChromaDB Collection 名可预测 | 🟢 低 | 低 |
| L2 | clearAll 只删 enabled=true 记录 | 🟢 低 | 低 |
| L3 | clearAll MySQL/ChromaDB 非原子 | 🟢 低 | 中 |

---

## 2. P0 修复（高危，立即实施）

### H1: 记忆 API 频率限制

**文件**: [RateLimitInterceptor.java](src/main/java/com/example/aichat/config/RateLimitInterceptor.java)

在构造函数中新增规则：

```java
// 记忆添加 30 次/天
rules.add(new RateLimitRule(
    uri -> uri.equals("/api/memory/add"), "POST",
    30, TimeUnit.DAYS.toMillis(1), "memory-add"));

// 记忆搜索 30 次/分钟（高频操作，窗口短）
rules.add(new RateLimitRule(
    uri -> uri.equals("/api/memory/search"), "POST",
    30, TimeUnit.MINUTES.toMillis(1), "memory-search"));

// 清空记忆 3 次/天（破坏性操作）
rules.add(new RateLimitRule(
    uri -> uri.equals("/api/memory/clear"), "POST",
    3, TimeUnit.DAYS.toMillis(1), "memory-clear"));
```

**未覆盖的端点**（已有其他安全机制）:
- `GET /api/memory/list` — 读取操作，风险低，不加限频
- `GET /api/memory/enabled` — 同上
- `PUT /api/memory/{id}` — 单条更新，频率自然低
- `DELETE /api/memory/{id}` — 单条删除，频率自然低

---

### H2: 记忆内容校验 + prompt injection 防御

#### 2a. MemoryController /add 空值和长度校验

**文件**: [MemoryController.java](src/main/java/com/example/aichat/controller/MemoryController.java) L44-47

```java
// 修复前
String value = (String) body.get("value");
return memoryService.addManual(getUserId(auth), value);

// 修复后
String value = (String) body.get("value");
if (value == null || value.isBlank()) {
    return ResponseEntity.badRequest().body(Map.of("error", "记忆内容不能为空"));
}
if (value.length() > 2000) {
    return ResponseEntity.badRequest().body(Map.of("error", "记忆内容过长（最大2000字符）"));
}
return memoryService.addManual(getUserId(auth), sanitizeMemoryValue(value));
```

#### 2b. MemoryController /search query 空值校验

**文件**: [MemoryController.java](src/main/java/com/example/aichat/controller/MemoryController.java) L88-93

```java
String query = (String) body.get("query");
if (query == null || query.isBlank()) {
    return ResponseEntity.badRequest().body(Map.of("error", "搜索关键词不能为空"));
}
if (query.length() > 500) {
    return ResponseEntity.badRequest().body(Map.of("error", "搜索关键词过长"));
}
```

#### 2c. 记忆内容 prompt injection 过滤

**文件**: [MemoryService.java](src/main/java/com/example/aichat/service/MemoryService.java)

新增工具方法，在 `addManual()` 和 `extractAndStore()` 写入记忆前过滤：

```java
private static final Pattern MEMORY_INJECTION_PATTERN = Pattern.compile(
    "(?i)(ignore\\s+(all\\s+)?(previous|above|prior)\\s+(instructions?|directives?|commands?|prompt)" +
    "|you\\s+are\\s+(now\\s+)?(DAN|jailbreak|an?\\s+unrestricted)" +
    "|\\[system\\]|system:\\s*(override|ignore|prompt)" +
    "|<\\|im_start\\|>|<\\|im_end\\|>)",
    Pattern.DOTALL);

static String sanitizeMemoryValue(String value) {
    if (value == null) return null;
    return MEMORY_INJECTION_PATTERN.matcher(value).replaceAll("[已过滤]");
}
```

同时在 [MessageContextBuilder.java](src/main/java/com/example/aichat/service/MessageContextBuilder.java) 注入记忆到上下文时，用明确边界包裹，防止记忆内容与 system prompt 混淆：

```java
// 修复前
sb.append("- ").append(m.getValue()).append("\n");

// 修复后
sb.append("- ").append(sanitizeMemoryValue(m.getValue())).append("\n");
```

---

### H3: 实体合并用户隔离

**文件**: [GraphMemoryService.java](src/main/java/com/example/aichat/service/GraphMemoryService.java) L311-345

`mergeEntities()` 方法需要新增 `userId` 参数并校验两个实体归属：

```java
// 修复前
public void mergeEntities(Long fromId, Long toId)

// 修复后
public void mergeEntities(Long fromId, Long toId, Long userId)

// 方法体内新增校验
MemoryEntity from = entityRepo.findById(fromId).orElseThrow(...);
MemoryEntity to = entityRepo.findById(toId).orElseThrow(...);
if (!from.getUserId().equals(userId) || !to.getUserId().equals(userId)) {
    throw BusinessException.forbidden("无权操作他人的实体");
}
```

同步修改调用方 [MemoryController.java](src/main/java/com/example/aichat/controller/MemoryController.java) 中对应的接口，从 `Authentication auth` 传入 `userId`。

`expireRelations()` 同样需要新增 `userId` 参数，先通过 `memoryItemId` 查到记忆后校验 `userId` 归属。

---

## 3. P1 修复（中危，本次一并实施）

### M1: BM25 查询长度限制

**文件**: [Bm25IndexService.java](src/main/java/com/example/aichat/service/Bm25IndexService.java) L94

```java
private static final int MAX_QUERY_LENGTH = 500;

public List<ScoredDoc> search(long userId, String query, int topK) {
    if (query == null || query.isBlank()) return List.of();
    if (query.length() > MAX_QUERY_LENGTH) {
        query = query.substring(0, MAX_QUERY_LENGTH);
    }
    // 原有逻辑...
}
```

### M2: /search 空 query 校验

已在 H2b 中一并修复。

### M3: /add 空 value 校验

已在 H2a 中一并修复。

### M4: 记忆提取闭环防御

**文件**: [MemoryService.java](src/main/java/com/example/aichat/service/MemoryService.java) L66-136

在 `extractAndStore()` 中，LLM 提取出的记忆在入库前经过 `sanitizeMemoryValue()` 过滤（同 H2c）。

### M5: 实体名称外传风险

**文件**: [GraphMemoryService.java](src/main/java/com/example/aichat/service/GraphMemoryService.java)

`extractTriples()` (L132-175) 和 `suggestMerges()` (L262-303) 调用外部 LLM 前，对实体名称列表进行脱敏处理：

```java
// 限制单次发送的实体数量上限
private static final int MAX_ENTITIES_FOR_LLM = 50;

if (entityNames.size() > MAX_ENTITIES_FOR_LLM) {
    entityNames = entityNames.subList(0, MAX_ENTITIES_FOR_LLM);
}
```

---

## 4. P2 修复（低危，后续迭代）

| # | 风险 | 方案 | 实施复杂度 |
|---|------|------|-----------|
| L1 | Collection 名可预测 | 使用 `UUID.randomUUID()` 作为 Collection 后缀 | 中（需迁移现有数据） |
| L2 | clearAll 只删 enabled | 改为 `deleteByUserId(userId)` 删除所有记录 | 低 |
| L3 | 非原子操作 | ChromaDB 删除失败时回滚 MySQL（补偿事务） | 中 |

---

## 5. 变更文件清单

| 文件 | 变更内容 | 优先级 |
|------|---------|--------|
| `RateLimitInterceptor.java` | 新增 3 条 memory-* 限频规则 | P0 |
| `MemoryController.java` | /add value 校验 + /search query 校验 + mergeEntities 传 userId | P0 |
| `MemoryService.java` | sanitizeMemoryValue() + addManual/extractAndStore 过滤 + addManual 参数前调用 | P0/P1 |
| `MessageContextBuilder.java` | 记忆注入时 sanitizeMemoryValue 过滤 | P0 |
| `GraphMemoryService.java` | mergeEntities/expireRelations 新增 userId 校验 + 实体列表上限 | P0/P1 |
| `Bm25IndexService.java` | search() 查询长度 MAX_QUERY_LENGTH=500 | P1 |
