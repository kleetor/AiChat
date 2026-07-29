# 工具调用改造 — 实施检查清单

> 创建日期：2026-07-20 | 预计工时：待评估

---

## Phase 1：基础工具框架

| # | 步骤 | 文件 | 状态 | 备注 |
|---|------|------|------|------|
| 1.1 | 定义 `ToolDefinition` — name, description, parameters JSON Schema, toJsonNode() | `service/tool/ToolDefinition.java` | ☐ | 值对象 |
| 1.2 | 定义 `ToolCall` — id, name, arguments | `service/tool/ToolCall.java` | ☐ | 值对象 |
| 1.3 | 定义 `ToolResult` — toolCallId, name, content | `service/tool/ToolResult.java` | ☐ | 值对象 |
| 1.4 | 定义 `ToolHandler` 接口 — name(), getDefinition(), execute() | `service/tool/ToolHandler.java` | ☐ | 接口 |
| 1.5 | 实现 `ToolRegistry` — Spring 收集所有 handler, execute(), getActiveTools(), hasActiveTools() | `service/tool/ToolRegistry.java` | ☐ | @Component |
| 1.6 | 实现 `ToolCallAccumulator` — 按 index 分组累积 + accumulateDelta() 静态方法 + finalize() | `service/tool/ToolCallAccumulator.java` | ☐ | 流式累积核心 |
| 1.7 | `ModelConfig` 新增 `supportsToolCalling` 字段（默认 false） | `model/ModelConfig.java` | ☐ | +数据库迁移 |
| 1.8 | 数据库迁移：`model_configs` 表加 `supports_tool_calling` 列 | `resources/db/migration/` | ☐ | TINYINT(1) DEFAULT 0 |
| 1.9 | `ChatStreamService` 提取公共方法 `parseContentStream()` | `service/ChatStreamService.java` | ☐ | 重构，减少重复 |
| 1.10 | `ChatStreamService` 新增 `streamWithToolLoop()` 骨架（含 tools 请求体构建、SSE 解析中 tool_calls 检测/累积/finish_reason 判断） | `service/ChatStreamService.java` | ☐ | 核心逻辑 |
| 1.11 | `streamWithToolLoop` 实现递归调用（Phase 2 不带 tools → 走 `parseContentStream`） | `service/ChatStreamService.java` | ☐ | 递归 + emitter 转发 |

### Phase 1 验证

- [ ] 单元测试：`ToolCallAccumulator` 正确累积并行 tool_calls 分片
- [ ] 单元测试：`ToolRegistry.getActiveTools()` 正确过滤
- [ ] 回归测试：无工具时 `streamWithToolLoop` 行为与 `streamDeepSeek` 一致

---

## Phase 2：搜索工具化

| # | 步骤 | 文件 | 状态 | 备注 |
|---|------|------|------|------|
| 2.1 | 实现 `SearchWebTool` — Tavily + 千帆双引擎降级 | `service/tool/SearchWebTool.java` | ☐ | @Component |
| 2.2 | 改造 `MessageContextBuilder.buildMessagesArray()` — webSearchEnabled 分支不再注入搜索结果 | `service/MessageContextBuilder.java` | ☐ | ~15行 |
| 2.3 | 改造 `ChatService.chatStream()` — 判断 supportsToolCalling && webSearchEnabled → 传 tools 走 streamWithToolLoop | `service/ChatService.java` | ☐ | 分流逻辑 |
| 2.4 | 改造 `ChatService.chatAndSave()` — 同理，非流式也走工具路径(调用 LLMService 非流式 tool_calls) | `service/ChatService.java` | ☐ | 非流式路径 |
| 2.5 | 前端：SSE 监听 `status` 事件，显示"正在搜索..." | `useChat.ts` / 前端组件 | ☐ | 用户体验 |

### Phase 2 验证

- [ ] 端到端测试：webSearchEnabled=true → LLM 调用 search_web → 正确回答
- [ ] 无工具场景回归：webSearchEnabled=true 但 LLM 选择不调用 → 正常回复
- [ ] 降级测试：webSearchEnabled=false → 不走任何工具路径，行为不变
- [ ] 双引擎降级测试：停用 Tavily 后千帆正常工作

---

## Phase 3：识图工具化

| # | 步骤 | 文件 | 状态 | 备注 |
|---|------|------|------|------|
| 3.1 | 实现 `AnalyzeImageTool` — 复用 ImageService.recognizeImage() | `service/tool/AnalyzeImageTool.java` | ☐ | @Component |
| 3.2 | `ChatRequest` 新增 `imageUrl` 字段 | `dto/ChatRequest.java` | ☐ | 保留 imageDescription 兼容 |
| 3.3 | `ImageService` 暴露 `recognizeImage()` 为 public（已是 public） | `service/ImageService.java` | ☐ | 确认即可 |
| 3.4 | 改造 `MessageContextBuilder` — imageUrl 非 null 时注入 URL 引用 system 消息，不再注入描述文本 | `service/MessageContextBuilder.java` | ☐ | ~15行 |
| 3.5 | 改造 `ChatController.chatStream()` — 传递 imageUrl 参数 | `controller/ChatController.java` | ☐ | ~3行 |
| 3.6 | 改造 `ChatController.chat()`（非流式）— 传递 imageUrl 参数 | `controller/ChatController.java` | ☐ | ~3行 |
| 3.7 | 改造 `ChatService` — 判断 hasImageUrl → 传 tools 走工具路径 | `service/ChatService.java` | ☐ | 含混合场景 |
| 3.8 | 前端：上传图片后传 `imageUrl` 而非 `imageDescription` | `App.tsx` / `useImageUpload.ts` | ☐ | ~20行 |

### Phase 3 验证

- [ ] 端到端测试：上传图片 + 提问 → LLM 调用 analyze_image → 正确描述
- [ ] 混合场景：上传图片 + webSearchEnabled + 提问 → 同时调用两个工具
- [ ] 旧路径兼容：imageDescription 非 null → 走旧注入路径
- [ ] 无图片场景回归：不上传图片 → 行为不变

---

## Phase 4：优化与降级

| # | 步骤 | 文件 | 状态 | 备注 |
|---|------|------|------|------|
| 4.1 | 不支持 tool_calling 的模型自动降级（`supportsToolCalling=false` → 走旧注入） | `ChatService.java` | ☐ | 已含在 Phase 2/3 |
| 4.2 | MAX_ROUNDS=3 保护（超过后不带 tools 强制文本生成） | `ChatStreamService.java` | ☐ | 防无限循环 |
| 4.3 | 并行工具执行（多个 tool_calls 时并发调用 handler） | `ChatStreamService.java` | ☐ | CompletableFuture |
| 4.4 | 工具调用日志（每次工具调用 info 级日志） | `SearchWebTool.java`, `AnalyzeImageTool.java` | ☐ | 关键调试信息 |
| 4.5 | SSE `status` 事件细化：`searching` / `analyzing` / `generating`，前端对应提示 | 后端 + 前端 | ☐ | 用户体验 |
| 4.6 | token 消耗过高时搜索结果截断（~300字符） | `SearchWebTool.java` | ☐ | 可选优化 |
| 4.7 | 递归异常安全（每层独立 try-catch，冒泡到顶层时 emitter.completeWithError） | `ChatStreamService.java` | ☐ | 稳定性 |

### Phase 4 验证

- [ ] 降级测试：`supportsToolCalling=false` 模型行为与改造前完全一致
- [ ] 极限测试：连续调用工具超过 MAX_ROUNDS 次 → 强制生成文本
- [ ] 异常测试：工具执行抛异常 → 不中断整体流程
- [ ] 并行测试：多个 tool_calls 并发执行，总耗时 ≈ 最慢的工具

---

## 全局回归清单

- [ ] **无工具 + 短消息**：回复流畅，无异常 SSE 事件
- [ ] **无工具 + 长消息**：分块推送正常，无截断问题
- [ ] **扣费正常**：改造前后的 token 计费一致
- [ ] **消息保存正常**：最终回复正确保存到数据库
- [ ] **对话标题更新**：改造后标题仍能从首条消息生成

---

## 文件创建/修改总览

### 新文件 (8个)

```
src/main/java/com/example/aichat/service/tool/ToolDefinition.java
src/main/java/com/example/aichat/service/tool/ToolCall.java
src/main/java/com/example/aichat/service/tool/ToolResult.java
src/main/java/com/example/aichat/service/tool/ToolHandler.java
src/main/java/com/example/aichat/service/tool/ToolRegistry.java
src/main/java/com/example/aichat/service/tool/ToolCallAccumulator.java
src/main/java/com/example/aichat/service/tool/SearchWebTool.java
src/main/java/com/example/aichat/service/tool/AnalyzeImageTool.java
```

### 改造文件 (6个)

```
src/main/java/com/example/aichat/dto/ChatRequest.java        (+imageUrl)
src/main/java/com/example/aichat/controller/ChatController.java (传参)
src/main/java/com/example/aichat/service/MessageContextBuilder.java (移除搜索/识图注入)
src/main/java/com/example/aichat/service/ChatService.java    (分流逻辑)
src/main/java/com/example/aichat/service/ChatStreamService.java (streamWithToolLoop)
src/main/java/com/example/aichat/model/ModelConfig.java      (+supportsToolCalling)
```

### 数据库迁移 (1个)

```
resources/db/migration/V{timestamp}__add_supports_tool_calling.sql
```
