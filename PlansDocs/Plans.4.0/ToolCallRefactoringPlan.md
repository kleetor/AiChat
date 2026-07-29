# 工具调用（Tool Calling）改造计划

> 创建日期：2026-07-18
> 状态：规划中

---

## 一、背景与目标

### 1.1 现状问题

当前项目的搜索和识图功能通过"上下文注入"模式工作——服务端预先调用搜索/识图 API，然后将结果包装成 `role: "system"` 消息塞入 LLM 上下文。这种模式存在以下问题：

**搜索场景：**
- LLM 不知道搜索结果是怎么来的，RLHF 训练导致其对"新闻/热点"类问题条件反射式拒绝
- 典型表现：说明明搜索成功了，AI 仍说"我无法实时访问互联网"
- 详情见 `测试` 文件中的聊天记录

**识图场景：**
- 图片描述作为 system 消息注入后，LLM 可能不完全信任这段注入内容
- 用户无法在对话中途上传新图片让 LLM 主动分析（必须先上传再发消息）
- 多模态模型的视觉能力被降级为"读文字描述"，白白浪费原生视觉能力

### 1.2 改造目标

将搜索和识图从"上下文注入模式"改造为"工具调用（Function Calling）模式"：

```
改造前（被动注入）：
  服务端 → 预执行搜索/识图 → 结果塞入上下文 → LLM 被动接收 → 可能拒绝

改造后（主动调用）：
  服务端 → 声明工具定义 → LLM 自主决定是否调用 → 执行 → 结果返回 → LLM 基于结果回答
```

**核心收益：**
- LLM 自己决定调用工具，不存在"我不知道我有搜索能力"的问题
- 幻觉显著减少——LLM 倾向于使用工具结果而非编造
- 架构更规范，后续新增工具（计算器、数据库查询、代码执行等）只需注册+实现

---

## 二、当前架构分析

### 2.1 搜索功能现状

**完整链路：**

```
前端 WebSearchToggle
  → ChatRequest.webSearchEnabled = true
    → ChatController (第59/91行提取)
      → ChatService.chatStream() / chatAndSave()
        → MessageContextBuilder.buildMessagesArray() (第172-191行)
          → TavilySearchService.searchAsMarkdown(userMessage, 5)
            → 失败降级 → SearchService.searchAsMarkdown(userMessage, 5)
          → 注入: {role:"system", content:"最新搜索信息（Tavily）：\n..."}
```

**关键文件：**

| 文件 | 职责 |
|------|------|
| `service/TavilySearchService.java` | Tavily 搜索，API: `https://api.tavily.com/search` |
| `service/SearchService.java` | 百度千帆搜索，API: `https://qianfan.baidubce.com/v2/ai_search/web_search`，source=`baidu_search_v2` |
| `service/MessageContextBuilder.java` | 第172-191行：双引擎降级注入 |
| `config/props/TavilyProperties.java` | Tavily 配置（key, url） |
| `config/props/QianfanProperties.java` | 千帆搜索配置（key, url） |
| `dto/ChatRequest.java` | `webSearchEnabled` 字段 |
| `controller/ChatController.java` | 提取 webSearchEnabled 并传递 |

**搜索服务接口：**
```java
// TavilySearchService
List<Map<String, Object>> search(String query, int maxResults, String searchDepth, boolean includeAnswer)
String searchAsMarkdown(String query, int maxResults)

// SearchService (千帆)
List<Map<String, Object>> search(String query, int count)
String searchAsMarkdown(String query, int count)
```

### 2.2 识图功能现状

**完整链路：**

```
用户点击上传 → InputBar.tsx
  → POST /api/image/upload (multipart/form-data)
    → ImageController.uploadImage()
      → ImageService.uploadAndRecognize()
        → Step1: uploadImage() → S3 兼容存储 (雨云 rains3.com)
        → Step2: recognizeImage() → POST https://jeniya.cn/v1/chat/completions
                  模型: gemini-3.1-flash-lite
                  返回: AI 生成的图片描述文本
        → Step3: formatImageDescription() → 包装为 system 消息格式
      返回: { imageUrl, description }

用户发送消息 → ChatRequest.imageDescription = "图片描述文本"
  → MessageContextBuilder (第193-198行) → 注入为 system 消息
```

**关键文件：**

| 文件 | 职责 |
|------|------|
| `controller/ImageController.java` | `POST /api/image/upload`，校验 MIME 类型 |
| `service/ImageService.java` | 上传 S3 + 视觉识别 + 描述格式化 |
| `config/props/ImageProperties.java` | API key/url/model 配置 |
| `config/props/S3Properties.java` | S3 存储配置（endpoint/accessKey/secretKey/bucketName/urlPrefix） |
| `dto/ChatRequest.java` | `imageDescription` 字段 |
| `service/MessageContextBuilder.java` | 第193-198行：图片描述注入 |

**ImageService 关键方法：**
```java
// 主要入口
Map<String, String> uploadAndRecognize(MultipartFile file)  // 上传 + 识别
// 内部步骤
String uploadImage(MultipartFile file)                       // → S3，返回 URL
String recognizeImage(String imageUrl)                       // → 视觉模型，返回描述
String formatImageDescription(String imageUrl, String desc)  // → 包装为 system 格式
```

**ImageProperties 配置：**
```properties
image.api-key=${IMAGE_API_KEY}
image.api-url=https://jeniya.cn/v1/chat/completions
image.model=gemini-3.1-flash-lite
```

### 2.3 上下文注入顺序（MessageContextBuilder）

当前注入顺序（第68-205行）：

```
0. 系统规则 (SystemRule, DB中配置)
1. 用户自定义提示词 (Prompt)
2. 长期记忆 (MemoryItem)
3. 对话摘要 (ConversationSummary)
4. 知识库检索 (ChromaDB)
5. 历史消息 (user/assistant pairs)
6. 联网搜索结果 (Tavily/千帆)       ← 搜索注入点
7. 图片识别描述                      ← 识图注入点
8. 当前用户消息
```

### 2.4 ChatStreamService 流式调用现状

`ChatStreamService.streamDeepSeek()` (第64-281行)：
- 构建 `ObjectNode` 请求体，设置 `model`、`stream=true`、`messages`
- 使用 Apache HttpClient 5 发送 POST
- 逐行读取 SSE 响应，**仅解析 `choices[0].delta.content`**（第199-218行），不处理 `delta.tool_calls`
- 按 4 字符或句尾标点分块 flush 给前端

**当前请求体结构：**
```json
{
  "model": "deepseek-chat",
  "stream": true,
  "messages": [ ... ]
}
```

**关键限制：** 当前代码完全不知道 `tool_calls` 的存在，需要在此处增加分流逻辑。

### 2.5 流式/非流式策略分析（设计决策）

经过讨论，最终确定的流式策略：

```
始终使用 stream: true，在 SSE 流中区分路径：
  ├─ 检测到 delta.tool_calls → 标记本轮有工具调用，累积 tool_calls JSON 片段
  │     → finish_reason="tool_calls" 后 → 执行工具 → 递归调用（再走一次流式，不带 tools）
  └─ 没有 tool_calls → 直接推 delta.content（和当前行为完全一致）
```

**为什么不用非流式？**
- `stream` 是请求级参数，无法中途切换
- 非流式拿到文本后只能"模拟流式"输出，对于2000字以上的长回复，用户等待时间指数上涨
- 流式 tool_calls 中，`tool_calls` delta 比 `content` delta 先到达，第一个 chunk 就能判断是否要走工具

**为什么 Phase 2（工具执行后的文本生成）也不带 tools？**
- 此时 messages 已包含完整的 tool call 历史（assistant tool_call 消息 + tool 结果消息）
- LLM 只会返回纯文本，不返回 tool_calls
- 解析逻辑和当前完全一样，零额外改动

---

## 三、工具定义设计

### 3.1 search_web —— 联网搜索工具

```json
{
  "type": "function",
  "function": {
    "name": "search_web",
    "description": "搜索互联网获取实时信息。当用户询问以下内容时应调用此工具：\n- 新闻、热点、时事\n- 天气、股价、汇率等实时数据\n- 最新动态、事件进展\n- 任何需要最新信息才能准确回答的问题\n\n不要在你已知的常识性问题上调用此工具。",
    "parameters": {
      "type": "object",
      "properties": {
        "query": {
          "type": "string",
          "description": "搜索关键词，应提炼用户问题中的核心信息。尽量包含时间、地点等限定词以提高搜索结果准确性。例如：'2026年7月18日 重庆 天气'、'凡人修仙传 最新集数 2026年7月'"
        }
      },
      "required": ["query"]
    }
  }
}
```

**实现：** 复用 `TavilySearchService.searchAsMarkdown()` + `SearchService.searchAsMarkdown()` 双引擎降级。

### 3.2 analyze_image —— 图片识别工具

```json
{
  "type": "function",
  "function": {
    "name": "analyze_image",
    "description": "识别和分析用户上传的图片内容。当用户在对话中上传了图片并询问相关问题时应调用此工具。可以识别图片中的物体、人物、文字、场景等。",
    "parameters": {
      "type": "object",
      "properties": {
        "image_url": {
          "type": "string",
          "description": "图片的 URL 地址"
        }
      },
      "required": ["image_url"]
    }
  }
}
```

**实现：** 复用 `ImageService.recognizeImage()`，但跳过 S3 上传（图片已在之前上传时存到 S3）。

### 3.3 工具选择策略

不同场景下激活不同的工具：

```
webSearchEnabled=true  → tools: [search_web]
有图片上传            → tools: [analyze_image]
两者都有              → tools: [search_web, analyze_image]
都没有                → 不传 tools 字段（完全兼容现有行为）
```

---

## 四、改造方案

### 4.1 新增文件

```
src/main/java/com/example/aichat/service/tool/
├── ToolDefinition.java          # 工具定义（name, description, parameters JSON Schema）
├── ToolCall.java                # LLM 返回的工具调用请求
├── ToolResult.java              # 工具执行结果
├── ToolRegistry.java            # 工具注册中心（name → ToolHandler）
├── ToolHandler.java             # 工具处理器接口
├── ToolCallAccumulator.java     # 流式 tool_calls 分片累积器
├── SearchWebTool.java           # search_web 实现
└── AnalyzeImageTool.java        # analyze_image 实现
```

> 注：工具循环逻辑直接集成到 `ChatStreamService` 中，不再需要独立的 `ToolCallOrchestrator`。

### 4.2 改造文件

| 文件 | 改动点 | 改动量 |
|------|--------|--------|
| `MessageContextBuilder.java` | webSearchEnabled 时改为注入工具声明而非搜索结果；识图时不再注入描述文本，改为传递 imageUrl | ~20行 |
| `ChatStreamService.java` | 新增 `streamWithToolLoop()` 方法，支持 tool_calls 流式解析 | ~80行 |
| `ChatService.java` | 分流逻辑：有工具时走 orchestrator | ~30行 |
| `ChatRequest.java` | 新增 `imageUrl` 字段（替代 imageDescription 透传） | ~3行 |
| `ChatController.java` | 传递 imageUrl | ~5行 |

**总计新增约 350-400 行，改造约 140 行。**

### 4.3 核心类设计

#### ToolDefinition
```java
public class ToolDefinition {
    String name;           // "search_web"
    String description;    // 工具描述（给 LLM 看）
    JsonNode parameters;   // JSON Schema 参数定义
    boolean strict;        // 是否强制 structured output
}
```

#### ToolCall（LLM 返回的）
```java
public class ToolCall {
    String id;             // "call_abc123"
    String name;           // "search_web"
    String arguments;      // {"query":"重庆天气 2026-07-18"}（JSON 字符串）
}
```

#### ToolResult（工具执行结果）
```java
public class ToolResult {
    String toolCallId;     // 对应 ToolCall.id
    String name;           // 工具名
    String content;        // 执行结果（Markdown）
}
```

#### ToolHandler 接口
```java
public interface ToolHandler {
    String name();
    ToolDefinition getDefinition();
    ToolResult execute(ToolCall call);
}
```

#### ToolRegistry
```java
@Component
public class ToolRegistry {
    Map<String, ToolHandler> handlers;

    public ToolRegistry(List<ToolHandler> handlers) {
        // Spring 自动注入所有 ToolHandler 实现
    }

    ToolResult execute(ToolCall call);
    List<ToolDefinition> getActiveTools(...);  // 根据上下文决定激活哪些工具
}
```

#### ChatStreamService 流式工具循环（核心——始终 stream: true）

`ToolCallOrchestrator` 不作为独立类存在，而是直接集成到 `ChatStreamService` 中，因为工具循环本质上是流式 SSE 解析的自然延伸。核心逻辑：

```java
// ChatStreamService 中改造后的流式方法
public SseEmitter streamWithToolLoop(ArrayNode messages, ModelConfig config,
                                      List<ToolDefinition> tools,
                                      Long conversationId, String userMessage,
                                      Long userId, Boolean longMemoryEnabled,
                                      int round) {

    if (round >= MAX_ROUNDS) {
        // 超过最大轮次，不再带 tools，强制生成回复
        tools = Collections.emptyList();
    }

    SseEmitter emitter = new SseEmitter(120_000L);
    String apiUrl = config.getApiUrl();
    String apiKey = config.getApiKey();
    String modelName = config.getModelName();

    Runnable task = () -> {
        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", modelName);
            requestBody.put("stream", true);  // ← 始终流式
            requestBody.set("messages", messages);

            // 带 tools（Phase 2 不传 tools）
            if (tools != null && !tools.isEmpty()) {
                requestBody.set("tools", buildToolsArray(tools));
                requestBody.put("tool_choice", "auto");
            }

            HttpPost postRequest = new HttpPost(apiUrl);
            postRequest.setHeader("Content-Type", "application/json");
            postRequest.setHeader("Authorization", "Bearer " + apiKey);
            postRequest.setEntity(new StringEntity(requestBody.toString(), UTF_8));

            try (CloseableHttpResponse response = httpClient.execute(postRequest)) {
                // ... 错误处理同现有代码 ...

                StringBuilder fullResponse = new StringBuilder();
                StringBuilder chunkBuf = new StringBuilder();

                // ===== 工具调用相关状态 =====
                boolean hasToolCalls = false;
                List<ToolCallAccumulator> toolCallAccumulators = new ArrayList<>();

                try (BufferedReader reader = ...) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.trim().isEmpty()) continue;

                        String payload = line.startsWith("data:") ? line.substring(5).trim() : line;
                        if (payload.isEmpty()) continue;
                        if ("[DONE]".equals(payload)) break;

                        JsonNode root = objectMapper.readTree(payload);
                        JsonNode choices = root.get("choices");
                        if (choices == null || !choices.isArray() || choices.size() == 0) continue;

                        JsonNode delta = choices.get(0).get("delta");

                        // ===== 关键：检测 tool_calls（比 content 先到达）=====
                        if (delta != null && delta.has("tool_calls")) {
                            hasToolCalls = true;
                            accumulateToolCalls(delta.get("tool_calls"), toolCallAccumulators);
                            // ⚠️ 不推送给前端，进入累积模式
                            continue;
                        }

                        // ===== 没有 tool_calls：正常推送 content =====
                        if (!hasToolCalls && delta != null && delta.has("content")
                                && !delta.get("content").isNull()) {
                            String content = delta.get("content").asText();
                            fullResponse.append(content);
                            chunkBuf.append(content);
                            // ... 分块 push 到 emitter（和现有逻辑完全一样）
                        }

                        // ===== 检查 finish_reason =====
                        String finishReason = null;
                        if (choices.get(0).has("finish_reason")
                                && !choices.get(0).get("finish_reason").isNull()) {
                            finishReason = choices.get(0).get("finish_reason").asText();
                        }

                        if ("tool_calls".equals(finishReason)) {
                            // 工具调用完成，执行工具并递归
                            List<ToolCall> calls = finalizeToolCalls(toolCallAccumulators);

                            // 追加 assistant 消息（含 tool_calls）
                            appendAssistantToolCallMessage(messages, calls);

                            // 逐个执行工具并追加结果
                            for (ToolCall call : calls) {
                                emitter.send(SseEmitter.event()
                                        .name("status")
                                        .data("{\"tool\":\"" + call.name() + "\",\"status\":\"running\"}"));
                                ToolResult result = toolRegistry.execute(call);
                                appendToolResultMessage(messages, result);
                            }

                            // 递归调用，进入 Phase 2（不带 tools，纯文本流式生成）
                            emitter.send(SseEmitter.event()
                                    .name("status")
                                    .data("{\"status\":\"generating\"}"));
                            // 注意：此处需要将后续流式结果转发到当前 emitter
                            forwardStreamToEmitter(messages, config, emitter, conversationId,
                                    userMessage, userId, longMemoryEnabled, round + 1);
                            return;
                        }
                    }
                    // ... 正常收尾（flush 剩余 chunk、保存消息、扣费等）
                }
            }
        } catch (Exception e) { ... }
    };

    chatExecutorService.submit(task);
    return emitter;
}
```

**关键点：**

1. **第一个 tool_calls chunk 到达时设置 `hasToolCalls = true`**，此后所有 `delta.content` 都被丢弃（它们属于"工具调用前"的思考过程，不是最终输出）
2. **`finish_reason = "tool_calls"` 时**，拼接完整的 tool_calls，执行工具，递归调用 `streamWithToolLoop`（round+1，不带 tools）
3. **递归进入 Phase 2 时不再传 tools**，LLM 只返回纯文本，解析逻辑和当前完全一样
4. **递归时通过 emitter 事件转发**——Phase 2 的 content chunk 推到同一个 SSE 连接上，用户感知上是连续的

### 4.4 SearchWebTool 实现

```java
@Component
public class SearchWebTool implements ToolHandler {

    private final TavilySearchService tavily;
    private final SearchService qianfan;

    @Override
    public String name() { return "search_web"; }

    @Override
    public ToolDefinition getDefinition() {
        // 返回上面 3.1 节定义的 JSON Schema
    }

    @Override
    public ToolResult execute(ToolCall call) {
        String query = parseJson(call.arguments()).get("query").asText();

        logger.info("工具调用 search_web, query={}", query);

        try {
            String md = tavily.searchAsMarkdown(query, 5);
            return new ToolResult(call.id(), "search_web", md);
        } catch (Exception e) {
            try {
                String md = qianfan.searchAsMarkdown(query, 5);
                return new ToolResult(call.id(), "search_web", md);
            } catch (Exception e2) {
                return new ToolResult(call.id(), "search_web", "搜索暂时不可用: " + e2.getMessage());
            }
        }
    }
}
```

### 4.5 AnalyzeImageTool 实现

```java
@Component
public class AnalyzeImageTool implements ToolHandler {

    private final ImageService imageService;

    @Override
    public String name() { return "analyze_image"; }

    @Override
    public ToolDefinition getDefinition() {
        // 返回上面 3.2 节定义的 JSON Schema
    }

    @Override
    public ToolResult execute(ToolCall call) {
        String imageUrl = parseJson(call.arguments()).get("image_url").asText();

        logger.info("工具调用 analyze_image, url={}", imageUrl);

        try {
            // 直接调视觉模型，不需要再上传 S3（图片已经在上传时存好了）
            String description = imageService.recognizeImage(imageUrl);
            String formatted = imageService.formatImageDescription(imageUrl, description);
            return new ToolResult(call.id(), "analyze_image", formatted);
        } catch (Exception e) {
            return new ToolResult(call.id(), "analyze_image",
                    "图片识别失败: " + e.getMessage());
        }
    }
}
```

### 4.6 请求体变化（始终 stream: true）

**改造前（当前）：**
```json
{
  "model": "deepseek-chat",
  "stream": true,
  "messages": [
    {"role":"system", "content":"系统规则..."},
    {"role":"system", "content":"最新搜索信息：\n..."},      ← 预注入搜索结果
    {"role":"system", "content":"[系统提示：图片描述]..."},   ← 预注入图片描述
    {"role":"user", "content":"这张图片里是什么？"}
  ]
}
```

**改造后（Phase 1，带 tools——每次对话的第一轮请求）：**
```json
{
  "model": "deepseek-chat",
  "stream": true,
  "messages": [
    {"role":"system", "content":"系统规则..."},
    {"role":"system", "content":"用户上传了一张图片，URL: https://..."},
    {"role":"user", "content":"这张图片里是什么？今天有什么新闻？"}
  ],
  "tools": [
    {"type":"function","function":{"name":"search_web","description":"...","parameters":{...}}},
    {"type":"function","function":{"name":"analyze_image","description":"...","parameters":{...}}}
  ],
  "tool_choice": "auto"
}
```

**Phase 1 SSE 流中检测到 tool_calls 后，追加到 messages 的消息：**
```json
// 追加 assistant 消息
{"role":"assistant", "content":null, "tool_calls":[
  {"id":"call_1","type":"function","function":{"name":"analyze_image","arguments":"{\"image_url\":\"https://...\"}"}},
  {"id":"call_2","type":"function","function":{"name":"search_web","arguments":"{\"query\":\"2026年7月18日 热点新闻\"}"}}
]},
// 追加 tool 结果消息
{"role":"tool", "tool_call_id":"call_1", "content":"## 图片识别结果\n图片中是..."},
{"role":"tool", "tool_call_id":"call_2", "content":"## 搜索结果\n1. **今日热点**\n..."}
```

**改造后（Phase 2，不带 tools——递归调用，纯文本流式生成）：**
```json
{
  "model": "deepseek-chat",
  "stream": true,
  "messages": [
    {"role":"system", "content":"系统规则..."},
    {"role":"system", "content":"用户上传了一张图片，URL: https://..."},
    {"role":"user", "content":"这张图片里是什么？今天有什么新闻？"},
    {"role":"assistant", "content":null, "tool_calls":[...]},
    {"role":"tool", "tool_call_id":"call_1", "content":"..."},
    {"role":"tool", "tool_call_id":"call_2", "content":"..."}
  ]
  // ← 不传 tools 字段！LLM 只返回纯文本
}
```

---

## 五、改造后的数据流

### 5.1 搜索场景：用户问"今日热点新闻"

```
用户: "今日热点新闻"
  ↓
MessageContextBuilder: 构建 messages（含工具声明 system 消息，不含搜索注入）
  ↓
ChatStreamService.streamWithToolLoop(round=0):
  请求: {stream: true, messages: [...], tools: [search_web]}
    ↓
  SSE 流到达:
    chunk1: delta.tool_calls[{function.name:"search_"}]    ← 检测到！hasToolCalls=true
    chunk2: delta.tool_calls[{function.name:"web"}]
    chunk3: delta.tool_calls[{function.arguments:"{\"qu"}]  ← 累积中...
    chunk4: delta.tool_calls[{function.arguments:"ery\":"}]
    chunk5: delta.tool_calls[{function.arguments:"\"2026年7月18日 热点新闻\"}"}]
    chunk6: finish_reason="tool_calls"
    ↓
  执行 SearchWebTool → Tavily.search("2026年7月18日 热点新闻") → "## 搜索结果\n1. 王俊凯..."
  追加到 messages: [{role:"assistant", tool_calls:[...]}, {role:"tool", content:"## 搜索结果\n..."}]
  发 SSE status 事件: {"status":"generating"}
  递归调用 streamWithToolLoop(round=1, tools=[])  ← 不带 tools
    ↓
  请求: {stream: true, messages: [...含tool历史...]}
    ↓
  SSE 流到达（纯文本，无 tool_calls）:
    chunk: delta.content="根据"     → SSE push "根据"
    chunk: delta.content="实时"     → SSE push "实时"
    chunk: delta.content="搜索"     → SSE push "搜索"
    ...
    finish_reason="stop"
    ↓
  保存消息，扣费，emitter.complete()
```

### 5.2 无工具场景：用户问"哈基米是什么意思"（零额外开销）

```
用户: "哈基米是什么意思"
  ↓
MessageContextBuilder: 构建 messages
  ↓
ChatStreamService.streamWithToolLoop(round=0):
  请求: {stream: true, messages: [...], tools: [search_web]}  ← 仍带 tools，但 LLM 不调
    ↓
  SSE 流到达（纯文本，无 tool_calls——和改造前完全一样）:
    chunk: delta.content="哈基米"     → SSE push
    chunk: delta.content="是"         → SSE push
    ...
    finish_reason="stop"
    ↓
  保存消息，扣费，emitter.complete()
```

> **关键：无工具调用时，路径和改造前一模一样。** tool_calls 检测从不触发，content 直接推送，零性能开销。

### 5.3 识图场景：用户上传图片后问"这是什么"

```
用户点击上传 → POST /api/image/upload
  → ImageService.uploadAndRecognize()
    → S3 存储 → 返回 imageUrl
    → 调用视觉模型 → 返回 description（不上报给主 LLM）
  ← 前端收到 { imageUrl, description }

用户发送: "这张图里是什么？"（ChatRequest: imageUrl="https://...", 不传 imageDescription）
  ↓
MessageContextBuilder:
  - webSearchEnabled=false → 不激活 search_web
  - imageUrl 不为 null → 激活 analyze_image
  - 注入 imageUrl 引用（system 消息）: "用户上传了一张图片，URL: https://..."

  ↓
ChatStreamService.streamWithToolLoop(round=0):
  请求: {stream: true, messages: [...], tools: [analyze_image]}
    ↓
  SSE 流中检测到 tool_calls: [{name:"analyze_image", arguments:{image_url:"https://..."}}]
    ↓
  执行 AnalyzeImageTool → imageService.recognizeImage("https://...") → 调用 gemini
  追加 tool result 到 messages
  递归 streamWithToolLoop(round=1, tools=[])  ← 不带 tools
    ↓
  SSE 纯文本流式推送: "这张图片显示的是......"
```

### 5.4 混合场景：既有图片又有搜索需求

```
用户上传图片后问: "这张图片的拍摄地点是哪里？今天那边天气怎么样？"

  ↓
MessageContextBuilder: tools: [search_web, analyze_image]

  ↓
ChatStreamService.streamWithToolLoop(round=0):
  请求: {stream: true, messages: [...], tools: [search_web, analyze_image]}
    ↓
  检测到 tool_calls:
    [{name:"analyze_image", args:{image_url:"..."}},
     {name:"search_web", args:{query:"重庆解放碑 天气 2026-07-18"}}]
    ↓
  执行两个工具（可并行）→ 追加两个 tool result
  发 SSE status: "正在分析图片..." → "正在搜索..."
  递归 streamWithToolLoop(round=1, tools=[])  ← 不带 tools
    ↓
  SSE 纯文本流式推送: "根据图片识别，这里是重庆解放碑。搜索结果显示今天重庆天气..."
```

---

## 六、实施步骤

### Phase 1：基础工具框架（优先级：高）

| 步骤 | 内容 | 文件 | 预估代码量 |
|------|------|------|-----------|
| 1.1 | 新建 `service/tool/` 包，定义 `ToolDefinition`、`ToolCall`、`ToolResult` | 3个新文件 | ~80行 |
| 1.2 | 定义 `ToolHandler` 接口 + `ToolRegistry` | 2个新文件 | ~50行 |
| 1.3 | 改造 `ChatStreamService`：在 SSE 解析循环中增加 `delta.tool_calls` 检测 + 累积 + 递归逻辑 | 改造1个文件 | ~100行 |
| 1.4 | 新增 `supportsToolCalling` 到 `ModelConfig`，数据库加字段 | 1个模型 + 1个迁移文件 | ~20行 |

### Phase 2：搜索工具化（优先级：高）

| 步骤 | 内容 | 文件 | 预估代码量 |
|------|------|------|-----------|
| 2.1 | 实现 `SearchWebTool`（复用现有 Tavily + 千帆） | 1个新文件 | ~50行 |
| 2.2 | 改造 `MessageContextBuilder`：webSearchEnabled 时注入工具声明 system 消息，不再注入搜索结果 | 改造1个文件 | ~15行 |
| 2.3 | 改造 `ChatService`：根据 webSearchEnabled 决定是否传 tools 参数 | 改造1个文件 | ~15行 |
| 2.4 | 前端：tool loop 期间监听 `status` SSE 事件，显示"正在搜索..." | 改造 `useChat.ts` | ~15行 |
| 2.5 | 测试：对比改造前后搜索表现（包含无工具调用场景的性能回归验证） | - | - |

### Phase 3：识图工具化（优先级：中）

| 步骤 | 内容 | 文件 | 预估代码量 |
|------|------|------|-----------|
| 3.1 | 实现 `AnalyzeImageTool`（复用 ImageService） | 1个新文件 | ~50行 |
| 3.2 | 改造 `ChatRequest`：新增 `imageUrl` 字段，保留 `imageDescription` 兼容 | 改造1个 DTO | ~3行 |
| 3.3 | 改造 `MessageContextBuilder`：有 imageUrl 时注入 URL 引用 system 消息，不再注入描述文本 | 改造1个文件 | ~15行 |
| 3.4 | 改造前端：上传后传递 `imageUrl` 而非 `imageDescription`，走工具调用路线 | 改造 `App.tsx`、`useImageUpload.ts` | ~20行 |
| 3.5 | 测试：对比改造前后识图表现 | - | - |

### Phase 4：优化与降级（优先级：中）

| 步骤 | 内容 |
|------|------|
| 4.1 | 不支持 tool_calling 的模型 → 自动降级回"上下文注入"模式 |
| 4.2 | tool loop 轮次上限保护（超过 MAX_ROUNDS=3 强制不带 tools 生成） |
| 4.3 | 并行工具调用（当 LLM 一次返回多个 tool_calls 时，并发执行） |
| 4.4 | 工具调用日志与统计 |
| 4.5 | SSE status 事件细化（`searching` / `analyzing` / `generating`），前端对应展示不同提示

---

## 七、兼容性设计

### 7.1 模型降级

在 `ModelConfig` 中增加字段：

```sql
ALTER TABLE model_configs ADD COLUMN supports_tool_calling BIT(1) NOT NULL DEFAULT 0;
```

当 `supports_tool_calling = false` 时，保持现有的"上下文注入"模式，不做任何改动。

### 7.2 前端兼容

- 前端只需新增一个 `"正在搜索..."` / `"正在识别图片..."` 状态提示
- 现有的 WebSearchToggle 开关行为不变
- 现有的图片上传流程不变（仍然先调 `/api/image/upload`，只是传递方式从 text 改为 url）

### 7.3 API 兼容

- `ChatRequest` 向后兼容：新增字段 `imageUrl`，保留 `imageDescription`
- `imageDescription` 非 null → 按旧逻辑注入（降级路径）
- `imageUrl` 非 null → 按新逻辑走工具调用

---

## 八、风险与注意事项

| 风险 | 缓解措施 |
|------|---------|
| SSE 流中 tool_calls 分片到达，拼接逻辑复杂 | 使用累加器模式，按 `index` 字段分组累积，`finish_reason="tool_calls"` 时合并；第三方库（如 OpenAI Java SDK）已封装的逻辑可供参考 |
| token 消耗增加（tool loop 每轮重新发送完整 messages） | 限制 MAX_ROUNDS=3，搜索结果截断到 300 字符 |
| 某些模型不支持 Function Calling | ModelConfig 中加 `supports_tool_calling` 标志位，不支持则走旧逻辑 |
| 递归 SSE 调用时 emitter 生命周期管理 | Phase 2 的 content chunk 通过同一 emitter 转发，确保 complete/error 只在最终结束时触发一次 |
| LLM 可能在不需要时也调工具（过度调用） | tool description 中明确"不要在你已知的常识上调用此工具" |
| 无工具调用时长回复仍保持流式体验 | 始终 stream: true，无工具时路径和改造前完全一致，零回归风险 |
| 递归深层的异常处理 | 每个递归层级独立 try-catch，异常时 emitter.completeWithError() 终止整个链路 |

---

## 九、后续扩展方向

工具化框架完成后，可以轻松新增其他工具：

| 工具 | 用途 |
|------|------|
| `calculate` | 数学计算（解决 LLM 算不准问题） |
| `query_database` | 查询用户自己的数据 |
| `read_url` | 读取指定 URL 的内容 |
| `get_current_time` | 获取当前精确时间 |
| `send_email` | 发送邮件（需权限控制） |

只需实现 `ToolHandler` 接口并注册即可，无需改动核心流程。
