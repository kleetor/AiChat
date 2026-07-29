# 工具调用改造 — 实施规范

> 基于 [ToolCallRefactoringPlan.md](./ToolCallRefactoringPlan.md) 的详细实施规范  
> 创建日期：2026-07-20  
> 状态：待实施

---

## 一、总体架构

### 1.1 新增包结构

```
com.example.aichat.service.tool/
├── ToolDefinition.java          # 工具定义值对象
├── ToolCall.java                # LLM 返回的工具调用请求值对象
├── ToolResult.java              # 工具执行结果值对象
├── ToolHandler.java             # 工具处理器接口
├── ToolRegistry.java            # 工具注册中心 (Spring @Component)
├── ToolCallAccumulator.java     # 流式 tool_calls 分片累积器
├── SearchWebTool.java           # @Component search_web 实现
└── AnalyzeImageTool.java        # @Component analyze_image 实现
```

### 1.2 改造文件清单

| # | 文件 | 改动类型 | 核心改动 |
|---|------|---------|---------|
| 1 | `ChatRequest.java` | 新增字段 | 增加 `imageUrl` 字段 |
| 2 | `ChatController.java` | 传递参数 | stream 端点增加 imageUrl 传参 |
| 3 | `MessageContextBuilder.java` | 改造 | 搜索和识图改为工具声明注入，而非结果注入 |
| 4 | `ChatService.java` | 改造 | 根据条件传递 tools 列表，分流到 tool loop |
| 5 | `ChatStreamService.java` | 重构 | 新增 `streamWithToolLoop()`，支持 SSE 中检测/累积/执行/递归 |
| 6 | `ModelConfig.java` | 新增字段 | 增加 `supportsToolCalling` 布尔字段 |
| 7 | 数据库迁移 | 新增 | `model_configs` 表加 `supports_tool_calling` 列 |

---

## 二、详细规范

### 2.1 新增类详细规范

#### ToolDefinition

```java
package com.example.aichat.service.tool;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 工具定义，对应 OpenAI API 的 tools 数组中的每个元素。
 */
public class ToolDefinition {
    private String name;          // 工具名: "search_web" / "analyze_image"
    private String description;   // 给 LLM 看的描述
    private JsonNode parameters;  // JSON Schema 参数定义
    private boolean strict;       // 是否使用 structured output（默认 false）

    // 全参构造 + getter

    /**
     * 序列化为 OpenAI API 格式的 JsonNode。
     * 结构: {"type":"function","function":{"name":"...","description":"...","parameters":{...}}}
     */
    public ObjectNode toJsonNode(ObjectMapper mapper) { ... }
}
```

#### ToolCall

```java
package com.example.aichat.service.tool;

/**
 * LLM 返回的工具调用请求。
 */
public class ToolCall {
    private String id;        // "call_abc123"，对应 delta.tool_calls[i].id
    private String name;      // 函数名
    private String arguments; // JSON 字符串，如 {"query":"重庆天气"}

    // 全参构造 + getter
}
```

#### ToolResult

```java
package com.example.aichat.service.tool;

/**
 * 工具执行结果，将作为 role:tool 消息追加到 messages 数组。
 */
public class ToolResult {
    private String toolCallId; // 对应 ToolCall.id
    private String name;      // 工具名
    private String content;   // 执行结果（Markdown 格式文本）

    // 全参构造 + getter
}
```

#### ToolHandler 接口

```java
package com.example.aichat.service.tool;

public interface ToolHandler {
    /** 工具唯一名称 */
    String name();

    /** 返回工具定义（供 LLM 识别） */
    ToolDefinition getDefinition();

    /** 执行工具并返回结果 */
    ToolResult execute(ToolCall call);
}
```

#### ToolRegistry

```java
package com.example.aichat.service.tool;

@Component
public class ToolRegistry {
    private final Map<String, ToolHandler> handlers;  // name → handler
    private final ObjectMapper objectMapper;

    /**
     * Spring 自动注入所有 ToolHandler 实现。
     */
    public ToolRegistry(List<ToolHandler> handlerList) { ... }

    /**
     * 根据调用执行对应工具。
     */
    public ToolResult execute(ToolCall call) { ... }

    /**
     * 获取当前应激活的工具列表。
     *
     * @param webSearchEnabled 是否启用联网搜索
     * @param hasImageUrl      用户是否上传了图片
     * @return 激活的 ToolDefinition 列表
     */
    public List<ToolDefinition> getActiveTools(boolean webSearchEnabled, boolean hasImageUrl) {
        List<ToolDefinition> tools = new ArrayList<>();
        if (webSearchEnabled) tools.add(handlers.get("search_web").getDefinition());
        if (hasImageUrl)      tools.add(handlers.get("analyze_image").getDefinition());
        return tools;
    }

    /**
     * 是否有任何工具被激活。
     */
    public boolean hasActiveTools(boolean webSearchEnabled, boolean hasImageUrl) {
        return webSearchEnabled || hasImageUrl;
    }
}
```

#### ToolCallAccumulator

```java
package com.example.aichat.service.tool;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * 流式 tool_calls 分片累积器。
 * 处理多个并行 tool_calls（每个 call 有自己的 index），
 * 累积分片直到 finish_reason="tool_calls" 后合并为完整的 ToolCall 列表。
 */
public class ToolCallAccumulator {
    private final int index;               // tool_calls 数组中的索引
    private final StringBuilder id;        // 累积 id
    private final StringBuilder name;      // 累积 function.name
    private final StringBuilder arguments; // 累积 function.arguments

    public ToolCallAccumulator(int index) { ... }

    /** 添加一个 tool_call delta 分片 */
    public void accumulate(JsonNode delta) { ... }

    /** 是否已完整（id 和 name 都已收到） */
    public boolean isComplete() { ... }

    /** 合并为完整的 ToolCall */
    public ToolCall toToolCall() { ... }

    // ===== 静态工具方法 =====

    /**
     * 解析 delta.tool_calls JSON 数组，将分片累加到对应的 accumulators 中。
     * 如果某个 index 尚未有 accumulator，则新建。
     */
    public static void accumulateDelta(
            JsonNode toolCallsDelta,
            List<ToolCallAccumulator> accumulators) { ... }

    /**
     * 将所有 accumulators 合并为最终的 ToolCall 列表。
     */
    public static List<ToolCall> finalize(List<ToolCallAccumulator> accumulators) { ... }
}
```

### 2.2 SearchWebTool 规范

```java
@Component
public class SearchWebTool implements ToolHandler {

    private final TavilySearchService tavily;
    private final SearchService qianfan;

    // 双引擎降级策略:
    //   try Tavily → 失败 → try 千帆 → 失败 → 返回错误信息

    @Override
    public ToolDefinition getDefinition() {
        // 见计划 3.1 节的 JSON Schema
    }

    @Override
    public ToolResult execute(ToolCall call) {
        // 1. 从 call.arguments 解析 query
        // 2. 尝试 tavily.searchAsMarkdown(query, 5)
        // 3. 失败则降级 qianfan.searchAsMarkdown(query, 5)
        // 4. 全失败则返回错误信息
        // 5. 返回 ToolResult(toolCallId, "search_web", markdown)
    }
}
```

### 2.3 AnalyzeImageTool 规范

```java
@Component
public class AnalyzeImageTool implements ToolHandler {

    private final ImageService imageService;

    @Override
    public ToolDefinition getDefinition() {
        // 见计划 3.2 节的 JSON Schema
    }

    @Override
    public ToolResult execute(ToolCall call) {
        // 1. 从 call.arguments 解析 image_url
        // 2. 调用 imageService.recognizeImage(imageUrl) — 跳过 S3 上传
        // 3. 调用 imageService.formatImageDescription(imageUrl, description)
        // 4. 返回 ToolResult(toolCallId, "analyze_image", formatted)
    }
}
```

### 2.4 ChatRequest 改造

```java
// 新增字段：
private String imageUrl; // 图片 URL（由前端上传图片后填入），替代 imageDescription
```

**兼容策略：**
- `imageUrl` 非 null → 走工具调用新路径
- `imageDescription` 非 null（且 `imageUrl` 为 null）→ 走旧注入路径（降级）
- 两者都 null → 无图片

### 2.5 ChatController 改造

流式端点 `POST /api/chat/{conversationId}/stream`：

```java
// 新增传参：
SseEmitter emitter = chatService.chatStream(
    conversationId, request.getMessage(), ...,
    request.getImageUrl(),             // ← 新增
    request.getImageDescription()      // ← 保留兼容
);
```

非流式端点相同处理。

### 2.6 MessageContextBuilder 改造

**改造点 1：联网搜索（第172-191行）**

```java
// 改造前：
if (webSearchEnabled) {
    // 预执行搜索 → 注入 system 消息
    String results = tavily.searchAsMarkdown(...);
    // 注入: {role:"system", content:"最新搜索信息：\n..."}
}

// 改造后：
if (webSearchEnabled) {
    // 注入一条 system 消息告知 LLM 它有搜索工具可用（可选，可增强行为）
    // 但不再注入搜索结果本身
    // 搜索结果将在工具调用循环中注入
}
```

注：实际上 `MessageContextBuilder` 不需要在构建阶段注入任何与工具相关的 system 消息。工具的存在通过 API 请求的 `tools` 参数告知 LLM。**改造后此处的 webSearchEnabled 分支仅保留为日志记录**，不再注入搜索结果。

**改造点 2：图片描述（第193-198行）**

```java
// 改造前：
if (imageDescription != null) {
    // 注入: {role:"system", content:"[系统提示：图片描述]..."}
}

// 改造后：
if (imageUrl != null) {
    // 注入一条 system 消息引用图片 URL
    // {role:"system", content:"用户上传了一张图片，URL: https://..."}
}
// 保留旧路径兼容：
else if (imageDescription != null) {
    // 走旧注入路径
}
```

### 2.7 ChatService 改造

```java
public SseEmitter chatStream(..., String imageUrl, String imageDescription, ...) {
    ModelConfig config = validateAndGetConfig(conversationId, modelConfigId);

    // 判断是否支持工具调用
    boolean supportsToolCalling = Boolean.TRUE.equals(config.getSupportsToolCalling());
    boolean hasImageUrl = imageUrl != null && !imageUrl.isBlank();

    ArrayNode messagesArray = messageContextBuilder.buildMessagesArray(
            conversationId, promptId, userMessage, webSearchEnabled,
            hasImageUrl ? null : imageDescription,  // 有 imageUrl 时不传旧描述
            knowledgeBaseId, userId, longMemoryEnabled);

    if (supportsToolCalling && (webSearchEnabled || hasImageUrl)) {
        // 走工具调用路线
        List<ToolDefinition> tools = toolRegistry.getActiveTools(webSearchEnabled, hasImageUrl);
        return chatStreamService.streamWithToolLoop(
                messagesArray, config, conversationId, userMessage, userId,
                longMemoryEnabled, tools, 0);
    } else {
        // 走旧路径（不支持工具调用 或 没有工具激活）
        return chatStreamService.streamDeepSeek(
                messagesArray, config, conversationId, userMessage, userId, longMemoryEnabled);
    }
}
```

### 2.8 ChatStreamService 改造 — streamWithToolLoop

**新增方法签名：**

```java
public SseEmitter streamWithToolLoop(
    ArrayNode messages, ModelConfig config,
    Long conversationId, String userMessage, Long userId,
    Boolean longMemoryEnabled,
    List<ToolDefinition> tools,
    int round)
```

**MAX_ROUNDS 常量：** `private static final int MAX_ROUNDS = 3;`

**核心逻辑（已在计划 4.3 节详述）：**

1. 构建请求体：`model`、`stream:true`、`messages`、`tools`（非空时传入）
2. 发送 POST 请求到 LLM API
3. SSE 流解析循环中：
   - 检测 `delta.tool_calls` → 设置 `hasToolCalls=true`，调用 `ToolCallAccumulator.accumulateDelta()` 累积
   - 检测 `delta.content` → 正常推送（仅在 `hasToolCalls=false` 时）
   - 检测 `finish_reason="tool_calls"` → 执行工具、追加消息、递归
4. 递归调用时：`round+1`、不传 tools
5. 递归完成后：正常收尾（保存消息、扣费、emit complete）

**关于 Phase 2 递归：** 由于 Phase 2（round>= 1 且不带 tools）的 SSE 流和当前 `streamDeepSeek` 的解析逻辑一致，应考虑提取 `parseContentStream(reader, emitter, ...)` 作为公共方法，避免代码重复。

**提取的公共方法：**

```java
/**
 * 解析纯文本 SSE 流（无 tool_calls 场景）。
 * 返回完整响应文本，用于后续保存消息和扣费。
 */
private String parseContentStream(BufferedReader reader, SseEmitter emitter,
                                   StringBuilder chunkBuf, int flushEvery,
                                   AtomicInteger eventCount) throws IOException {
    // 从 streamDeepSeek 提取的公共解析逻辑
}

/**
 * 构建 tools JSON 数组。
 */
private ArrayNode buildToolsArray(List<ToolDefinition> tools) { ... }
```

### 2.9 ModelConfig 改造

```java
// 新增字段：
@Column(name = "supports_tool_calling")
@Builder.Default
private Boolean supportsToolCalling = false;  // 默认不支持，迁移时全部设为 false
```

**数据库迁移：**

```sql
ALTER TABLE model_configs
    ADD COLUMN supports_tool_calling TINYINT(1) NOT NULL DEFAULT 0
    COMMENT '是否支持工具调用(Function Calling)';
```

### 2.10 前端改动

**useChat.ts / 前端 SSE 事件监听：**

新增监听 SSE 事件：
- `event: status` — 工具执行状态
  - `{"tool":"search_web","status":"running"}` → 显示"正在搜索..."
  - `{"tool":"analyze_image","status":"running"}` → 显示"正在识别图片..."
  - `{"status":"generating"}` → 隐藏状态提示，显示"正在生成回复..."

**图片上传改造：**

上传完成后，前端保存 `imageUrl` 并在发送消息时作为 `ChatRequest.imageUrl` 传递，
而非像当前那样传递 `imageDescription`。

---

## 三、降级策略

### 3.1 模型不支持工具调用

当 `config.supportsToolCalling = false` 时，完全走当前路径：
- `MessageContextBuilder` 不做任何改造，仍使用上下文注入
- `ChatService` 调用旧的 `streamDeepSeek`
- 新代码零影响

### 3.2 工具执行失败

单个工具失败不应影响其他工具的调用和整体流程：
- 失败的工具返回 `ToolResult(content="xxx 暂时不可用: 错误信息")`
- LLM 会基于已有信息回复
- 不会中断整个 SSE 连接

### 3.3 递归异常保护

- 每层递归有独立 try-catch
- 递归抛出异常 → 发送 error 事件给前端 → emitter.completeWithError()
- 不产生悬空 SSE 连接

### 3.4 计费安全

- 计费在最终文本生成完成后执行（与现有逻辑一致）
- 工具调用轮次只消耗 API token（主 LLM 的请求+响应），不额外扣用户余额
- 当前已在 ChatController 中预扣余额，无需额外处理

---

## 四、实施顺序与依赖

```
Phase 1 (基础框架)
├── 1.1 ToolDefinition / ToolCall / ToolResult       ← 无依赖
├── 1.2 ToolHandler / ToolRegistry                    ← 依赖 1.1
├── 1.3 ToolCallAccumulator                           ← 依赖 1.1
├── 1.4 ModelConfig + DB migration                    ← 无依赖
└── 1.5 ChatStreamService: streamWithToolLoop 骨架     ← 依赖 1.1-1.4

Phase 2 (搜索工具化)
├── 2.1 SearchWebTool                                 ← 依赖 1.2
├── 2.2 MessageContextBuilder 改造 (搜索)              ← 依赖 1.5
├── 2.3 ChatService 分流逻辑                           ← 依赖 1.5, 2.2
└── 2.4 前端: status 事件监听                          ← 依赖 1.5

Phase 3 (识图工具化)
├── 3.1 AnalyzeImageTool                              ← 依赖 1.2
├── 3.2 ChatRequest + imageUrl 字段                   ← 无依赖
├── 3.3 MessageContextBuilder 改造 (识图)              ← 依赖 3.2
└── 3.4 前端: 传 imageUrl 而非 imageDescription         ← 依赖 3.2

Phase 4 (优化)
└── 各项优化 (日志、状态细化、并行执行等)
```

---

## 五、验收标准

### 5.1 Phase 1 验收

- [ ] `ToolRegistry.getActiveTools()` 正确返回激活的工具列表
- [ ] `ToolCallAccumulator` 能正确累积分片的 tool_calls delta 并合并
- [ ] `streamWithToolLoop` 能正确构建包含 tools 参数的请求体
- [ ] 无工具场景下 `streamWithToolLoop` 行为与 `streamDeepSeek` 一致（回归测试）

### 5.2 Phase 2 验收

- [ ] webSearchEnabled=true 时，LLM 调用 `search_web`（而非拒绝）
- [ ] 搜索结果正确渲染在回复中
- [ ] 无工具调用时性能零退化（diff 对比）

### 5.3 Phase 3 验收

- [ ] 上传图片后，LLM 调用 `analyze_image` 并基于结果回复
- [ ] 图片描述作为 tool result 注入，内容正确
- [ ] 旧路径 `imageDescription` 保持不变

### 5.4 Phase 4 验收

- [ ] 降级路径正常工作（`supportsToolCalling=false`）
- [ ] 工具调用日志完整可追踪
- [ ] MAX_ROUNDS 保护生效

---

## 六、OpenAI API 兼容性说明

### 6.1 流式 tool_calls 格式

根据 OpenAI API 规范，流式响应中的 `delta.tool_calls` 格式如下：

```json
{
  "choices": [{
    "index": 0,
    "delta": {
      "tool_calls": [{
        "index": 0,
        "id": "call_abc123",
        "type": "function",
        "function": {
          "name": "search_web",
          "arguments": ""
        }
      }]
    }
  }]
}
```

后续 chunks 可能只有部分字段：

```json
// 后续分片1：只有 arguments 继续
{"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{\"qu"}}}]}}]}

// 后续分片2：arguments 继续
{"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"ery\":\"2026年7月20日 天气\"}"}}]}}]}

// 并行调用时 index 不同
{"choices":[{"index":0,"delta":{"tool_calls":[{"index":1,"id":"call_def456","type":"function","function":{"name":"analyze_image","arguments":""}}]}}]}
```

### 6.2 ToolCallAccumulator 处理规则

- 按 `index` 分组：每个 index 对应一个独立的 ToolCall
- `id` 只出现在第一个分片中，后续分片没有 id 字段
- `function.name` 只出现在第一个分片中（或 name 出现的分片），后续只有 `function.arguments`
- `function.arguments` 是逐片拼接的 JSON 字符串
- `finish_reason="tool_calls"` 表示所有 tool_calls 都已完整
