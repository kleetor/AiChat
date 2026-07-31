# Agent 循环升级计划  【暂缓】

> 创建日期：2026-07-30
> 状态：规划中
> 基于：[核心服务流分析](./core-service-flow.md)、[ToolCallRefactoringPlan](../Plans.4.0/ToolCallRefactoringPlan.md)

***

## 一、背景与目标

### 1.1 现状

当前工具调用系统已于 Plans.4.0 完成改造，具备完整的 Function Calling 能力：

```
Phase 1: 带 tools 的流式请求 → 检测 tool_calls
  → 累积分片 → 执行工具 → 结果注入 messages[]
  → Phase 2: 不带 tools 的纯文本请求 → 输出 → 结束
```

**核心限制：恰好 1 轮工具调用**。`sendPhase2Request()` 硬编码了"工具执行后强制进入纯文本生成"，模型拿到工具结果后没有机会再调用第二轮工具。

### 1.2 目标

将单轮 Function Calling 升级为**真正的 Agent 多轮循环**：

```
Round 0: Think → Act (tool_calls)
Round 1: Observe → Think → Act  ← 可再次调工具
Round N: Observe → Think → Respond (finish_reason="stop")  ← 模型自主终止
```

**模型自主决定**何时停止调用工具、何时输出最终回答，后端仅设安全上限。

### 1.3 非目标

- 不引入推理中间层（ReAct / Chain-of-Thought 显式输出）——当前设计已足够
- 不引入复杂的规划/子目标分解——超出 MVP 范围
- 不改变现有的非流式聊天路径

***

## 二、当前架构分析

### 2.1 关键代码路径

核心流式循环位于 [`ChatStreamService.java`](../../src/main/java/com/example/aichat/service/ChatStreamService.java)：

| 位置                                                                                           | 代码                                                  | 作用                                    |
| -------------------------------------------------------------------------------------------- | --------------------------------------------------- | ------------------------------------- |
| [L97-L103](../../src/main/java/com/example/aichat/service/ChatStreamService.java#L97-L103)   | `streamWithToolLoop(..., int round)`                | 工具调用入口，round 当前始终为 0                  |
| [L176-L179](../../src/main/java/com/example/aichat/service/ChatStreamService.java#L176-L179) | `buildToolsArray(tools)` 注入到请求体                     | 仅当 tools 非空时传 tools 参数                |
| [L351-L470](../../src/main/java/com/example/aichat/service/ChatStreamService.java#L351-L470) | `parseStreamWithTools()`                            | 检测 tool\_calls、累积、执行                  |
| [L419](../../src/main/java/com/example/aichat/service/ChatStreamService.java#L419)           | `finish_reason="tool_calls"` 检测                     | 触发工具执行                                |
| [L436-L437](../../src/main/java/com/example/aichat/service/ChatStreamService.java#L436-L437) | `appendAssistantToolCallMessage` + `executeTools`   | 追加 tool 消息 + 执行                       |
| [L449](../../src/main/java/com/example/aichat/service/ChatStreamService.java#L449)           | `sendPhase2Request(messages, config, emitter, ...)` | **硬编码进入纯文本** ← 需要改为递归                 |
| [L528-L579](../../src/main/java/com/example/aichat/service/ChatStreamService.java#L528-L579) | `sendPhase2Request()`                               | 不带 tools 的独立请求，走 `parseContentStream` |
| [L53](../../src/main/java/com/example/aichat/service/ChatStreamService.java#L53)             | `MAX_ROUNDS = 3`                                    | 安全上限常量（已定义但未用于循环）                     |

### 2.2 计费现状

[`BillingService.java`](../../src/main/java/com/example/aichat/service/BillingService.java) 计费模型：

```
ChatController → checkAndReserveBalance(估算)
  → LLM 调用完成 → deductTokens(实际 input+output)

预扣 = input * inputPrice + (input * 2.0) * outputPrice  (SAFETY_MULTIPLIER=2.0)
实扣 = 实际 input * inputPrice + 实际 output * outputPrice
```

**Agent 化的问题：** 当前 `SAFETY_MULTIPLIER=2.0` 仅覆盖 1 轮工具调用。3 轮 Agent 的 messages\[] 累积增长会使实际 input tokens 达到预估的 4-9 倍。

### 2.3 超时现状

```java
SSE_TIMEOUT_MS = 300_000L;   // 5 分钟
TOOL_TIMEOUT_SECONDS = 60;   // 单个工具执行超时
```

Agent 3 轮（每轮 \~30s LLM + 工具执行）约需 2-3 分钟，5 分钟够用。

***

## 三、改造方案

### 3.1 核心改动：将 Phase 2 替换为递归调用

**改造前（当前代码）：**

```java
// ChatStreamService.java parseStreamWithTools() L449
sendPhase2Request(messages, config, emitter, fullResponse, chunkBuf, eventCount);
return promptTokens;
```

**改造后：**

```java
// 递归调用 doStream，让模型自己决定是否继续调用工具
// round 递增，用于限制最大轮次
doStream(messages, config, conversationId, userMessage, userId,
        longMemoryEnabled, promptId,
        hasNextRound ? tools : Collections.emptyList(),  // 安全上限时去 tools
        round + 1, fileUrl);
return promptTokens;
```

**关键差异：**

| <br />   | 改造前                       | 改造后                          |
| -------- | ------------------------- | ---------------------------- |
| tools 参数 | 永远不传                      | round < MAX\_ROUNDS 时传，超限时不传 |
| 终止条件     | 硬编码（必定 Phase 2）           | 模型自主（`finish_reason="stop"`） |
| 递归入口     | `sendPhase2Request`（独立方法） | `doStream`（自身递归）             |

### 3.2 安全终止策略（四层防线）

```
第一层：System Prompt 规则
  "拿到工具结果后信息足够→立即回答，不要继续调工具"
  "同一工具不要用相同参数重复调用"
  "连续2次工具失败→直接告知用户无法获取"

第二层：工具结果压缩
  ToolResult.content > 2000字符 → 截断 + 注入元认知信号
  "...结果过长，已截断，如需详情请缩小搜索范围..."

第三层：MAX_ROUNDS 安全上限
  round >= MAX_AGENT_ROUNDS (建议 8-10)
  → 去掉 tools 参数，强制模型基于已有信息生成回答

第四层：SSE 整体超时
  SSE_TIMEOUT_MS = 600_000 (10 分钟)
  超时 → emitter.completeWithError()
```

### 3.3 终止规则的 System Prompt 注入

在 [`MessageContextBuilder.java`](../../src/main/java/com/example/aichat/service/MessageContextBuilder.java) 的系统规则区域（第 73-85 行已有注入机制）增加一条：

```
## 工具调用终止规则
1. 调用工具前先判断：现有上下文是否已足够回答用户问题？
   是 → 直接回答，不要调用工具。
2. 一次调用多个独立的工具（如同时搜索多个关键词），不要串行。
3. 拿到工具返回的结果后：
   - 信息已足够 → 立即给出完整回答，不要继续调用工具
   - 信息不够 → 最多再调 1-2 次，然后基于现有信息给出回答
4. 同一工具不要用相同或相似的参数重复调用。
5. 如果工具连续 2 次返回错误或无结果，直接告知用户"无法获取该信息"。
```

### 3.4 工具结果压缩

在 `appendToolResultMessage` 调用前增加压缩逻辑：

```java
// ChatStreamService 新增方法
private String compressToolResult(String content) {
    if (content == null || content.length() <= 2000) return content;
    return content.substring(0, 1000)
            + "\n\n[...结果过长，已截断。如需详细内容，请缩小搜索范围或提出更具体的问题...]\n\n"
            + content.substring(content.length() - 500);
}
```

### 3.5 跨轮次 Token 累积追踪

当前 `doStream` 中用 `AtomicLong promptTokensRef` 追踪单次请求用量。Agent 模式下需要跨轮次累加：

```java
// doStream() 的 task Runnable 中
// 改造前：每次 doStream 创建新的 AtomicLong
AtomicLong promptTokensRef = new AtomicLong(0);

// 改造后：从外层传入累加器
// 首次调用时创建，递归时透传
if (accumulatedPrompt == null) accumulatedPrompt = new AtomicLong(0);
if (accumulatedCompletion == null) accumulatedCompletion = new AtomicLong(0);

// 每轮结束后累加
accumulatedPrompt.addAndGet(thisRoundPrompt);
accumulatedCompletion.addAndGet(thisRoundCompletion);

// 最后一轮（模型输出 stop）时：
doBilling()  // 使用累计值
```

**消息保存策略：** 仅在最终轮保存完整回复。中间轮只追加 tool\_calls 消息到 messages\[]，不保存到数据库。

### 3.6 计费改造

#### 预扣系数调整

```java
// BillingService 或 ChatService 中
// 改造前
private static final BigDecimal SAFETY_MULTIPLIER = new BigDecimal("2.0");

// 改造后：Agent 模式使用更高系数
// 3 轮 × 每轮输入膨胀系数 1.5 ≈ 4.5，取 6.0 留安全余量
private static final BigDecimal AGENT_SAFETY_MULTIPLIER = new BigDecimal("6.0");
```

#### 计费时机

```
改造前：
  预扣 → 1 轮 LLM → 扣费

改造后：
  预扣（Agent 系数）→ Round 0 → Round 1 → ... → Round N (stop) → 扣费
                                                          ↑
                                               累计所有轮次的 tokens
```

实扣逻辑 `deductTokens()` 无需改动——它接收累计后的 inputTokens + outputTokens 即可。

### 3.7 前端进度反馈

每轮循环开始时通知前端当前进度：

```java
// 每次递归 doStream 前
safeSend(emitter, "status",
    "{\"status\":\"agent_round\",\"round\":" + round + ",\"maxRounds\":" + MAX_AGENT_ROUNDS + "}",
    MediaType.APPLICATION_JSON);
```

前端 `useChat.ts` 新增监听：

```
event: status  data: {"status":"agent_round","round":1,"maxRounds":8}
→ 显示 "正在思考...（第 1 轮）"

event: status  data: {"status":"generating"}
→ 显示 "正在生成回复..."
```

### 3.8 改造后的请求体变化示例

**Round 0（带 tools）：**

```json
{
  "model": "deepseek-chat",
  "stream": true,
  "messages": [
    {"role":"system", "content":"系统规则...\n## 工具调用终止规则\n..."},
    {"role":"user", "content":"帮我查一下重庆今天天气，再对比一下北京"}
  ],
  "tools": [search_web, analyze_image]
}
```

**Round 1（模型决定需要两次搜索，带 tools）：**

```json
{
  "model": "deepseek-chat",
  "stream": true,
  "messages": [
    ...system...
    {"role":"user", "content":"..."},
    {"role":"assistant", "content":null, "tool_calls":[
      {"id":"call_1","function":{"name":"search_web","arguments":"{\"query\":\"重庆天气 2026-07-30\"}"}},
      {"id":"call_2","function":{"name":"search_web","arguments":"{\"query\":\"北京天气 2026-07-30\"}"}}
    ]},
    {"role":"tool", "tool_call_id":"call_1", "content":"## 搜索结果\n重庆..."},
    {"role":"tool", "tool_call_id":"call_2", "content":"## 搜索结果\n北京..."}
  ],
  "tools": [search_web]   // ← 仍带 tools，模型可继续调用
}
```

**Round N（模型判断信息足够，finish\_reason="stop"，带 tools 但模型不调用）：**

```json
{
  "model": "deepseek-chat",
  "stream": true,
  "messages": [
    ...所有历史...
  ],
  "tools": [search_web]
}
// SSE 流中：delta.content="重庆今天..." → finish_reason="stop"
```

***

## 四、实施步骤

### Phase 1：核心循环改造（P0）

| #   | 步骤                                                                    | 文件                     | 改动量    |
| --- | --------------------------------------------------------------------- | ---------------------- | ------ |
| 1.1 | `MAX_ROUNDS` 从 3 改为 10，新增 `MAX_AGENT_ROUNDS`                          | ChatStreamService.java | 1 行    |
| 1.2 | `sendPhase2Request` 替换为递归 `doStream` 调用                               | ChatStreamService.java | \~15 行 |
| 1.3 | 超限时去掉 tools 兜底（`round >= MAX_AGENT_ROUNDS → Collections.emptyList()`） | ChatStreamService.java | \~5 行  |
| 1.4 | `parseStreamWithTools` 返回类型改为支持递归（返回 promptTokens，递归后累加）              | ChatStreamService.java | \~10 行 |
| 1.5 | `safeComplete` 改为仅在最终轮触发（中间轮不 complete emitter）                       | ChatStreamService.java | \~5 行  |
| 1.6 | 删除 `sendPhase2Request` 方法（逻辑合并到递归 `doStream` 中）                       | ChatStreamService.java | -80 行  |

### Phase 2：终止策略（P0）

| #   | 步骤                            | 文件                         | 改动量    |
| --- | ----------------------------- | -------------------------- | ------ |
| 2.1 | System Prompt 注入"工具调用终止规则"    | MessageContextBuilder.java | \~15 行 |
| 2.2 | 工具结果压缩 `compressToolResult()` | ChatStreamService.java     | \~15 行 |
| 2.3 | 工具执行失败计数器（连续 2 次失败 → 去 tools） | ChatStreamService.java     | \~10 行 |

### Phase 3：计费与追踪（P0）

| #   | 步骤                                         | 文件                                     | 改动量    |
| --- | ------------------------------------------ | -------------------------------------- | ------ |
| 3.1 | 跨轮次 token 累积（AtomicLong 从外层透传）             | ChatStreamService.java                 | \~10 行 |
| 3.2 | Agent 模式预扣系数 `AGENT_SAFETY_MULTIPLIER=6.0` | ChatService.java / BillingService.java | \~5 行  |
| 3.3 | 仅最终轮保存消息到数据库                               | ChatStreamService.java                 | \~5 行  |
| 3.4 | 扣费仅在最终轮执行一次                                | ChatStreamService.java                 | \~10 行 |

### Phase 4：前端与体验（P1）

| #   | 步骤                                         | 文件                     | 改动量    |
| --- | ------------------------------------------ | ---------------------- | ------ |
| 4.1 | 每轮开始时 SSE 推送 `agent_round` 进度事件            | ChatStreamService.java | \~5 行  |
| 4.2 | 前端 `useChat.ts` 监听 `agent_round` 事件，显示轮次提示 | useChat.ts             | \~10 行 |
| 4.3 | SSE 超时从 5 分钟延长到 10 分钟                      | ChatStreamService.java | 1 行    |

### Phase 5：测试与验收（P1）

| #   | 步骤                         |
| --- | -------------------------- |
| 5.1 | 回归测试：无工具场景行为不变             |
| 5.2 | 单轮工具场景：与改造前一致（1 轮工具 + 文本）  |
| 5.3 | 多轮工具场景：模型自主调 2+ 轮工具后给出回答   |
| 5.4 | 终止测试：MAX\_ROUNDS 超限后正常生成文本 |
| 5.5 | 终止测试：模型在不需要工具时直接回答（0 轮工具）  |
| 5.6 | 计费测试：多轮 token 累计值正确        |
| 5.7 | 异常测试：工具执行失败不中断循环           |

***

## 五、改动量总览

| 文件                           | 新增          | 删除                           | 净改动       |
| ---------------------------- | ----------- | ---------------------------- | --------- |
| `ChatStreamService.java`     | \~80 行      | \~80 行 (`sendPhase2Request`) | \~0       |
| `MessageContextBuilder.java` | \~15 行      | 0                            | +15       |
| `ChatService.java`           | \~5 行       | 0                            | +5        |
| `BillingService.java`        | \~5 行       | 0                            | +5        |
| `useChat.ts`                 | \~10 行      | 0                            | +10       |
| **合计**                       | **\~115 行** | **\~80 行**                   | **+35 行** |

**不涉及：** 新文件、新数据库迁移、新依赖。

***

## 六、风险与缓解

| 风险                        | 概率 | 缓解                                           |
| ------------------------- | -- | -------------------------------------------- |
| 模型陷入无限工具循环                | 低  | MAX\_ROUNDS=10 安全上限 + 连续失败计数器                |
| 模型过早终止（工具可用但不用）           | 中  | System Prompt 引导 + tool description 明确触发场景   |
| 消息数组过长超过模型 context window | 低  | 工具结果压缩（2000 字符截断） + MAX\_HISTORY\_SIZE=30 不变 |
| 递归 SSE 中 emitter 生命周期混乱   | 低  | 只在最终轮 complete/error，中间轮仅 push data          |
| 预扣金额不足导致实扣失败              | 低  | AGENT\_SAFETY\_MULTIPLIER=6.0 覆盖 3 轮 × 2x 膨胀 |
| SSE 超时（多轮耗时过长）            | 低  | 超时从 5 分钟延长到 10 分钟                            |

***

## 七、验收标准

- [ ] Agent 模式下模型可自主进行 2+ 轮工具调用
- [ ] 模型判断信息足够后输出 `finish_reason="stop"`，循环正常终止
- [ ] 不触发工具的场景（`finish_reason="stop"` 首轮）行为与改造前完全一致
- [ ] MAX\_ROUNDS 超限后去掉 tools 参数，正常生成文本，不报错
- [ ] 多轮 token 累计正确，扣费金额准确
- [ ] 前端轮次进度提示正常显示
- [ ] 无内存泄漏（递归不产生悬空 emitter）

