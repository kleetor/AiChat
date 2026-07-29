# 问题修复报告

**日期**: 2026-07-28  
**分支**: 三批优化 + 运行时 Bug 修复

---

## 一、删除消息 403 Forbidden

**现象**: 聊天消息气泡点击删除被 403 拦截。

**根因**: 前端 `getChatHistory`（[services.ts](file:///c:/Users/makot/Desktop/aichat/frontend/src/lib/services.ts#L153-L165)）将 DB ID 做了 `*2` / `*2-1` 的虚拟 ID 转换：
```
DB id=3 → user 气泡 id=5, assistant 气泡 id=6
删除请求 → DELETE /api/chat/messages/5 → WHERE id=5 → 0 行 → 403
```

**修复**:
| 文件 | 变更 |
|------|------|
| `services.ts` `getChatHistory` | user: `r.id` / assistant: `-r.id`（绝对值还原 DB ID） |
| `services.ts` `deleteChatMessage` | `Math.abs(id)` 还原 DB ID |
| `App.tsx` 删除过滤 | `Math.abs(m.id) !== Math.abs(id)` 同时移除配对气泡 |
| `ChatHistoryService.deleteMessage` | `@Transactional` + JPQL `countByIdAndUserId` 校验归属 |
| `ChatMessageRepository` | 新增 `countByIdAndUserId` JPQL 查询，避免 lazy load User |

**附带修复**:
- `getHistory` 端点添加 `Cache-Control: no-store` 防止浏览器缓存

---

## 二、主聊天 SSE 流式输出空白

**现象**: 发送消息后 loading 气泡过后显示空白，刷新页面才出现 LLM 回复。

**根因**: 前后端 SSE 数据格式不匹配。
| 层 | 发送格式 | 期望格式 |
|----|---------|---------|
| 后端 | `data:Hello`（纯文本） | — |
| 前端 | — | `data:{"content":"Hello"}`（JSON） |

`JSON.parse("Hello")` → 异常 → 静默跳过 → `fullReply` 为空 → 不创建 assistant 消息。

**修复**: [ChatStreamService.java](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/service/ChatStreamService.java) — 新增 `sendContent()` 包装为 `{"content":"..."}` JSON，替换全部 5 处纯文本输出。

---

## 三、知识库文档状态不刷新

**现象**: 上传文档处理完毕后状态仍显示 PROCESSING，必须手动退出再进入才变为 READY。

**根因 1**: `KBModal.tsx` `loadDocs` 轮询自停逻辑有 bug——空知识库时立即停止轮询，上传新文档后轮询已死。

**修复 1**: 移除 `loadDocs` 自动停轮询，仅退出视图或关闭模态框时停止。

**根因 2**: `listDocuments` 的 `@Cacheable` 缓存了 PROCESSING 状态，清除缓存后前端未自动重新请求。

**修复 2**: 移除 `listDocuments` 的 `@Cacheable` 及对应 `@CacheEvict`（数据量小无需缓存）。

---

## 四、信息输入框发送后不立即清空

**现象**: 输入框内容在回复完全生成后才消失。

**修复**: [App.tsx](file:///c:/Users/makot/Desktop/aichat/frontend/src/App.tsx)
- `setInputValue("")` 移到 `await` 之前 → 立即清空
- `disabled={!isLoggedIn || chat.isGenerating}` → 生成期间灰色不可编辑

---

## 五、强制终止对话产生大量 Warn 日志

**修复**: [ChatStreamService.java](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/service/ChatStreamService.java) — SSE 写入失败时检查 `IOException: "Broken pipe"` → debug 级别，不再 warn。

---

## 六、AES 密钥前缀不一致

**现象**: 迁移代码 `AichatApplication` 使用 `"AES:"` 前缀，与 JPA Converter `"ENC:"` 不匹配。

**修复**: 统一为 `"ENC:"`，兼容修复旧 `"AES:"` 数据。

---

## 七、N+1 查询治理

| 位置 | 问题 | 修复 |
|------|------|------|
| `AdminService.getConversations` | 分页序列化每行一次 User 懒加载 | `@EntityGraph` JOIN FETCH |
| `FriendService.getFriendList` | 循环内 3N+1 查询 | 批量 `findAllById` + 批量查询 |
| `FriendService.getPendingRequests` | 循环内 N+1 查询 | 批量 `findAllById` |

---

## 八、编译错误修复

| 错误 | 位置 | 修复 |
|------|------|------|
| `Type mismatch: cannot convert` | `BillingService` | `SecureRandom.getInstanceStrong()` → `new SecureRandom()` |
| `Unhandled exception type` | `PromptsHubController` | `body(Map.of(...))` → `build()` |
| `@Query countQuery` 语法错误 | `ConversationRepository` | 移除 `@Query`，仅保留 `@EntityGraph` |
| `ExecutorService` Bean 不存在 | `KnowledgeBaseService` | 改为注入 `ThreadPoolTaskExecutor` |
| Lambda `final` 变量 | `HybridRetrievalService` | `final double rank = i` 外提循环变量 |

---

## 影响范围

| 层 | 文件数 | 风险 |
|----|--------|------|
| 前端 | 4（App.tsx, services.ts, KBModal.tsx, ChatMessages.tsx） | 低 |
| 后端 | 11（Controller × 2, Service × 5, Repository × 2, Config × 1, Util × 1） | 低 |
