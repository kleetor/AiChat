# AI Chat 全项目代码与安全审查报告（含复核）

**审查日期**: 2026-07-24\
**复核日期**: 2026-07-24（二次复核通过）\
**修复日期**: 2026-07-24（全部 HIGH 已修复）\
**审查范围**: 全部源码（Java 后端 + React 前端）\
**项目**: AI Chat (Spring Boot + React)\
**分支**: master (与 `AiChat/master` 同步)

***

## 审查摘要

| 指标     | 初报 | 复核后    | 已修复          |
| ------ | -- | ------ | ------------ |
| HIGH   | 10 | **6**  | **6 (100%)** |
| MEDIUM | 20 | **19** | 0            |
| LOW    | 21 | **24** | 0            |

***

## 复核说明

经逐项读取源码并追踪完整调用链，对以下发现进行了级别修正：

### 从 HIGH 降级（4 项）

| 编号                                          | 降级              | 原因                                                                                                                                                                                                                                 |
| ------------------------------------------- | --------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| H4 `ConversationService.deleteConversation` | HIGH→**LOW**    | [ChatController L140-148](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/controller/ChatController.java#L140-L148) 在调用 `deleteConversation()` 前已通过 `belongsToUser()` 校验所有权。Service 层缺失属防御深度不足，实际 API 不可被利用 |
| H7 `AESUtil` 零填充                            | HIGH→**MEDIUM** | 密钥来自 `ENCRYPTION_KEY` 环境变量（[AppConfig L35-37](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/config/AppConfig.java#L35-L37) 强制检查非空），攻击者需同时取得配置错误 + 数据库访问权限才可施行                                             |
| H8 `JwtAuthenticationFilter` 角色无白名单         | HIGH→**MEDIUM** | JWT 经过 HMAC-SHA256 签名验证（[JwtUtil L87-91](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/util/JwtUtil.java#L87-L91)），伪造令牌需密钥泄露，属防御深度缺失                                                                      |
| H10 `AdminConversationController` 读取聊天记录    | HIGH→**MEDIUM** | 端点受 `ROLE_ADMIN` 保护（[SecurityConfig L41](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/config/SecurityConfig.java#L41)），实际缺少的是审计日志，非权限绕过                                                                  |

### 从 MEDIUM 降级（2 项）

| 编号                                          | 降级             | 原因                                                                                                                                                                                                                                                                                                     |
| ------------------------------------------- | -------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| M1 `ChatHistoryService.saveMessage`         | MEDIUM→**LOW** | 调用方 [ChatService](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/service/ChatService.java#L131) / [ChatStreamService](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/service/ChatStreamService.java#L222) 均来自 ChatController，后者已做 `belongsToUser()` 校验 |
| M11 `AuthController.updateProfile` 缺 @Valid | MEDIUM→**LOW** | UpdateProfileRequest 仅含 1 个字段（signature），且 [Controller L130-131](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/controller/AuthController.java#L130-L131) 有手动长度校验                                                                                                              |

***

## 一、HIGH 级别问题（共 6 项，已全部修复 ✅）

### H1. CORS 允许任意来源 — WebConfig.java ✅ 已修复

**文件**: [`WebConfig.java`](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/config/WebConfig.java) :33-35, :93

**类别**: `auth_misconfiguration` | **置信度**: 0.95

`addCorsMappings` (L33) 与 `corsConfigurationSource` Bean (L93) 同时配置了 `allowedOriginPatterns("*")`。Spring Security 通过 `.cors(withDefaults())` 引用该 Bean。任意域可对全部 API 发起跨域请求。

**修复**: 双路径均改为读取 `${cors.allowed-origins}` 配置项，默认值 `http://localhost:5173,http://localhost:8080`。生产环境通过 `ALLOWED_ORIGINS` 环境变量设置真实域名。

```
修改前: .allowedOriginPatterns("*")
修改后: .allowedOriginPatterns(allowedOrigins.split(","))
```

***

### H2. 管理员默认密码硬编码 — docker-compose.yml ✅ 已修复

**文件**: [`docker-compose.yml`](file:///c:/Users/makot/Desktop/aichat/docker-compose.yml) :60-62

**类别**: `hardcoded_credentials` | **置信度**: 0.93

```yaml
# 修改前
ADMIN_USERNAME: ${ADMIN_USERNAME:-admin}
ADMIN_PASSWORD: ${ADMIN_PASSWORD:-admin123}
ADMIN_EMAIL: ${ADMIN_EMAIL:-admin@aichat.com}
```

[AichatApplication L41-61](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/AichatApplication.java#L41-L61) 在启动时调用 `initAdmin` 自动创建管理员账户。若环境变量未设置，将以 `admin123` 密码创建管理员。

**修复**: 使用 Docker `:?` 语法，未设环境变量则容器启动失败：

```yaml
# 修改后
ADMIN_USERNAME: ${ADMIN_USERNAME:?ADMIN_USERNAME is required}
ADMIN_PASSWORD: ${ADMIN_PASSWORD:?ADMIN_PASSWORD is required}
ADMIN_EMAIL: ${ADMIN_EMAIL:?ADMIN_EMAIL is required}
```

> 部署前须在 `.env` 或部署平台中设置这三个变量的强密码值。

***

### H3. MySQL 连接禁用 SSL ✅ 已修复

**文件**: `application-prod.properties` :2, `docker-compose.yml` :69

**类别**: `sensitive_data_exposure` | **置信度**: 0.90

生产环境及 docker-compose 中均配置 `useSSL=false&allowPublicKeyRetrieval=true`，通信完全明文。

**修复**: 采用方案 A（内网信任自签名证书）：

```properties
# 修改后
spring.datasource.url=jdbc:mysql://mysql:3306/ai_chat_db?useSSL=true&requireSSL=true&verifyServerCertificate=false&serverTimezone=Asia/Shanghai
```

| 参数                              | 作用                  |
| ------------------------------- | ------------------- |
| `useSSL=true`                   | 启用 SSL 加密传输         |
| `requireSSL=true`               | 强制要求 SSL            |
| `verifyServerCertificate=false` | 内网 bridge 网络跳过证书链校验 |

> 已在 `application-prod.properties` 和 `docker-compose.yml` 的 `SPRING_DATASOURCE_URL` 同步更新。MySQL 8.0 容器默认生成自签名证书，无需额外配置。

***

### H4. FriendController 4 个写端点缺失 @Valid 注解 ✅ 已修复

**文件**: [`FriendController.java`](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/controller/FriendController.java) :30, :37, :45, :68

**类别**: `missing_validation` | **置信度**: 0.92

| 端点                          | 缺失校验                         |
| --------------------------- | ---------------------------- |
| `POST /api/friends/request` | `body.userId` 可能为 null       |
| `POST /api/friends/accept`  | `body.friendshipId` 可能为 null |
| `POST /api/friends/reject`  | `body.friendshipId` 可能为 null |
| `POST /api/friends/message` | `body.friendshipId` 可能为 null |

[FriendRequestDTO](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/dto/FriendRequestDTO.java) 中 `userId` 有 `@NotNull` 注解，但缺少 `@Valid` 导致注解完全不生效。

**修复**: 4 个方法的 `@RequestBody` 参数前添加 `@Valid`：

```java
// 修改前: @RequestBody FriendRequestDTO body
// 修改后: @Valid @RequestBody FriendRequestDTO body
```

***

### H5. 异常信息泄漏内部细节（3 处） ✅ 已修复

**文件**:

- [`FileController.java`](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/controller/FileController.java) :50-51
- [`ImageController.java`](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/controller/ImageController.java) :44
- [`KnowledgeBaseController.java`](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/controller/KnowledgeBaseController.java) :73

**类别**: `information_disclosure` | **置信度**: 0.88

`IOException.getMessage()` 可能包含服务器文件路径。KnowledgeBaseController 通过 `BusinessException` 抛出后，[GlobalExceptionHandler L31](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/config/GlobalExceptionHandler.java#L31) 将消息完整返回客户端。

**修复**: 返回固定文案，真实异常通过 `logger.error` 记录：

```java
// 修改前: error.put("error", "文件上传失败: " + e.getMessage());
// 修改后:
logger.error("文件上传失败", e);
error.put("error", "文件上传失败，请稍后重试");
```

> 三文件均已添加 `Logger` 声明和 `import`。

***

### H6. PromptsHubController 图片上传端点缺少 Content-Type 校验 ✅ 已修复

**文件**: [`PromptsHubController.java`](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/controller/PromptsHubController.java) :197-214, :216-227

**类别**: `file_upload` | **置信度**: 0.87

`uploadPromptWithImage` 和 `uploadImageForPrompt` 两个端点无任何 Controller 层 Content-Type 校验，仅依赖 Service 层下游 `IllegalArgumentException` 防御。

**修复**: 在 Controller 层添加 `image/` 前缀检查（参考 FileController 校验模式）：

```java
if (image != null && !image.isEmpty()) {
    String contentType = image.getContentType();
    if (contentType == null || !contentType.startsWith("image/")) {
        return ResponseEntity.badRequest().build();
    }
}
```

***

## 二、MEDIUM 级别问题（共 17 项，附解决方案）

### M1. CSP 允许 unsafe-inline 脚本

**文件**: [`SecurityConfig.java`](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/config/SecurityConfig.java) :65-68

`script-src 'self' 'unsafe-inline'` 削弱 XSS 防护。`connect-src 'self' https:` 过于宽松。

**方案**: 将内联脚本迁移为外部 `.js` 文件后，移除 `'unsafe-inline'`；`connect-src` 收敛为具体 API 域名列表。

***

### M2. AESUtil 短密钥零填充削弱加密强度【忽略】

**文件**: [`AESUtil.java`](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/util/AESUtil.java) :61-64

`reloadKey` 对非 16 字节密钥静默零填充/截断。

**方案**: 在 `reloadKey` 中改为：若 `keyBytes.length != 16`，直接抛出 `IllegalArgumentException("ENCRYPTION_KEY 必须为 16 个字符")`，拒绝运行而非静默弱化。

***

### M3. JwtAuthenticationFilter 角色声明无白名单

**文件**: [`JwtAuthenticationFilter.java`](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/config/JwtAuthenticationFilter.java) :61-62, :74-75

`new SimpleGrantedAuthority("ROLE_" + role)` 无白名单校验。防御深度缺失。

**方案**: 在 L62 后添加白名单检查：

```java
if (!List.of("USER", "ADMIN").contains(role)) {
    throw new BadCredentialsException("非法角色");
}
```

***

### M4. AdminConversationController 管理员读聊天无审计日志

**文件**: [`AdminConversationController.java`](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/controller/admin/AdminConversationController.java) :30-34

**方案**: 在 `getMessages` 方法中注入 `AdminAuditLogService`，记录 `adminId`、`conversationId`、操作时间、IP。

***

### M5. PromptService.getPromptById 跨用户读取提示词

**文件**: [`PromptService.java`](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/service/PromptService.java) :71-75

[MessageContextBuilder L91](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/service/MessageContextBuilder.java#L91) 使用用户提供的 promptId 直接加载，未校验所有权。

**方案**: 在 `MessageContextBuilder.buildMessagesArray` 中加入所有权校验，或在 `PromptService.getPromptById` 添加 `userId` 参数并校验 `prompt.getUser().getId().equals(userId)`。

***

### M6. API Key 前 6 位出现于 debug 日志

**文件**: [`ChatStreamService.java`](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/service/ChatStreamService.java) :156-159

**方案**: 将 `apiKey.substring(0, Math.min(6, apiKey.length()))` 替换为 `(apiKey != null ? "***" : "null")`，仅记录是否已配置。

***

### M7. GlobalExceptionHandler 缺少 AccessDeniedException 处理

**文件**: [`GlobalExceptionHandler.java`](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/config/GlobalExceptionHandler.java)

**方案**: 添加 handler 返回 403：

```java
@ExceptionHandler(AccessDeniedException.class)
public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException e) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(Map.of("success", false, "message", "权限不足"));
}
```

***

### M8. MySQL FULLTEXT 布尔模式未转义用户输入

**文件**: [`PromptsHubRepository.java`](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/repository/PromptsHubRepository.java) :47-56

`AGAINST(:q IN BOOLEAN MODE)` 中 `q` 未经转义。

**方案**: 在 `PromptsHubService.search` 方法调用 Repository 前，对用户输入做转义——移除 `+` `-` `*` `(` `)` `"` `@` 等布尔运算符字符（或参考 `escapeFulltext` 方法逻辑）。

***

### M9. 密码策略不一致（注册 8 位 vs 重置 6 位）

**文件**: `RegisterRequest.java` :19 vs `ResetPasswordRequest.java` :19

**方案**: 将 `ResetPasswordRequest.newPassword` 的 `@Size(min = 6)` 改为 `@Size(min = 8)`，与注册和修改密码接口统一。

***

### M10. MySQL 使用 root 账户【忽略】

**文件**: `application-dev.properties` :3, `application-prod.properties` :3

**方案**: 在 `docker-compose.yml` 的 MySQL 初始化脚本中创建专用用户：

```sql
CREATE USER 'aichat'@'%' IDENTIFIED BY '${DB_PASSWORD}';
GRANT SELECT, INSERT, UPDATE, DELETE ON ai_chat_db.* TO 'aichat'@'%';
```

然后将 `spring.datasource.username` 改为 `aichat`。

***

### M11. PromptsHubController 提示词字段无长度限制（4 处）

**文件**: [`PromptsHubController.java`](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/controller/PromptsHubController.java) :137, :156, :178, :68

**方案**: 在 `upload`、`create`、`update`、`search` 方法中添加长度校验，例如 `content` 限制 10000 字符、`description` 限制 500 字符、`q` 限制 200 字符。

***

### M12. AdminModelConfigController 使用实体作为请求体

**文件**: [`AdminModelConfigController.java`](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/controller/admin/AdminModelConfigController.java) :38, :49

JPA 实体 `ModelConfig` 直接作为 `@RequestBody`，存在 Mass Assignment 风险。

**方案**: 创建专用的 `ModelConfigCreateRequest` DTO，仅在接收客户端输入时暴露 `apiKey`、`apiUrl`、`modelName`、`displayName`、`inputTokenPrice`、`outputTokenPrice` 字段，Controller 中将 DTO 映射到实体。

***

### M13. AdminUserController 余额修改无边界检查

**文件**: [`AdminUserController.java`](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/controller/admin/AdminUserController.java) :47-64

**方案**: 添加金额范围校验：

```java
if (amount.compareTo(new BigDecimal("-100000")) < 0 || amount.compareTo(new BigDecimal("100000")) > 0) {
    return ResponseEntity.badRequest().body(Map.of("error", "金额超出允许范围"));
}
```

***

### M14. AdminSponsorController Token 发放无上限

**文件**: [`AdminSponsorController.java`](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/controller/admin/AdminSponsorController.java) :41

**方案**: 对 `tokens` 字符串做 try-catch + 上限校验：

```java
BigDecimal tokens;
try {
    tokens = new BigDecimal(body.get("tokens").toString());
} catch (NumberFormatException e) {
    return ResponseEntity.badRequest().body(Map.of("error", "金额格式无效"));
}
if (tokens.compareTo(new BigDecimal("1000000")) > 0 || tokens.compareTo(BigDecimal.ZERO) <= 0) {
    return ResponseEntity.badRequest().body(Map.of("error", "金额超出范围"));
}
```

***

### M15. UpdateProfileRequest signature 无长度限制

**文件**: [`UpdateProfileRequest.java`](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/dto/UpdateProfileRequest.java) :3-7

**方案**: 添加 `@Size(max = 200)`（与 User 实体 `@Column(length = 200)` 一致）。

***

### M16. SendCodeRequest 缺少 @Size 限制

**文件**: [`SendCodeRequest.java`](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/dto/SendCodeRequest.java) :7-8

**方案**: 添加 `@Size(max = 100)`。

***

### M17. 速率限制回退到 IP 可被代理池绕过

**文件**: [`RateLimitInterceptor.java`](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/config/RateLimitInterceptor.java) :279-285

**方案**: 对于关键端点（登录、注册），结合 User-Agent + IP 组合作为标识，增加绕过成本。

***

### M18. 前端 Token 存储于 localStorage/sessionStorage

**文件**: [`frontend/src/lib/api.ts`](file:///c:/Users/makot/Desktop/aichat/frontend/src/lib/api.ts) :1-37

**方案**:

- 短期：修复 `setToken` 双写策略，仅在不勾选"记住我"时写入 sessionStorage
- 长期：改用 httpOnly Secure Cookie 由后端 Set-Cookie，或引入 BFF 模式代理 Token

***

### M19. 前端文件上传缺少客户端校验

涉及 `KBModal.tsx`、`WalletModal.tsx`、`ProfileModal.tsx`、`InputBar.tsx`、`services.ts`

**方案**: 在 `services.ts` 的 `uploadImage`/`uploadFile` 中添加统一的校验函数：

```typescript
function validateFile(file: File, maxSizeMB: number, allowedTypes: string[]) {
  if (file.size > maxSizeMB * 1024 * 1024) throw new Error('文件过大');
  if (!allowedTypes.some(t => file.type.startsWith(t))) throw new Error('不支持的文件类型');
}
```

***

### M20. AI 提示词注入风险（6 处架构级别）

涉及 `SummaryService`、`MemoryService`、`EntityRetrievalService`、`GraphMemoryService`、`QueryRewriterService`、`MessageContextBuilder`

**方案**:

1. 将 `MemoryService.sanitizeMemoryValue` 提取为公共工具方法 `sanitizePromptInput`
2. 在所有 LLM prompt 拼接处统一调用该方法
3. AI 回复也进行注入模式过滤后再注入摘要/记忆 prompt
4. 优先使用结构化消息格式（role 分离），用户数据放在 `user` role 中

***

## 三、LOW 级别问题（共 24 项）

| #   | 问题                                           | 位置                                                                                                                                             | 方案                                      |
| --- | -------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------- |
| L1  | ResetPasswordRequest 缺 @Email                | [`ResetPasswordRequest.java`](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/dto/ResetPasswordRequest.java) :13        | 添加 `@Email`                             |
| L2  | RegisterRequest 验证码字段无 @NotBlank             | [`RegisterRequest.java`](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/dto/RegisterRequest.java) :22                  | 添加 `@NotBlank`                          |
| L3  | FriendRequestDTO content 无长度限制               | [`FriendRequestDTO.java`](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/dto/FriendRequestDTO.java) :9                 | 添加 `@Size(max = 500)`                   |
| L4  | docker-compose MySQL 端口暴露宿主机                 | [`docker-compose.yml`](file:///c:/Users/makot/Desktop/aichat/docker-compose.yml) :30-31                                                        | 如无需外部访问则注释 `ports`                      |
| L5  | /admin 前端路由公开可访问                             | [`SecurityConfig.java`](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/config/SecurityConfig.java) :36-37              | 如不必要可考虑添加 IP 白名单                        |
| L6  | BillingService 使用 java.util.Random           | [`BillingService.java`](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/service/BillingService.java) :227               | 改用 `ThreadLocalRandom` 或 `SecureRandom` |
| L7  | AdminService sortBy 未白名单校验                   | [`AdminService.java`](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/service/AdminService.java) :120                   | 添加 `ALLOWED_SORT_FIELDS` 白名单            |
| L8  | ApiKeyEncryptor 前缀暴露算法信息                     | [`ApiKeyEncryptor.java`](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/config/ApiKeyEncryptor.java) :17               | 将 `"AES:"` 改为无意义标识符                     |
| L9  | ApiKeyEncryptor 解密失败返回密文原文                   | [`ApiKeyEncryptor.java`](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/config/ApiKeyEncryptor.java) :38               | 密钥变更时返回占位符或抛出明确异常                       |
| L10 | ApiKeyEncryptor 历史明文仅警告不自动加密                 | [`ApiKeyEncryptor.java`](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/config/ApiKeyEncryptor.java) :41-42            | 已有 AichatApplication 迁移脚本，确认执行后可删除      |
| L11 | 前端大量 console.warn 暴露错误详情（13 处）               | `App.tsx`, `ErrorBoundary.tsx`, 多个 hook                                                                                                        | 生产构建时通过 Vite 配置移除 console               |
| L12 | 前端使用 alert() 阻塞 UI（2 处）                      | `App.tsx` :328, `WalletModal.tsx` :167                                                                                                         | 替换为 Toast 通知组件                          |
| L13 | TypeScript \~6.0.2 / Vite ^8.1.0 版本异常        | [`package.json`](file:///c:/Users/makot/Desktop/aichat/frontend/package.json) :40-41                                                           | 验证是否为稳定版，运行 `npm audit`                 |
| L14 | API 层 JSON.parse 无异常处理                       | [`frontend/src/lib/api.ts`](file:///c:/Users/makot/Desktop/aichat/frontend/src/lib/api.ts) :94-96                                              | 添加 try-catch 返回统一错误格式                   |
| L15 | JwtProperties.expiration 无单位说明               | [`JwtProperties.java`](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/config/props/JwtProperties.java) :18             | 字段名改为 `expirationMs` 或加注释               |
| L16 | SSE 解析 JSON 失败静默传递原始数据                       | [`frontend/src/lib/api.ts`](file:///c:/Users/makot/Desktop/aichat/frontend/src/lib/api.ts) :171-176                                            | 解析失败时跳过该帧而非 yield 原始数据                  |
| L17 | TokenBlacklist DB 回退无缓存保护                    | [`TokenBlacklist.java`](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/config/TokenBlacklist.java) :63                 | 添加空值缓存或布隆过滤器                            |
| L18 | RateLimitInterceptor 规则硬编码                   | [`RateLimitInterceptor.java`](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/config/RateLimitInterceptor.java) :38-215 | 改为配置文件驱动                                |
| L19 | 密码修改接口缺速率限制                                  | RateLimitInterceptor                                                                                                                           | 注册 `change-password` 规则（5次/分钟）          |
| L20 | Vite emptyOutDir: false                      | [`frontend/vite.config.ts`](file:///c:/Users/makot/Desktop/aichat/frontend/vite.config.ts) :54-57                                              | 改为 `true` 或添加清理脚本                       |
| L21 | setToken 双写绕过"不记住我"                          | [`frontend/src/lib/api.ts`](file:///c:/Users/makot/Desktop/aichat/frontend/src/lib/api.ts) :26-31                                              | 仅在不勾选时写入 sessionStorage                 |
| L22 | ConversationService.deleteConversation 缺防御深度 | Service 方法无归属校验（但 ChatController 已做）                                                                                                           | 添加 userId 参数到 Service 方法                |
| L23 | ChatHistoryService.saveMessage 缺防御深度         | Service 方法无归属校验（但 Controller 已做）                                                                                                               | 添加 userId 参数到 Service 方法                |
| L24 | AuthController.updateProfile 缺 @Valid        | Controller 已有手动校验                                                                                                                              | 添加 `@Valid` 做声明式校验                      |

***

## 四、良好安全实践（17 项）

| #  | 实践                                                                           | 位置                                                                           |
| -- | ---------------------------------------------------------------------------- | ---------------------------------------------------------------------------- |
| 1  | SSRF 防护：`NetworkUtils.validateExternalUrl` 检查 loopback/link-local/site-local | `NetworkUtils.java`                                                          |
| 2  | 图片 URL 白名单校验防 tool\_calls 注入                                                 | `AnalyzeImageTool.java` :103-117                                             |
| 3  | 路径穿越防护：`Paths.get().getFileName()` + UUID                                    | `KnowledgeBaseService.java` :174-176                                         |
| 4  | 密码强度：8+ 字符、大小写字母和数字                                                          | `UserService.java` :147-160                                                  |
| 5  | 用户枚举防护：resetCode 不存在时静默返回                                                    | `UserService.java` :203-206                                                  |
| 6  | 验证码 TOCTOU 防护：`ConcurrentHashMap.compute()`                                  | `EmailService.java` :56-62                                                   |
| 7  | 验证码爆破防护：5 次错误自动失效                                                            | `EmailService.java` :95-102                                                  |
| 8  | JPQL 全部命名参数绑定，零字符串拼接                                                         | 全部 Repository                                                                |
| 9  | AES-128-GCM：128 位认证标签，12 字节随机 IV                                             | `AESUtil.java`                                                               |
| 10 | JWT 强制签名验证（`parseSignedClaims`）                                              | `JwtUtil.java` :87-91                                                        |
| 11 | ModelConfig.apiKey AES 加密存储                                                  | `ApiKeyEncryptor.java`                                                       |
| 12 | User.password `@JsonProperty(access = WRITE_ONLY)`                           | `User.java` :31                                                              |
| 13 | 密码字段 type="password"，值用 refs 持有                                              | `ProfileModal.tsx` :39-44                                                    |
| 14 | Docker 非 root 用户（appuser）                                                    | `Dockerfile` :24                                                             |
| 15 | Lucene/FULLTEXT 查询转义                                                         | `Bm25IndexService.java`, `KbBm25IndexService.java`, `PromptsHubService.java` |
| 16 | 安全响应头：HSTS/CSP/X-Frame-Options/X-Content-Type-Options                        | `SecurityConfig.java` :53-73                                                 |
| 17 | ENCRYPTION\_KEY 启动强制非空检查                                                     | `AppConfig.java` :35-37                                                      |

***

## 五、未发现问题的领域（高可信度）

- **SQL/JPQL 注入** — 所有查询参数化，零发现
- **OS 命令注入** — 无 `Runtime.exec()` / `ProcessBuilder`
- **XXE** — 无 XML 解析器
- **不安全反序列化** — 无 `ObjectInputStream` / `pickle`
- **XSS** — 前端无 `dangerouslySetInnerHTML`，React 默认转义
- **CSRF** — JWT Bearer Token 模式下合理禁用
- **开放重定向** — 全部硬编码路径
- **eval/exec** — 无动态代码执行

***

## 六、修复优先级

### P0（立即修复 · 已完成 ✅）

| #  | 问题                                         | 状态 |
| -- | ------------------------------------------ | -- |
| H1 | CORS 通配符收敛为白名单                             | ✅  |
| H2 | 移除管理员默认密码                                  | ✅  |
| H3 | MySQL 启用 SSL                               | ✅  |
| H4 | FriendController 4 端点加 @Valid              | ✅  |
| H5 | 异常消息脱敏（3 处）                                | ✅  |
| H6 | PromptsHubController 图片上传加 Content-Type 校验 | ✅  |

### P1（本周修复 · 附方案）

| #       | 问题                    | 工作量     |
| ------- | --------------------- | ------- |
| M1      | CSP 移除 unsafe-inline  | 中       |
| M5      | PromptService 添加所有权校验 | 小       |
| M6      | API Key 日志脱敏          | 小（1行改动） |
| M8      | FULLTEXT 布尔运算符转义      | 小       |
| M9      | 密码策略统一为 8 位           | 小（1行改动） |
| M10     | MySQL 创建专用用户          | 中       |
| M15-M16 | DTO 补充 @Size 校验       | 小       |

### P2（下迭代 · 附方案）

| #      | 问题                       | 工作量 |
| ------ | ------------------------ | --- |
| M2     | AESUtil 拒绝短密钥            | 小   |
| M3     | JWT 角色白名单                | 小   |
| M4     | Admin 读聊天审计日志            | 中   |
| M7     | AccessDeniedException 处理 | 小   |
| M11    | PromptsHub 字段长度限制        | 小   |
| M12    | ModelConfig 专用 DTO       | 中   |
| M13    | 余额边界检查                   | 小   |
| M14    | Token 发放上限               | 小   |
| M17    | IP 限流增强                  | 中   |
| M18    | Token 存储方案优化             | 大   |
| M19    | 前端上传校验                   | 小   |
| M20    | AI 注入统一防护                | 大   |
| L1-L24 | 全部 LOW 项                 | 按需  |

***

*报告由自动化审查工具生成并经人工复核。HIGH 级问题已于 2026-07-24 全部修复并通过编译验证。MEDIUM 及 LOW 问题均已提供具体修复方案。*
