# 记忆系统改造方案：提示词级记忆隔离

> 生成日期：2026-07-22
> 前置依赖：V9 知识图谱+时态管理、多信号检索+Rerank 已就绪

## 问题

项目支持多个提示词模板，每个模板代表不同角色（编程导师、健身教练等）。当前所有记忆混在同一个用户空间，不同角色间共享。导致：
- 与编程导师对话中记录的"用户熟悉 TypeScript"会注入到健身教练的对话中
- 角色切换后，记忆空间污染，LLM 产生不相关甚至矛盾的上下文

## 方案

在 `memory_items` 加 `prompt_id` 字段：
- `NULL` = 共享记忆，所有角色可见
- `X`   = 仅角色 X 可见

现有记忆默认 `NULL`（向后兼容），新记忆根据对话上下文自动设置。

```
记忆空间: userId × (promptId | NULL=共享)
                       ↑
           NULL → 所有角色可见（姓名、偏好等通用信息）
            X   → 仅角色X可见（角色特定信息）
```

## 改动点

### 1. 数据库（V10 Flyway 迁移）

```sql
ALTER TABLE memory_items
    ADD COLUMN prompt_id BIGINT NULL,
    ADD INDEX idx_user_prompt (user_id, prompt_id, last_accessed_at DESC),
    ADD FOREIGN KEY (prompt_id) REFERENCES prompts(id) ON DELETE SET NULL;
```

### 2. 数据层

| 文件 | 变更 |
|------|------|
| `model/MemoryItem.java` | 加 `promptId` 字段 |
| `repository/MemoryItemRepository.java` | `findTopNActive()` 加 `promptId` 过滤（NULL=共享 OR promptId=当前） |

### 3. 调用链穿线：ChatService → ChatStreamService → ChatPostProcessor → MemoryService

promptId 目前的生命周期止步于 ChatService（仅传给 MessageContextBuilder 注入系统提示词）。需穿透到下游：

```
ChatService.chatStream(promptId, ...)
  ├── 已有: MessageContextBuilder.buildMessagesArray(promptId, ...)
  └── 新增: ChatStreamService.streamDeepSeek(..., promptId, ...)  // 传 promptId
              └── ChatPostProcessor.triggerAsyncProcessing(..., promptId)
                    └── MemoryService.extractAndStore(..., promptId)
```

| 文件 | 变更 |
|------|------|
| `service/ChatService.java` | `chatStream()` / `chatAndSave()` 将 promptId 传入 ChatStreamService |
| `service/ChatStreamService.java` | `streamDeepSeek()` / `streamWithToolLoop()` 加 `Long promptId` 参数，传给 ChatPostProcessor |
| `service/ChatPostProcessor.java` | `triggerAsyncProcessing()` 加 `Long promptId` 参数，传给 MemoryService |
| `service/MemoryService.java` | `extractAndStore()` 加 `Long promptId` 参数 |

### 4. 记忆提取（模式1）

```java
// MemoryService.extractAndStore(userId, conversationId, userMessage, aiReply, promptId)
MemoryItem item = MemoryItem.builder()
    .promptId(promptId)          // ← 新增：角色隔离
    .validFrom(LocalDateTime.now())
    .build();
```

手动添加的记忆默认 `prompt_id = NULL`（共享），也可通过 API 指定。

### 5. 记忆注入（模式2）

```java
// MemoryItemRepository.findTopNActive() 查询条件
WHERE user_id = :userId
  AND status IN :statuses
  AND detail_level IN :levels
  AND enabled = true
  AND (prompt_id IS NULL OR prompt_id = :promptId)  // ← 新增行
```

当 `promptId` 为 null（无角色选择时），仅注入共享记忆。

### 6. 记忆搜索（模式3）

```java
// HybridRetrievalService.hybridSearch() 三路召回完成后
// MySQL 加载候选时过滤 prompt_id
WHERE id IN (:candidateIds)
  AND (prompt_id IS NULL OR prompt_id = :promptId)
```

### 7. BM25 索引

Lucene 文档增加 `promptId` 字段，搜索时过滤：

```java
// BM25 搜索：组合条件
BooleanQuery = textQuery          // MUST
             + userIdFilter       // FILTER
             + promptIdFilter     // FILTER (SHOULD: null OR match)
```

### 8. 知识图谱与实体

实体和关系保持用户级共享（"张三"是同一个人，无论和哪个角色对话）。不做 prompt 级隔离。

## 改动清单总览

| 文件 | 操作 | 说明 |
|------|------|------|
| `V10__memory_prompt_scoped.sql` | 新增 | ALTER TABLE memory_items 加字段+索引+FK |
| `model/MemoryItem.java` | 修改 | 加 `promptId` |
| `repository/MemoryItemRepository.java` | 修改 | `findTopNActive` 加 promptId 参数 |
| `service/ChatService.java` | 修改 | 传 promptId 到 ChatStreamService |
| `service/ChatStreamService.java` | 修改 | 接收并传递 promptId 到 ChatPostProcessor |
| `service/ChatPostProcessor.java` | 修改 | 接收并传递 promptId 到 MemoryService |
| `service/MemoryService.java` | 修改 | extractAndStore 加 promptId；注入/搜索加过滤 |
| `service/MessageContextBuilder.java` | 修改 | getRecentMemoriesForContext 加 promptId 参数 |
| `service/Bm25IndexService.java` | 修改 | 索引/搜索加 promptId 过滤 |

## 向后兼容性

- 现有记忆 `prompt_id = NULL` → 所有角色可见（与前一致）
- 不选角色时（promptId=null）：仅注入共享记忆
- 选择角色时（promptId=X）：注入共享记忆 + 角色 X 的专属记忆
- 手动添加记忆时：默认共享，可选角色
