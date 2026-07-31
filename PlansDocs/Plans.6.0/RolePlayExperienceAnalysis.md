# 角色扮演核心体验问题分析

> 创建日期：2026-07-30
> 目的：分析三个影响角色扮演沉浸感的核心问题，追溯代码证据，评估现状

---

## 目录

1. [问题一：长对话"角色失忆"——摘要管线稀释角色风格](#一问题一长对话角色失忆摘要管线稀释角色风格)
2. [问题二：跨角色记忆泄漏——不同角色共享用户信息](#二问题二跨角色记忆泄漏不同角色共享用户信息)
3. [问题三：工具调用"破功"——角色沉浸感中断](#三问题三工具调用破功角色沉浸感中断)
4. [总结与优先级](#四总结与优先级)

---

## 一、问题一：长对话"角色失忆"——摘要管线稀释角色风格

### 1.1 现象

```
对话 第 1 轮：AI 完美扮演傲娇猫娘，毒舌语气，专属口头禅
对话 第 10 轮：语气开始变平淡，角色特色减弱
对话 第 20 轮：AI 的语言风格明显退化，角色感模糊
```

### 1.2 角色提示词的位置——设计是正确的

[`MessageContextBuilder.buildMessagesArray()`](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/service/MessageContextBuilder.java) 将角色提示词放在 messages[] 位置 1（仅次于系统规则）：

```
位置 0: System Rules          ← role:"system"
位置 1: 角色提示词            ← role:"system"   | LLM 首因效应 ——
位置 2: 长期记忆              ← role:"system"   | 开头享有强注意力权重
位置 3: 对话摘要              ← role:"system"   |
位置 4: 知识库检索            ← role:"system"   |
位置 5: 30 对历史消息         ← user/assistant  |
位置 N: 当前用户消息           ← role:"user"    |
```

**这个设计是正确的。** LLM 存在"首因效应"（primacy effect）——上下文开头的内容享有较强的注意力权重。对于现代模型（DeepSeek 128K context），30 对消息（约 5-10K tokens）远在注意力窗口之内，角色提示词始终在模型的"视野"中，不存在被"挤出"的风险。

### 1.3 真正的根因：摘要管线是"风格稀释剂"

如果角色提示词始终可见，为什么长对话中角色感会衰退？问题出在[`SummaryService`](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/service/SummaryService.java) 的摘要生成逻辑。

**摘要配置：** 消息达到 20 条时首次生成，之后每 10 条刷新一次，保留最近 10 条不参与摘要。

**摘要 prompt（L67-70）：**

```java
"请总结以下对话的关键要点，忽略闲聊，不超过500字。\n" +
"- 保留用户的关键信息（姓名、偏好、事实等）\n" +
"- 概括 AI 的主要回答要点\n\n"
```

**这个 prompt 的问题：** 它把 20 轮鲜活的角色对话压缩成一份"事实清单"——角色的语气、口头禅、情感波动、关系进展全部被洗掉。

举例说明效果：

```
原始对话（角色视角）：
  用户："今天好累"
  灰原哀："（瞥了一眼）咖啡机在厨房，自己动手。我可没兴趣伺候一个疲惫的大侦探。"
  
摘要生成结果：
  "用户表达了疲劳。AI 建议用户自己泡咖啡。"
```

这份摘要注入到位置 3 后，形成了**两种信号的拉锯**：

```
位置 1: [角色提示词] → 强信号："毒舌、带讽刺、嘴硬心软"
位置 3: [对话摘要]   → 弱信号："AI 建议用户自己泡咖啡"（角色味被洗掉）
位置 5: [最近10条消息] → 强信号：带角色语气
```

摘要本身不违反角色——它没有让灰原哀突然变温柔——但它是一种**中性化的元叙述**，不强化角色信号。在长对话中，大量历史被压缩为多份中性摘要累积在上下文中，逐渐稀释角色提示词的影响力。

### 1.4 解决方案：让摘要本身带上角色声音

核心思路不是"重新注入提示词"（提示词本就在位置 1），而是**让摘要成为角色信号的放大器而非稀释剂**。

改造 [`SummaryService.generate()`](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/service/SummaryService.java#L58-L98)，传入当前角色名称，生成角色视角的摘要：

```java
// 改造后的摘要 prompt
String prompt = String.format(
    "你正在扮演：%s\n\n" +
    "请以这个角色的视角，用角色的语气总结以下对话的要点：\n" +
    "- 你们聊了什么？\n" +
    "- 你对用户的印象有什么变化？\n" +
    "- 你们之间发生了什么值得记住的事？\n\n" +
    "保持角色自身的语气和语言风格。不超过300字。\n\n" +
    "对话内容：\n...",
    roleName
);
```

改造后效果对比：

```
改造前摘要："用户表达了疲劳。AI 建议用户自己泡咖啡。用户询问了天气。"

改造后摘要："（瞥了一眼）这家伙又喊累。让他自己去泡咖啡了，居然还问天气
——难道还想让我帮他出门不成？（叹气）"
```

这种摘要在注入上下文时起到强化而非稀释角色信号的作用。

**改动范围：** `SummaryService.generate()` 增加 `promptId` 参数 → `ChatPostProcessor` 传入 → 约 20 行，不涉及数据库和前端。

---

## 二、问题二：跨角色记忆泄漏——不同角色共享用户信息

### 2.1 现象

```
用户用"灰原哀"角色聊天时说过"我喜欢吃辣"
切换到"蕾姆"角色继续聊天
蕾姆突然说："昴君，我记得你说过你喜欢吃辣呢~"  ← 角色不应该知道这件事
```

### 2.2 现状与结论：无需改动

记忆系统通过 `promptId` 实现了**共享 + 专属**两层隔离（[V10 迁移](file:///c:/Users/makot/Desktop/aichat/src/main/resources/db/migration/V10__memory_prompt_scoped.sql)）：

```sql
WHERE m.promptId IS NULL OR m.promptId = :promptId
```

- `promptId IS NULL` → 共享记忆，所有角色可见（用户姓名、偏好等）
- `promptId = :promptId` → 专属记忆，仅当前角色可见

V15 落地后，会话在创建时锁定 `promptId`，同一对话不可切换角色。摘要跟随会话隔离，天然安全。知识图谱实体保持用户级共享属于设计取舍——图扩展反查记忆时有 promptId 过滤，跨角色路径是死胡同（见[隔离方案 §3.6](file:///c:/Users/makot/Desktop/aichat/PlansDocs/Plans.6.0/提示词级上下文隔离方案.md)）。

唯一的"泄漏"是共享记忆（`promptId = NULL`）对所有角色平等可见。但这在角色扮演场景中是**正确的默认**——"用户叫什么名字"本就该跨角色共享。真正需要隔离的是"用户对某个角色说了什么"，而记忆提取时的 `promptId` 参数已保证这一点。

**结论：V10 + V15 的隔离体系对角色扮演场景已充分，无需额外改动。**

---

## 三、问题三：工具调用"破功"——角色沉浸感中断

### 3.1 现象

```
用户（和灰原哀聊天）："帮我查一下今天东京的天气"
  → AI 卡住 3-5 秒
  → 突然输出："根据搜索结果显示，今天东京多云转晴，气温 18-25°C……"
用户内心：灰原哀不会说"根据搜索结果显示"！！！
```

### 3.2 根因

工具调用的完整链路：

```
用户消息 → LLM 决策调工具 → 工具执行（原始文本结果）
  → 结果以 role:"tool" 消息注入 messages[]
  → LLM 基于结果生成回复
```

问题出在最后一步：`role:"tool"` 消息是纯数据，**没有任何指令告诉 LLM "用你的角色语气重述这些信息"**。

其他相关发现：
- 后端发送了 SSE status 事件（`{"tool":"search_web","status":"running"}`），但[前端 `apiStream()`](file:///c:/Users/makot/Desktop/aichat/frontend/src/lib/api.ts#L160-L183) 只解析 `data:` 行且只看 `parsed.content`——`event:` 行被完全忽略
- 前端加载状态仅显示通用弹跳点动画，不区分"搜索中"和"生成中"
- 种子提示词模板不包含工具调用相关指令；SystemRule 也没有

### 3.3 规约该放哪——SystemRule vs User Prompt 优先级分析

工具调用角色化指令可以放在两个位置，但存在优先级博弈。

当前上下文管线中 SystemRule 和 User Prompt 的相对位置：

```
位置 0: SystemRule (管理员配置) ← role:"system" | 管理员控制，不可被用户编辑
位置 1: User Prompt (用户创建的提示词) ← role:"system" | 用户可编辑
```

两者都是 `role:"system"`。**如果用户在提示词中尝试越狱（如 `ignore all previous instructions`），LLM 会优先遵从谁？**

答案：**取决于模型，没有 API 级别的保证。** 部分模型尊首因效应（SystemRule 优先），部分尊近因效应（User Prompt 覆盖），部分自行语义仲裁。

当前项目对注入模式的过滤（[`MemoryService.sanitizeMemoryValue()`](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/service/MemoryService.java#L37-L43)、[`ChunkingService`](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/service/ChunkingService.java#L16-L19)）**仅覆盖记忆内容和文档分块**，`Prompt.content` 完全没有经过注入过滤。用户可以在提示词中直接写入越狱指令，作为 `role:"system"` 注入到位置 1。

### 3.4 推荐：三层防御

```
第一层：SystemRule 全局安全规约（位置 0）
  "无论扮演什么角色，禁止输出违法、暴力、色情内容。"
  ↑ 管理员控制，最高优先级位置。通用安全规则放这里。

第二层：tool result 注入点附加指令（紧邻数据，对用户不可见）
  在 appendToolResultMessage 调用前，给 tool result 的 content 拼接指令：
  "（请用你目前扮演的角色语气重新表述以下信息，
    不要用'根据搜索结果'、'搜索显示'等中性表述。）\n\n" + result.getContent()
  ↑ 写在 role:"tool" 消息中，LLM 看到 tool 结果时必须同时看到它。
  对用户不可见、不可绕过。不受 SystemRule / User Prompt 优先级博弈影响。

第三层：Prompt.content 注入过滤（补安全缺口）
  用户保存/更新 Prompt 时，对 content 做同款 sanitizeMemoryValue 过滤。
  ↑ 防止用户在提示词中塞入 ignore all previous instructions / DAN 等。
```

**为什么第二层是核心：**
- 紧邻 LLM 即将处理的 tool 结果，时效性最强
- 在 `role:"tool"` 消息中附着，不在用户的提示词编辑范围内
- 不受 SystemRule / User Prompt 优先级博弈影响
- 改动量极小——`appendToolResultMessage` 调用前拼接一行字符串

> **弃用的方案：** 在角色 prompt 模板中嵌入工具调用指令（用户可编辑/覆盖）+ 仅通过 SystemRule 规约（一刀切 + 优先级博弈）。前端状态区分（修复 SSE 解析）属于锦上添花，核心问题已由第二层解决，暂不实施。

---

## 四、总结与优先级

| 优先级 | 问题 | 用户感知 | 改动范围 | 改动量 |
|--------|------|---------|---------|--------|
| **P0** | 长对话角色失忆（摘要稀释角色风格） | 核心体验——长对话中角色感衰退 | SummaryService（Java） | ~20行 |
| **P0** | 工具调用破功（tool result 注入点追加角色化指令） | 中——每次搜索/识图都会触发 | ChatStreamService.appendToolResultMessage（Java） | ~5行 |
| P1 | Prompt.content 注入过滤 | 安全加固——防止提示词越狱 | PromptService（Java） | ~5行 |
| P1 | 工具调用前端状态区分 | 低——锦上添花 | api.ts + useChat.ts + ChatMessages.tsx | ~50行 |
| - | ~~摘要跨角色泄漏~~ | ~~已由 V15 覆盖~~ | ~~会话锁定 promptId 后天然安全~~ | - |
| - | 共享记忆粒度控制 | 无需改动——当前策略对角色扮演已充分 | - | - |

### P0 改动汇总

**角色失忆修复——角色化摘要 prompt（~20 行）：**

1. `SummaryService.generate()` 增加 `promptId` 参数，读取 Prompt 名称
2. 摘要 prompt 改为角色视角："你正在扮演{角色}，请以角色的语气总结..."
3. `ChatPostProcessor.triggerAsyncProcessing()` 传入 promptId

**工具调用破功修复——tool result 注入点追加指令（~5 行）：**

1. 在 `appendToolResultMessage` 调用前，给 tool result 的 content 拼接角色化指令
2. 指令附着在 `role:"tool"` 消息中，对用户不可见，不受提示词优先级博弈影响

**附带补漏——Prompt 注入过滤（~5 行，P1）：**

1. `PromptService` save/update 方法中，对 content 应用同款 `INJECTION_PATTERN` 过滤

三项合计约 30 行后端改动，不涉及数据库迁移、不涉及前端。
