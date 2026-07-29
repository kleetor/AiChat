# HanaChat 项目全面健康审查报告

> 审查日期：2026-07-10 | 总体评分：🟡 **6.5/10**

## 项目概览

| 维度 | 详情 |
|------|------|
| **后端** | Spring Boot 4.0.6 + Java 17 + MySQL + JPA + ChromaDB + Flyway |
| **前端** | React 19 + TypeScript 6.0 + Vite 8 + Tailwind CSS 3 |
| **AI** | Spring AI 2.0 + SiliconFlow 嵌入 + Tavily 搜索 |

---

## 一、安全问题 (Critical / High)

### 1.1 AES 加密密钥硬编码默认值

**文件**：`src/main/java/com/example/aichat/util/AESUtil.java#L25`

```java
private static final String DEFAULT_KEY = "aichat-dev-key-!";
```

**文件**：`src/main/resources/application.properties#L62`

```properties
encryption.key=${ENCRYPTION_KEY:aichat-dev-key-01}
```

如果环境变量未设，所有 API Key 用弱密钥加密，源码泄漏即全部暴露。**应移除默认值，启动时检测到默认密钥直接拒绝启动。**

---

### 1.2 登录接口无速率限制

**文件**：`src/main/java/com/example/aichat/controller/AuthController.java#L72-83`

`/api/auth/login` 没有速率限制保护，存在暴力破解风险。

---

### 1.3 JWT Token 存储方式不安全

**文件**：`frontend/src/lib/api.ts#L4`

```typescript
const TOKEN_KEY = "chat_token";
export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}
```

JWT 存 localStorage 对 XSS 完全透明，攻击者可通过 XSS 直接盗取 token。**建议改用 httpOnly cookie 或至少使用 memory + refresh token 模式。**

---

### 1.4 CORS 不一致

**文件**：`src/main/java/com/example/aichat/controller/SearchController.java#L15`

```java
@CrossOrigin(origins = "*")
```

完全开放跨域，与 WebConfig 统一管理冲突。

---

### 1.5 API Key 部分泄漏到日志

**文件**：`src/main/java/com/example/aichat/service/ChatStreamService.java#L76-79`

```java
logger.debug("ChatStreamService 使用 ModelConfig: ... apiKey前6位={} ...",
        apiKey.substring(0, Math.min(6, apiKey.length())));
```

虽然只打印了前 6 位，仍属于敏感信息泄漏。建议完全移除。

---

### 1.6 前端 XSS 风险

**文件**：`frontend/src/components/modals/WalletModal.tsx#L88-94`

赞助截图 URL 未经校验直接渲染 `<img>`，虽是相对路径但若后端返回恶意 URL 可导致问题。

---

## 二、后端架构问题 (High)

### 2.1 AdminService God Class

**文件**：`src/main/java/com/example/aichat/service/AdminService.java#L1-281`

该 Service 同时负责 7 种职责：
- 仪表盘统计 (行 44-63)
- 用户管理 (行 66-110)
- 赞助审核 (行 113-151)
- 模型配置管理 (行 154-180)
- 提示词社区管理 (行 183-241)
- 消费记录管理 (行 244-258)
- 聊天记录管理 (行 261-281)

**应拆分为 4-5 个独立 Service。**

---

### 2.2 事务注解位置错误

**文件**：`src/main/java/com/example/aichat/controller/admin/AdminSystemRuleController.java#L28`

```java
@PostMapping
@Transactional      // <-- 事务应放在 Service 层
public ResponseEntity<SystemRule> create(@RequestBody Map<String, Object> body) {
```

Controller 层不应该管理事务边界。事务应统一在 Service 层处理。

---

### 2.3 缺失 @Transactional

**文件**：`src/main/java/com/example/aichat/service/CommentService.java#L197`

`deleteComment` 中连续两个 delete 操作无事务保护，存在数据不一致风险。

---

### 2.4 依赖注入风格混用

项目中构造器注入和 `@Autowired` 字段注入混用。AdminService 有 11 个 `@Autowired` 字段。**应统一为构造器注入**，便于测试和明确依赖。

---

### 2.5 统一 API 响应体缺失

项目中存在至少 4 种不同的响应格式：
- `Map.of("message", "...")`
- `Map.of("success", true, "message", "...")`
- `ResponseEntity.ok(Map.of("error", "..."))`
- `ResponseEntity.badRequest().build()`

**应定义统一的 `ApiResponse<T>` 包装类。**

---

## 三、后端代码质量问题 (Medium)

### 3.1 滥用 RuntimeException

**文件**：`src/main/java/com/example/aichat/service/UserService.java`

十余处直接抛出 `RuntimeException`：

```java
throw new RuntimeException("用户名已存在");
throw new RuntimeException("该邮箱已注册");
throw new RuntimeException("用户名/邮箱或密码错误");
```

**应定义自定义业务异常**（如 `DuplicateUserException`、`InvalidCredentialsException`），让 GlobalExceptionHandler 返回精确 HTTP 状态码。

---

### 3.2 使用 Map 替代 DTO 绕过校验

以下文件大量使用 `Map<String, Object>` 作为请求体，绕过了 `@Valid` 校验：

- `src/main/java/com/example/aichat/controller/AuthController.java` (多处)
- `src/main/java/com/example/aichat/controller/admin/AdminSystemRuleController.java#L28-35`
- `src/main/java/com/example/aichat/controller/FriendController.java` (多处)

---

### 3.3 N+1 查询

- **`src/main/java/com/example/aichat/controller/PromptsHubController.java#L457-461`**：每条提示词单独查头像
- **`src/main/java/com/example/aichat/service/CommentService.java#L251-259`**：每条评论单独查头像

---

### 3.4 代码重复

- `updateConversationTitleIfNeeded` 在 `ChatService.java#L119-132` 和 `ChatStreamService.java#L263-277` 中完全重复
- `sendVerificationCode` / `sendResetCode` 和 `verifyCode` / `verifyResetCode` 在 `EmailService.java` 中高度相似
- `SearchController.java` GET/POST 端点逻辑完全重复

---

### 3.5 线程安全与资源管理

- **`config/AppConfig.java#L70-72`**：`Executors.newFixedThreadPool(10)` 应用关闭时线程池不会自动终止，应使用 `ThreadPoolTaskExecutor`
- **`service/UserService.java#L35`**：单例 Service 中用 `new Random()` 线程不安全，应使用 `ThreadLocalRandom`
- **`service/ChatStreamService.java#L178`**：SSE 循环中 `Thread.sleep(50)` 阻塞线程池

---

### 3.6 内存分页

**文件**：`src/main/java/com/example/aichat/service/AdminService.java#L264-276`

将全部会话加载到内存后再 subList 分页，大数据量下 OOM 风险。应在 Repository 层添加分页查询。

---

## 四、前端问题 (High)

### 4.1 App.tsx 巨型组件 — 613 行

**文件**：`frontend/src/App.tsx`

所有业务逻辑集中在一个组件中，包含：
- 15+ 个 useState
- 15+ 个 load 函数
- 图片上传、好友管理、通知、钱包等所有逻辑

**这是整个前端最严重的问题。**建议拆分为自定义 hooks：
- `useConversations`
- `useChat`（发送消息、SSE 流处理）
- `useNotifications`
- `useFriends`
- `useBilling`
- `useImageUpload`

---

### 4.2 错误处理全面静默吞没

项目中有 **40+ 处** `catch { /* ignore */ }`：

```typescript
// App.tsx L126
} catch { /* ignore */ }
```

**应该加入统一的错误上报和用户提示。**

---

### 4.3 图片上传竞态条件

**文件**：`frontend/src/App.tsx#L358-381`

`handleImageUpload` 中先 `setImagePreview(e.target?.result)` 再 `setImagePreview(null)`，存在明显竞态。

---

### 4.4 模态框使用 `if (!open) return null` 模式

**文件**：`frontend/src/components/modals/FriendModal.tsx#L67`

关闭时组件完全卸载，丢失内部状态。应保留 DOM 结构通过 CSS 控制显隐，或使用 `useEffect` 在关闭时重置关键状态。

---

### 4.5 vite.config.ts 代理配置膨胀

**文件**：`frontend/vite.config.ts`

为每个静态文件（theme.css, login.js, app.css 等 18 个路径）写了单独的代理规则。**应统一为一个通配规则**或将静态文件转由 Vite 管理。

---

### 4.6 Send 按钮缺少防抖

**文件**：`frontend/src/App.tsx#L259`

虽然有 `sendingRef.current` 锁，但 `onKeyDown` + 点击事件同时触发时仍可能双重发送。

---

### 4.7 ESLint 配置不完整

**文件**：`frontend/.oxlintrc.json`

仅配置了 2 条规则，缺少 TypeScript 严格模式检查、React hooks 依赖检查等关键规则。

---

### 4.8 FriendModal 组件过长

**文件**：`frontend/src/components/modals/FriendModal.tsx`

229 行，集成了搜索、好友列表、好友申请、聊天四个子功能。应拆分为独立的面板组件。

---

## 五、配置问题 (Medium)

| 问题 | 位置 | 建议 |
|------|------|------|
| `spring.thymeleaf.cache=false` 硬编码 | `application.properties#L4` | 移入 `application-dev.properties` |
| JWT 过期 24 小时过长 | `application.properties#L27` | 建议 1-2 小时 + Refresh Token |
| `spring-dotenv` 生产风险 | `pom.xml#L138-142` | 应仅在 dev profile 激活 |
| Actuator 无安全限制 | `pom.xml#L46-48` | 应配置 Spring Security 保护 endpoints |
| `emptyOutDir: false` 构建配置 | `vite.config.ts#L120` | 不清空输出目录可能导致残留旧文件 |

---

## 六、正面亮点

1. React 19 + Vite 8 + TypeScript 6.0 — 技术栈现代化，版本较新
2. Flyway 数据库迁移管理到位
3. ErrorBoundary 全局错误捕获已配置
4. SSE 流失响应处理实现完整（含 abort 机制）
5. shadcn/ui + Radix UI 组件库选型合理
6. 部分 DTO（`LoginRequest`、`RegisterRequest`、`ChatRequest`）设计良好
7. 已有内存记忆分衰减等级、摘要机制 — 设计思路成熟

---

## 七、优先级排序

| 优先级 | 问题 | 模块 | 影响 |
|--------|------|------|------|
| **P0** | AES 硬编码密钥 | 后端 | 数据安全 |
| **P0** | 登录无速率限制 | 后端 | 账号安全 |
| **P0** | JWT 存 localStorage | 前端 | Token 窃取 |
| **P1** | App.tsx 巨型组件拆分 | 前端 | 可维护性 |
| **P1** | 前端静默吞没错误 40+ 处 | 前端 | 用户体验 |
| **P1** | AdminService God Class | 后端 | 可维护性 |
| **P1** | Controller 层 @Transactional | 后端 | 数据一致性 |
| **P2** | Map 替代 DTO 绕过校验 | 后端 | 输入安全 |
| **P2** | N+1 查询 | 后端 | 性能 |
| **P2** | 统一 API 响应体 | 后端 | 一致性 |
| **P2** | 统一异常处理体系 | 后端 | 可靠性 |
| **P3** | vite.config 代理规则优化 | 前端 | 可维护性 |
| **P3** | 内存分页问题 | 后端 | 性能 |
| **P3** | 线程安全（Random, 线程池） | 后端 | 稳定性 |

---

## 八、统计总览

| 类别 | Critical | High | Medium | Low |
|------|----------|------|--------|-----|
| 安全 | 2 | 3 | 1 | - |
| 架构 | - | 2 | 3 | - |
| 错误处理 | - | - | 2 | - |
| 代码重复 | - | - | 3 | - |
| 输入校验 | - | 1 | 2 | - |
| N+1 查询 | - | - | 2 | - |
| 配置 | - | 1 | 3 | - |
| 前端 | - | 3 | 3 | 2 |

**共计发现 31 个问题**，其中 P0（紧急）3 个，P1（高优先级）4 个，P2（中优先级）6 个，P3（低优先级）4 个。
