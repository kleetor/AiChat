# AI Chat 核心服务流文档

> **最后更新**: 2026-07-26  
> **覆盖范围**: 安全体系、认证用户、计费、核心聊天流、上下文注入、工具系统、管理后台、功能组件、基础设施  
> **核心文件**: 60+ Java 文件 + 10+ 前端文件

---

## 目录

1. [架构总览](#1-架构总览)
2. [安全体系](#2-安全体系)
   - 2.1 SecurityConfig — Spring Security 配置
   - 2.2 JwtUtil — JWT 令牌管理
   - 2.3 JwtAuthenticationFilter — JWT 认证过滤器
   - 2.4 AESUtil — AES-128-GCM 加密
   - 2.5 ApiKeyEncryptor — API Key 透明加密
   - 2.6 TokenBlacklist — 登出黑名单
   - 2.7 RateLimitInterceptor — 速率限制
   - 2.8 NetworkUtils — SSRF 防护
   - 2.9 GlobalExceptionHandler — 全局异常
3. [认证与用户体系](#3-认证与用户体系)
   - 3.1 AuthController — 认证 API
   - 3.2 UserService — 用户注册/登录
   - 3.3 EmailService — 邮箱验证码
4. [计费系统](#4-计费系统)
   - 4.1 BillingService — 余额/扣费
   - 4.2 BillingController — 充值 API
5. [核心聊天流](#5-核心聊天流)
6. [上下文注入管线](#6-上下文注入管线)
7. [工具调用系统](#7-工具调用系统)
8. [管理后台体系](#8-管理后台体系)
9. [功能组件](#9-功能组件)
10. [基础设施](#10-基础设施)
11. [关键配置与常量](#11-关键配置与常量)
12. [附录](#12-附录)
13. [外部依赖清单](#13-外部依赖清单)
14. [前后端接口契约](#14-前后端接口契约)
15. [QPS 预测与资源需求](#15-qps-预测与资源需求)

---

## 1. 架构总览

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                               AI Chat 系统架构                                 │
└──────────────────────────────────────────────────────────────────────────────┘

                             ┌─────────────────────┐
                             │    React Frontend    │
                             │  (Vite + TypeScript) │
                             └──────────┬──────────┘
                                        │ HTTP/SSE
                                        ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│                           Spring Boot Backend                                  │
│                                                                                │
│  ┌──────────┐  ┌──────────┐  ┌──────────────┐  ┌─────────────────────┐       │
│  │ Security │  │  Auth /  │  │   Billing    │  │   Admin Panel       │       │
│  │  Layer   │  │  User    │  │   Service    │  │  (Controllers +     │       │
│  │ (JWT/BCrypt/│  │ Service │  │              │  │   AuditLog)         │       │
│  │  RateLim)│  └──────────┘  └──────────────┘  └─────────────────────┘        │
│  └──────────┘                                                                  │
│                                                                                │
│  ┌──────────────────────────────────────┐  ┌──────────────────────────────┐   │
│  │          Core Chat Pipeline          │  │       Functional Modules      │   │
│  │                                      │  │                              │   │
│  │ ChatController → ChatService         │  │ PromptsHub / Comments / Likes│   │
│  │   → MessageContextBuilder            │  │ Friends / Follow             │   │
│  │   → ChatStreamService (SSE)          │  │ Notifications                │   │
│  │   → ChatPostProcessor                │  │ Knowledge Base (RAG)         │   │
│  │   → ToolRegistry (Function Calling)  │  │ Memory System + Graph        │   │
│  │   → ChatHistoryService               │  │                              │   │
│  └──────────────────────────────────────┘  └──────────────────────────────┘   │
│                                                                                │
│  ┌────────────────────────────────────────────────────────────────────────┐   │
│  │                       Infrastructure Layer                              │   │
│  │                                                                         │   │
│  │ LLMService  │ ChromaDB  │ SiliconFlowEmbedding │ SiliconFlowRerank      │   │
│  │ ImageService│ S3/MinIO FileStore              │ EmailService           │   │
│  └────────────────────────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. 安全体系

### 2.1 SecurityConfig — Spring Security 配置

**文件**: [`SecurityConfig.java`](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/config/SecurityConfig.java)

#### 认证与授权策略

```java
// 公开端点 (无需认证)
.requestMatchers("/api/auth/send-code", "/api/auth/register", "/api/auth/login",
    "/api/auth/send-reset-code", "/api/auth/reset-password").permitAll()
.requestMatchers("/api/admin/login").permitAll()

// 管理员端点 (ROLE_ADMIN)
.requestMatchers("/api/admin/**").hasAuthority("ROLE_ADMIN")

// 其余全部需认证
.anyRequest().authenticated()
```

#### 安全响应头

| 响应头 | 作用 | 值 |
|--------|------|-----|
| `Content-Security-Policy` | XSS 防护 | `default-src 'self'; script-src 'self' cdn...; connect-src 'self' https:` |
| `Strict-Transport-Security` | 强制 HTTPS | `max-age=31536000; includeSubDomains` |
| `X-Frame-Options` | 点击劫持 | `DENY` |
| `X-Content-Type-Options` | MIME 嗅探 | `nosniff` |

#### 密码加密

```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```

使用 BCrypt 单向哈希，自动加盐。所有密码在入库前经 `passwordEncoder.encode()` 处理。

---

### 2.2 JwtUtil — JWT 令牌管理

**文件**: [`JwtUtil.java`](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/util/JwtUtil.java)

#### 密钥策略

```
原始密钥 (来自 JWT_SECRET 环境变量)
    │
    ├── < 32 字节 → SHA-256 哈希扩展至 32 字节 (并输出 WARN)
    └── ≥ 32 字节 → 直接使用
            │
            ▼
    Keys.hmacShaKeyFor(keyBytes)  // HMAC-SHA256
```

#### 令牌生成

```java
Jwts.builder()
    .subject(username)
    .claim("userId", userId)
    .claim("role", role)      // "USER" | "ADMIN"
    .issuedAt(now)
    .expiration(now + jwtProperties.expiration)
    .signWith(getSigningKey())
    .compact();
```

#### 令牌校验

```java
// 显式签名验证 — 不接受 alg=none
Jwts.parser()
    .verifyWith(getSigningKey())     // 强制 HMAC-SHA256
    .build()
    .parseSignedClaims(token)        // 拒绝未签名令牌
    .getPayload();
```

---

### 2.3 JwtAuthenticationFilter — JWT 认证过滤器

**文件**: [`JwtAuthenticationFilter.java`](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/config/JwtAuthenticationFilter.java)

**执行顺序**: Spring Security Filter Chain 中，插入在 `UsernamePasswordAuthenticationFilter` 之前

```
每个请求到达
    │
    ▼
1. 提取 Authorization header → "Bearer <token>"
    │
    ▼
2. jwtUtil.validateToken(token)
    │
    ├── 无效 → 放行 (交 Security 处理，返回 401)
    └── 有效 → 继续
            │
            ▼
3. 组装 Authentication
    │
    ├── userId: jwtUtil.getUserIdFromToken(token)
    ├── username: jwtUtil.getUsernameFromToken(token)
    ├── role: jwtUtil.getRoleFromToken(token)
    │   └── 白名单校验: List.of("USER","ADMIN").contains(role) ✅ 已修复
    └── SimpleGrantedAuthority("ROLE_" + role)
            │
            ▼
4. tokenBlacklist.isBlacklisted(token)
    │
    ├── 已拉黑 → 返回 401 (已登出)
    └── 未拉黑 → SecurityContextHolder.getContext().setAuthentication(auth)
```

---

### 2.4 AESUtil — AES-128-GCM 加密

**文件**: [`AESUtil.java`](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/util/AESUtil.java)

#### 算法参数

| 参数 | 值 | 说明 |
|------|-----|------|
| 算法 | AES/GCM/NoPadding | AEAD 认证加密 |
| IV 长度 | 12 字节 (96 bits) | NIST 推荐 |
| 认证标签 | 128 bits | 防篡改 |
| 随机源 | SecureRandom | JVM 级 CSPRNG |
| 密钥长度 | 128 bits (16 字符) | AES-128 |

#### 加密格式

```
密文 = Base64( IV(12 bytes) || ciphertext )

解密时:
  1. Base64 解码
  2. 提取前 12 bytes → IV
  3. 剩余 → ciphertext
  4. Cipher.doFinal() — 自动验证 GCM 认证标签
```

#### 初始化流程

```java
// AppConfig.java 启动时调用
AESUtil.setKey(encryptionKey);  // 从 application.properties / .env 加载

// 未初始化时加密/解密会抛出 IllegalStateException
// 防止配置遗漏导致使用弱密钥
```

---

### 2.5 ApiKeyEncryptor — API Key 透明加密

**文件**: [`ApiKeyEncryptor.java`](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/config/ApiKeyEncryptor.java)

JPA `AttributeConverter`，自动拦截 `ModelConfig.apiKey` 字段的读写。

```
写入 DB 时:
  plainApiKey → AESUtil.encrypt() → "ENC:<base64>" → DB

读取 DB 时:
  DB → 检查 "ENC:" 前缀
    ├── 有 → AESUtil.decrypt() → 返回明文
    │   └── 解密失败 → 返回 null (已修复，原为返回密文)
    └── 无 → 兼容历史明文数据 → WARN → 返回原文
```

---

### 2.6 TokenBlacklist — 登出黑名单

**文件**: [`TokenBlacklist.java`](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/config/TokenBlacklist.java)

JWT 无状态特性下实现"登出失效"的双层方案：

```
用户登出
    │
    ▼
blacklist(token)
    ├── SHA-256 哈希 token → hash
    ├── Caffeine 缓存: hash → Boolean.TRUE (TTL = JWT 过期时间)
    └── MySQL 持久化: token_blacklist 表 (防重启丢失)

每个请求到达
    │
    ▼
isBlacklisted(token)
    ├── Caffeine.getIfPresent(hash)
    │   ├── Boolean.TRUE → 已拉黑
    │   ├── Boolean.FALSE → 未拉黑 (已缓存阴性结果)
    │   └── null → 缓存未命中
    │       ├── DB 查询
    │       ├── 未在 DB → 缓存 Boolean.FALSE
    │       └── 在 DB → 返回 true
    │
    └── 定时清理: 每小时删除过期黑名单记录
```

---

### 2.7 RateLimitInterceptor — 速率限制

**文件**: [`RateLimitInterceptor.java`](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/config/RateLimitInterceptor.java)

#### 架构

```
RateLimitInterceptor (HandlerInterceptor)
    │
    ├── Caffeine 缓存: max 5000 条目
    ├── 滑动窗口: Bucket { windowStart, count }
    └── 用户标识: UserPrincipal → IP 回退

每条请求:
    ├── 匹配第一个命中规则 → 检查计数
    │   ├── 超限 → 429 + JSON error + X-RateLimit-* 响应头
    │   └── 未超限 → count++
    └── 无匹配规则 → 放行
```

#### 所有速率限制规则 (24 条)

| 规则 ID | 端点 | 限制 | 窗口 |
|---------|------|------|------|
| `auth-login` | POST /api/auth/login | 5 次 | 1 分钟 |
| `admin-login` | POST /api/admin/login | 3 次 | 1 分钟 |
| `auth-register` | POST /api/auth/register | 3 次 | 1 分钟 |
| `auth-send-code` | POST /api/auth/send-code | 1 次 | 1 分钟 |
| `auth-send-reset-code` | POST /api/auth/send-reset-code | 1 次 | 1 分钟 |
| `auth-reset-password` | POST /api/auth/reset-password | 5 次 | 1 分钟 |
| `auth-change-password` | POST /api/auth/change-password | 5 次 | 1 分钟 |
| `auth-upload-avatar` | POST /api/auth/upload-avatar | 3 次 | 1 天 |
| `chat` | POST /api/chat/* | 30 次 | 1 分钟 |
| `billing-sponsor` | POST /api/billing/sponsor-* | 3 次 | 1 天 |
| `prompt-comment` | POST /api/prompts-hub/:id/comments | 5 次 | 1 分钟 |
| `prompt-upload` | POST /api/prompts-hub/upload | 20 次 | 1 天 |
| `prompt-like` | POST /api/prompts-hub/:id/(like\|dislike) | 10 次 | 1 分钟 |
| `prompt-image-update` | POST /api/prompts-hub/:id/image | 10 次 | 1 天 |
| `image-upload` | POST /api/image/upload | 30 次 | 1 天 |
| `file-upload` | POST /api/file/upload | 30 次 | 1 天 |
| `kb-docs-upload` | POST /api/kb/:id/docs/upload | 50 次 | 1 天 |
| `kb-docs-reindex` | POST /api/kb/docs/:id/reindex | 10 次 | 1 天 |
| `memory-add` | POST /api/memory/add | 30 次 | 1 天 |
| `memory-search` | POST /api/memory/search | 30 次 | 1 分钟 |
| `memory-clear` | DELETE /api/memory/clear | 3 次 | 1 天 |

---

### 2.8 NetworkUtils — SSRF 防护

**文件**: [`NetworkUtils.java`](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/util/NetworkUtils.java)

对外发起的 HTTP 请求（LLM API、搜索 API）都经过此校验：

```java
validateExternalUrl(apiUrl)
    │
    ├── 协议检查: 仅 http/https
    ├── DNS 解析: InetAddress.getByName(host)
    ├── isLoopbackAddress()    → 127.0.0.1, ::1
    ├── isLinkLocalAddress()   → 169.254.x.x
    ├── isSiteLocalAddress()   → 10.x, 172.16.x, 192.168.x
    ├── isAnyLocalAddress()    → 0.0.0.0
    ├── 0.0.0.0/8              → 拒绝
    └── 127.0.0.0/8            → 拒绝
```

---

### 2.9 GlobalExceptionHandler — 全局异常处理

**文件**: [`GlobalExceptionHandler.java`](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/config/GlobalExceptionHandler.java)

`@RestControllerAdvice` 统一拦截异常，避免内部信息泄漏：

| 异常类型 | HTTP 状态 | 返回内容 |
|----------|----------|---------|
| `BusinessException` | 400/404/409/403 | `{success:false, message: ...}` |
| `MethodArgumentNotValidException` | 400 | 按字段汇总校验错误 |
| `InsufficientBalanceException` | 402 | `{success:false, message: ...}` |
| `AccessDeniedException` | 403 | `{success:false, message:"权限不足"}` |
| `AsyncRequestTimeoutException` | 200 | SSE 长连接超时静默关闭 |
| 其他 Exception | 500 | `{success:false, message:"服务器内部错误"}` |

---

## 3. 认证与用户体系

### 3.1 AuthController — 认证 API

**文件**: [`AuthController.java`](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/controller/AuthController.java)

| 端点 | 方法 | 说明 | 速率限制 |
|------|------|------|---------|
| `/api/auth/register` | POST | 注册 | 3次/分钟 |
| `/api/auth/login` | POST | 登录 | 5次/分钟 |
| `/api/auth/send-code` | POST | 发送注册验证码 | 1次/分钟 |
| `/api/auth/send-reset-code` | POST | 发送重置验证码 | 1次/分钟 |
| `/api/auth/reset-password` | POST | 重置密码 | 5次/分钟 |
| `/api/auth/change-password` | POST | 修改密码 | 5次/分钟 |
| `/api/auth/update-profile` | POST | 更新签名 | - |
| `/api/auth/upload-avatar` | POST | 上传头像 | 3次/天 |
| `/api/auth/profile/{id}` | GET | 查看用户信息 | - |

### 3.2 UserService — 用户注册/登录

**文件**: [`UserService.java`](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/service/UserService.java)

#### 注册流程

```
register(request)
    │
    ├── 用户名校验: 3-20 个字符，字母/数字/中文/下划线
    ├── 唯一性检查: userRepository.existsByUsername()
    ├── 邮箱校验: emailService.verifyCode(email, code)
    │   └── TOCTOU 防护: ConcurrentHashMap.compute() 原子操作
    │   └── 5 次错误自动失效
    ├── 密码校验: 8+ 字符，包含大写、小写、数字
    │   └── passwordEncoder.encode() → BCrypt 哈希入库
    ├── 生成 PID: 6 位不重复数字
    └── 保存 → 生成 JWT → 返回 AuthResponse
```

#### 登录流程

```
login(request)
    │
    ├── 用户名查找 → userRepository.findByUsername()
    │   └── 不存在 → "用户名或密码错误" (防用户枚举)
    ├── 口令比对: passwordEncoder.matches(plain, hash)
    │   └── 不匹配 → "用户名或密码错误"
    ├── 账户状态: enabled == false → 403 "账号已被禁用"
    └── 生成 JWT → 返回 AuthResponse {token, userId, username, ...}
```

#### 安全特性

- **密码强度**: 8+ 字符，大写+小写+数字 三者至少各一个
- **用户枚举防护**: 登录失败不区分"用户不存在"和"密码错误"
- **重置密码枚举防护**: resetCode 不存在时静默返回成功
- **邮箱脱敏**: `maskEmail("user@example.com")` → `"u***@example.com"`

### 3.3 EmailService — 邮箱验证码

**文件**: [`EmailService.java`](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/service/EmailService.java)

| 配置项 | 值 | 说明 |
|--------|-----|------|
| 存储结构 | `ConcurrentHashMap<email, EmailCode>` | 内存存储 |
| 验证码格式 | 6 位数字 | `ThreadLocalRandom` |
| 发送间隔 | 60 秒 | `compute()` 原子检查防并发 |
| 验证码有效期 | 5 分钟 | `createTime` 比对 |
| 最大验证次数 | 5 次 | 防爆破，超限自动失效 |
| 一次性使用 | `used = true` | 使用后标记 |

```
sendVerificationCode(email)
    │
    ▼
codeMap.compute(email, (k, existing) -> {
    if (existing != null && now - existing.createTime < 60000)
        throw "请稍后再试"       ← TOCTOU 防护
    return new EmailCode(generateCode())
})
    │
    ▼
mailSender.send(SimpleMailMessage)  → 异步发送
```

---

## 4. 计费系统

### 4.1 BillingService — 余额/扣费

**文件**: [`BillingService.java`](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/service/BillingService.java)

#### 计费流程

```
┌──────────────────────────────────────────────────────────────────┐
│                    计费三阶段模型                                   │
└──────────────────────────────────────────────────────────────────┘

阶段 1: 预扣 (checkAndReserveBalance)
    聊天开始前执行
    │
    ├── 估算费用: estimatedCost = (inputTokens * inputPrice + outputTokens * outputPrice) * SAFETY_MULTIPLIER(2.0)
    ├── 余额检查: user.balance >= estimatedCost
    ├── 从 balance 转入 reservedBalance
    └── 失败 → InsufficientBalanceException → 402

阶段 2: 实扣 (deductTokens)
    聊天完成后执行
    │
    ├── 释放预留: user.reservedBalance -= preEstimated
    ├── 计算实际: actualCost = inputTokens * inputPrice + outputTokens * outputPrice
    ├── 实际扣除: user.balance -= actualCost
    ├── 记录用量: token_usages 表 (inputTokens, outputTokens, costAmount, modelConfigId)
    ├── 更新用户统计: totalTokens++, 更新 lastActiveAt, 更新 monthlyTokens
    ├── 悲观锁: @Lock(PESSIMISTIC_WRITE) → userRepository.findByIdWithLock()
    └── 缓存失效: @CacheEvict("billingBalance")

阶段 3: 释放预留 (releaseReservedBalance)
    聊天异常时执行
    │
    └── user.reservedBalance -= preEstimated
        user.balance += preEstimated
```

#### 充值订单

```
用户充值
    │
    ├── POST /api/billing/create-order → RechargeOrder (PENDING)
    ├── POST /api/billing/sponsor-submit → 赞助审核 (PENDING)
    ├── POST /api/billing/sponsor-query → 查询状态
    └── 管理员审核 → APPROVED / REJECTED
            │
            ▼ (APPROVED)
        ├── user.balance += order.amount
        ├── recharge_orders.status = "APPROVED"
        └── 审计日志
```

### 4.2 BillingController — 充值 API

**文件**: [`BillingController.java`](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/controller/BillingController.java)

| 端点 | 说明 |
|------|------|
| `POST /api/billing/create-order` | 创建充值订单 |
| `POST /api/billing/sponsor-submit` | 赞助审核提交 |
| `POST /api/billing/sponsor-query` | 查询审核结果 |
| `GET /api/billing/balance` | 查询余额 (含缓存) |
| `GET /api/billing/usage` | 消费记录 |

---

## 5. 核心聊天流

> 详见原文档 [§1-§9](#L75-L770)。本章精简列出关键文件入口。

| 步骤 | 文件 | 职责 |
|------|------|------|
| HTTP 入口 | `ChatController.java` | 鉴权、会话校验、余额预扣、委托 ChatService |
| 编排 | `ChatService.java` | 工具调用决策、分流 streamDeepSeek / streamWithToolLoop |
| 消息构建 | `MessageContextBuilder.java` | 8 步管线构建 messages[] |
| 流式引擎 | `ChatStreamService.java` | SSE 流解析、工具调用检测、二次请求 |
| 工具注册 | `ToolRegistry.java` | 工具收集、激活判断、执行分发 |
| 后处理 | `ChatPostProcessor.java` | 记忆提取 + 摘要生成 |

---

## 6. 上下文注入管线

> 详见原文档 [附录 D](#L1072-L1373)。8 步管线：系统规则 → 提示词 → 长期记忆 → 摘要 → 知识库 RAG → 历史 → 图片/文件 → 当前消息。

---

## 7. 工具调用系统

> 详见原文档 [附录 B/C/E](#L803-L1590)。两个工具：`search_web` (双引擎竞速) + `analyze_image` (视觉模型 + SSRF 防护)。

---

## 8. 管理后台体系

### 8.1 后台路由

**基础路径**: `/api/admin/**`  
**权限**: `ROLE_ADMIN`  
**入口**: `POST /api/admin/login` → JWT (role=ADMIN)

### 8.2 AdminService — 管理服务

**文件**: [`AdminService.java`](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/service/AdminService.java)

| 功能 | 方法 | 说明 |
|------|------|------|
| 仪表盘 | `getDashboardStats()` | 总用户/对话/消息数、总收入、今日新增、待审核 |
| 图表数据 | `getChartData(days)` | 日期×对话趋势、模型用量分布 |
| 用户列表 | `getUsers(keyword, page, size, sortBy, order)` | 关键词搜索，排序白名单 |
| 模型配置 | `getModelConfigs()` / `create()` / `update()` / `delete()` | CRUD + `@CacheEvict` |
| 消费记录 | `getUsageRecords()` | 按用户+日期筛选 |
| 聊天记录 | `getConversations()` / `getConversationMessages()` | 按用户筛选 |

### 8.3 AdminUserController — 用户管理

**文件**: [`AdminUserController.java`](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/controller/admin/AdminUserController.java)

| 端点 | 说明 | 审计 |
|------|------|------|
| `GET /api/admin/users` | 用户列表 | - |
| `GET /api/admin/users/{id}` | 用户详情+统计 | - |
| `PUT /api/admin/users/{id}/balance` | 修改余额 (±100000 范围) | `logBalanceUpdate` |
| `PUT /api/admin/users/{id}/role` | 修改角色 | `logRoleUpdate` |
| `PUT /api/admin/users/{id}/status` | 启/禁用账户 | `logUserStatus` |

### 8.4 AdminModelConfigController — 模型配置管理

**文件**: [`AdminModelConfigController.java`](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/controller/admin/AdminModelConfigController.java)

| 端点 | 说明 | 审计 |
|------|------|------|
| `GET /api/admin/model-configs` | 列表 (含缓存) | - |
| `POST /api/admin/model-configs` | 新建（`@Valid @RequestBody ModelConfig`） | `logModelCreate` |
| `PUT /api/admin/model-configs/{id}` | 更新 | `logModelUpdate` |
| `DELETE /api/admin/model-configs/{id}` | 删除 | `logModelDelete` |

### 8.5 AdminSponsorController — 赞助审核

**文件**: [`AdminSponsorController.java`](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/controller/admin/AdminSponsorController.java)

| 端点 | 说明 | 金额校验 |
|------|------|---------|
| `GET /api/admin/sponsor-reviews` | 审核列表 (PENDING/APPROVED/REJECTED) | - |
| `PUT .../approve` | 通过 | 0 < tokens ≤ 1,000,000 |
| `PUT .../reject` | 拒绝 | 附说明 |

### 8.6 AdminConversationController — 聊天记录查看

**文件**: [`AdminConversationController.java`](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/controller/admin/AdminConversationController.java)

| 端点 | 说明 |
|------|------|
| `GET /api/admin/conversations` | 对话列表，可选 userId 过滤 |
| `GET /api/admin/conversations/{id}/messages` | 消息记录 |

### 8.7 AdminAuditLogService — 审计日志

所有管理操作均记录到 `admin_operation_logs` 表：

```
审计日志结构:
  admin_id       — 操作管理员
  admin_username — 管理员用户名
  action_type    — BALANCE_UPDATE / ROLE_UPDATE / USER_STATUS / MODEL_CREATE / SPONSOR_APPROVE ...
  target_id      — 被操作对象 ID
  detail         — JSON 详情
  ip_address     — 操作者 IP
  created_at     — 时间戳
```

---

## 9. 功能组件

### 9.1 好友系统 (FriendService)

**文件**: [`FriendService.java`](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/service/FriendService.java)

```
好友关系管理:
  搜索用户 → 发送好友请求 → 接受/拒绝 → 好友消息

friendService.searchUsers(keyword, currentUserId)
    ├── PID 精确匹配 (优先)
    └── 用户名/邮箱/PID 模糊搜索 (Pageable.ofSize(20))

friendService.sendRequest(fromUserId, toUserId)
    ├── 禁止自我好友
    ├── 禁止重复请求
    └── 创建 Friendship (PENDING) + 通知

friendService.sendMessage(friendshipId, content, fromUserId)
    ├── 校验关系状态 (ACCEPTED)
    ├── 创建 FriendMessage
    └── 通知对方
```

| 端点 | 说明 |
|------|------|
| `POST /api/friends/search` | 搜索用户 |
| `POST /api/friends/request` | 发送好友请求 |
| `POST /api/friends/accept` | 接受请求 |
| `POST /api/friends/reject` | 拒绝请求 |
| `GET /api/friends` | 好友列表 |
| `POST /api/friends/message` | 发送消息 |
| `GET /api/friends/messages/{friendshipId}` | 消息记录 |

### 9.2 关注系统 (FollowService)

**文件**: [`FollowService.java`](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/service/FollowService.java)

```
关注/取关:
  follow(followerId, followedId)   — 防止自我关注，幂等
  unfollow(followerId, followedId)
  isFollowing(followerId, followedId)
  getFollowing(userId, page, size)  — "我关注的人"
  getFollowers(userId, page, size)  — "关注我的人"
```

### 9.3 通知系统 (NotificationService)

**文件**: [`NotificationService.java`](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/service/NotificationService.java)

```
Notification 结构:
  targetUserId  — 接收者
  type          — FRIEND_REQUEST / FRIEND_ACCEPT / COMMENT / LIKE / FOLLOW
  title         — 通知标题
  content       — 通知内容
  promptId      — 关联提示词 (可选)
  commentId     — 关联评论 (可选)
  fromUserName  — 触发者用户名
  isRead        — 已读标记

特色:
  - 自己所触发的操作不发通知给自己
  - 标记已读 (单条/全部)
  - 删除需校验 targetUserId 所有权
```

### 9.4 提示词广场 (PromptsHubService)

**文件**: [`PromptsHubService.java`](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/service/PromptsHubService.java)

社区化的提示词分享平台：

| 功能 | 方法 |
|------|------|
| 上传 | `uploadPrompt()` / `uploadPromptWithImage()` / `uploadImageForPrompt()` |
| 创建 | `createPrompt()` — 支持公开/私有 |
| 搜索 | `search()` — MySQL FULLTEXT 布尔模式 |
| 复制 | `copyPrompt()` — 复制到自己的提示词库，记录使用 |
| 点赞 | `toggleLike()` — 每人每日上限 10 次 |
| 收藏 | `toggleFavorite()` |
| 评分 | `ratePrompt()` — 1-5 星 |
| 评论 | `CommentService` — 提示词下的讨论 |

#### MySQL FULLTEXT 索引

```sql
-- prompts_hub 表
FULLTEXT INDEX ft_prompts (name, content, tags)

-- 布尔模式搜索 (已转义)
SELECT * FROM prompts_hub WHERE MATCH(name, content, tags) AGAINST(:q IN BOOLEAN MODE)
```

`PromptsHubService.escapeFulltext()` 在搜索前对布尔运算符 (+,-,*,",(,),@) 做转义。

### 9.5 知识库系统 (KnowledgeBaseService)

**文件**: [`KnowledgeBaseService.java`](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/service/KnowledgeBaseService.java)

#### 文档处理管线

```
上传文档 (MultipartFile)
    │
    ▼
1. 文档解析器选择 (DocumentParser 接口，8 种格式)
    ├── TextParser        — .txt
    ├── MarkdownParser    — .md
    ├── PdfParser         — .pdf (支持 OCR 回退 → Tesseract)
    ├── WordParser        — .docx
    ├── ExcelParser       — .xlsx
    ├── PptParser         — .pptx
    ├── HtmlParser        — .html
    └── CsvParser         — .csv
    │
    ▼
2. 分块 (ChunkingService)
    - 可配置 chunkSize (默认 500) 和 overlap (默认 50)
    - 知识库级别参数覆盖全局配置
    │
    ▼
3. 向量化 (SiliconFlowEmbeddingService)
    - 批量 embed ~32 条/批
    │
    ▼
4. 写入
    └── ChromaDB (collection: kb_{kbId}_doc_{docId})
```

#### 限制

| 限制项 | 值 |
|--------|-----|
| 单文件大小 | 20 MB |
| 单文档分块数 | 500 |
| 单知识库文档数 | 200 |
| 单用户文档总数 | 1000 |
| 单用户存储占用 | 500 MB |
| 处理线程池 | 2-4 核心线程, 有界队列 20 |

### 9.6 记忆子系统

#### 概览

```
┌─────────────────────────────────────────────────────────┐
│                   Memory System                          │
│                                                          │
│  MemoryService        — 提取/衰减/注入                    │
│  GraphMemoryService   — 知识图谱 (实体+关系)              │
│  MemoryChromaService  — ChromaDB 向量存储                 │
│  EntityRetrievalService — 实体检索 (基于图谱)              │
└─────────────────────────────────────────────────────────┘
```

#### 四种操作模式

| 模式 | 方法 | 触发时机 |
|------|------|---------|
| 1. 自动提取 | `extractAndStore()` | 每次对话完成，LLM 从对话中提取事实 |
| 2. 默认注入 | `getRecentMemoriesForContext()` | 每次构建上下文时 |
| 3. 按需回溯 | `searchChroma()` | 用户手动搜索记忆 |
| 4. 懒衰减 | 读取时实时检查 | `getRecentMemoriesForContext()` |

#### GraphMemoryService — 知识图谱

**文件**: [`GraphMemoryService.java`](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/service/GraphMemoryService.java)

```
记忆提取 → linkMemory()
    │
    ├── LLM 提取实体 (人名/地名/机构名)
    ├── memory_entities 表 merge
    ├── memory_item_entities 关联
    └── memory_relations 关系建边 (subject → predicate → object)

上下文注入 → expandViaGraph()
    │
    └── 种子记忆 → 1 跳出边 + 1 跳入边 → 邻接实体 → 召回关联记忆
```

#### EntityRetrievalService — 实体检索

**文件**: [`EntityRetrievalService.java`](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/service/EntityRetrievalService.java)

```
query="张三是谁"
    │
    ▼
1. LLM 提取实体: ["张三"]
    │
    ▼
2. entityRepo.findByUserIdAndName(userId, "张三")
    │
    ▼
3. itemEntityRepo.findMemoryIdsByEntityId(entityId)
    │
    ▼
4. 图扩展 (1 跳):
    ├── 出边: entity → neighbor → 关联记忆 (score=0.5)
    └── 入边: neighbor → entity → 关联记忆 (score=0.5)
    │
    ▼
5. 归一化打分 → Top-K
```

### 9.7 LLMService — 底层 API 调用

**文件**: [`LLMService.java`](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/service/LLMService.java)

两种调用模式：

| 方法 | 场景 | 特点 |
|------|------|------|
| `callAsyncWithUsage(messages, config)` | 非流式聊天 | `CompletableFuture<TokenUsageResult>`，返回 token 用量 |
| `chatSync(prompt)` | 记忆提取/摘要生成/实体提取 | 同步调用，使用 `memory.llm` 配置 |

```java
// chatSync: 独立线程池 + 独立 LLM 配置，避免死锁
public String chatSync(String prompt) {
    ModelConfig config = new ModelConfig();
    config.setApiKey(memoryProperties.getLlm().getApiKey());
    config.setApiUrl(memoryProperties.getLlm().getApiUrl());
    config.setModelName(memoryProperties.getLlm().getModelName());
    // ...
}
```

---

## 10. 基础设施

### 10.1 ChromaDBLauncher — 自动启停

**文件**: [`ChromaDBLauncher.java`](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/config/ChromaDBLauncher.java)

```
Spring Boot 启动
    │
    ▼
ApplicationRunner.run()
    ├── isChromaAlive()? (HTTP GET {chromadb.url}/api/v2/heartbeat)
    │   ├── true → 跳过启动
    │   └── false → 启动子进程
    │       └── ProcessBuilder: chroma run --path ./chroma_data --port 8000
    │
    ▼
@PreDestroy: chromaProcess.destroy()
```

### 10.2 SiliconFlowEmbeddingService — 向量化

**文件**: [`SiliconFlowEmbeddingService.java`](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/service/SiliconFlowEmbeddingService.java)

```
embedBatch(texts)
    │
    ├── POST {embedding.api-url}
    │   Body: { model, input: [...], encoding_format: "float" }
    │
    └── 响应: data[].embedding[] → List<List<Double>>
```

| 配置 | 值 |
|------|-----|
| 模型 | `embedding.model` (如 BAAI/bge-large-zh-v1.5) |
| 维度 | 1024 |
| 批量 | 最多 32 条/次 |

### 10.3 SiliconFlowRerankService — 精排

**文件**: [`SiliconFlowRerankService.java`](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/service/SiliconFlowRerankService.java)

Cross-Encoder 模型对粗排候选做二次精排：

```
rerank(query, candidates, topN)
    │
    ├── POST {rerank.api-url}
    │   Body: { model, query, documents: [...], top_n }
    │
    └── 响应: results[].{index, relevance_score}
        → 按 score 降序 → 取 topN
```

| 配置 | 说明 |
|------|------|
| `rerank.model` | Rerank 模型名 (如 BAAI/bge-reranker-v2-m3) |
| `rerank.api-key` | API Key |
| `rerank.api-url` | API 端点 |

### 10.4 ImageService — 图片处理

**文件**: [`ImageService.java`](file:///c:/Users/makot/Desktop/aichat/src/main/java/com/example/aichat/service/ImageService.java)

```
方式 A (工具路径):  uploadFileOnly(file)
    ├── S3/MinIO 存储
    └── 返回 URL → 传入 ChatRequest.imageUrl → LLM 调用 analyze_image

方式 B (降级路径): uploadAndRecognize(file)
    ├── S3/MinIO 存储
    ├── 同步调视觉模型
    └── 返回 imageDescription 文本 → 作为 system 消息注入
```

### 10.5 文件存储

| 组件 | 技术 | 用途 |
|------|------|------|
| S3/MinIO | `S3Properties` 配置 | 图片/文件上传 |
| 本地目录 | `./uploads/images`, `./uploads/kb`, `./uploads/avatars` | 开发环境回退 |
| ChromaDB 数据 | `./chroma_data` | 向量持久化 |

### 10.6 缓存策略

| 缓存名 | 类型 | TTL | 用途 |
|--------|------|-----|------|
| `billingBalance` | Spring Cache | - (手动 evict) | 用户余额 |
| `modelConfigs` | Spring Cache | - (手动 evict) | 模型配置列表 |
| `promptsByUser` | Spring Cache | - | 提示词按 ID |
| `memoryMemories` | Spring Cache | - | 记忆列表 |
| 速率限制计数器 | Caffeine | 规则窗口时长 | 每个用户+规则组合 |
| Token 黑名单 | Caffeine | JWT 过期时间 | 已登出令牌缓存 |

---

## 11. 关键配置与常量

### 安全相关

| 常量 | 位置 | 值 |
|------|------|-----|
| BCrypt 强度 | SecurityConfig | 默认 10 轮 |
| JWT 签名算法 | JwtUtil | HMAC-SHA256 |
| JWT 过期时间 | JwtProperties.expiration | 配置值 (毫秒) |
| AES 算法 | AESUtil | AES/GCM/NoPadding |
| AES IV 长度 | AESUtil | 12 bytes |
| AES 标签长度 | AESUtil | 128 bits |
| Caffeine 限流容量 | RateLimitInterceptor | 5000 条目 |
| Caffeine 黑名单容量 | TokenBlacklist | 无限制 (TTL 自动淘汰) |

### 业务相关

| 常量 | 位置 | 值 |
|------|------|-----|
| SSE 超时 | ChatStreamService | 120s |
| 最大历史轮数 | MessageContextBuilder | 30 |
| 工具调用最大轮次 | ChatStreamService | 2 |
| 工具执行超时 | ChatStreamService | 30s |
| Token 估算系数 | ChatStreamService / LLMService | 1.3 |
| 每个用户最大对话数 | ConversationService | 10 |
| 计费安全系数 | BillingService | 2.0 |
| 搜索竞速超时 | SearchWebTool | 30s |
| 搜索结果截断 | SearchWebTool | 2000 字符 |
| 单文件上传 | KnowledgeBaseService | 20 MB |
| 单 KB 文档数 | KnowledgeBaseService | 200 |
| 单用户文档数 | KnowledgeBaseService | 1000 |
| 单用户存储 | KnowledgeBaseService | 500 MB |
| PromptsHub 图片 | PromptsHubService | 5 MB, 5 种格式 |
| 每日点赞上限 | PromptsHubService | 10 次 |
| 验证码有效期 | EmailService | 5 分钟 |
| 验证码爆破上限 | EmailService | 5 次 |

---

## 12. 附录

### 附录 A: 工具调用 vs 降级路径对比

| 维度 | 工具调用路径 | 降级路径 (预注入) |
|------|------------|-----------------|
| 适用场景 | supportsToolCalling=true | 不支持 Function Calling 的模型 |
| 搜索触发 | LLM 自行决定 | 后端固定预先搜索 |
| 搜索时机 | LLM 首轮响应后 | 构建 messages 时预搜索 |
| 延迟影响 | 有 tool_calls 时加一次 RTT | 加搜索 API 等待 |

### 附录 B: 当前注册的工具

| 工具名 | Handler | 激活条件 | 参数 |
|--------|---------|---------|------|
| `search_web` | SearchWebTool | `webSearchEnabled=true` | `query` (string, required) |
| `analyze_image` | AnalyzeImageTool | `imageUrl`/`fileUrl` 非空 | `image_url` (string, required) |

> 详见 [附录 C/E] 搜索双引擎竞速详解和识图工具详解。

---

## 13. 外部依赖清单

### 13.1 运行时基础设施

| 组件 | 版本 | 用途 | Docker 镜像 |
|------|------|------|-------------|
| Java | 17 | 运行时 | `eclipse-temurin:17-jre` (基于 Dockerfile) |
| MySQL | 8.0 | 主数据库 | `mysql:8.0` |
| ChromaDB | 0.6.3 | 向量存储 | `chromadb/chroma:0.6.3` |
| Tesseract OCR | - | PDF OCR 回退 | 内置在 Docker 基础镜像中 |

### 13.2 后端 Maven 依赖

#### 核心框架

| 依赖 | 版本 | 用途 |
|------|------|------|
| spring-boot-starter-parent | 4.0.6 | Spring Boot 父 POM |
| spring-boot-starter-web | (继承) | REST API + 嵌入式 Tomcat |
| spring-boot-starter-data-jpa | (继承) | JPA + Hibernate ORM |
| spring-boot-starter-security | (继承) | 认证/授权框架 |
| spring-boot-starter-cache | (继承) | Spring Cache 抽象 |
| spring-boot-starter-mail | (继承) | SMTP 邮件发送 |
| spring-boot-starter-validation | (继承) | Bean Validation |
| spring-boot-starter-actuator | (继承) | 健康检查 / 指标监控 |
| spring-boot-starter-thymeleaf | (继承) | 模板引擎（管理后台 SSR） |
| spring-boot-devtools | (继承) | 开发热重载 |

#### 安全

| 依赖 | 版本 | 用途 |
|------|------|------|
| jjwt-api / jjwt-impl / jjwt-jackson | 0.12.6 | JWT 生成与解析 (HMAC-SHA256) |
| spring-dotenv | 4.0.0 | .env 文件加载 |

#### 数据库

| 依赖 | 版本 | 用途 |
|------|------|------|
| mysql-connector-j | (继承) | MySQL JDBC 驱动 |
| flyway-mysql | (继承) | 数据库迁移管理 |
| spring-ai-bom | 2.0.0 | Spring AI 依赖管理 |
| spring-ai-starter-model-openai | (继承) | OpenAI 兼容 API 客户端 |

#### 缓存与工具

| 依赖 | 版本 | 用途 |
|------|------|------|
| caffeine | (继承) | 本地高性能缓存 |
| jackson-databind | (继承) | JSON 处理 |
| httpclient5 | 5.4.1 | HTTP 客户端 |
| lombok | (继承) | 代码生成 |

#### 文档解析

| 依赖 | 版本 | 用途 |
|------|------|------|
| poi-ooxml | 5.3.0 | Word/Excel/PPT 解析 |
| jsoup | 1.18.1 | HTML 解析 |

#### 云存储

| 依赖 | 版本 | 用途 |
|------|------|------|
| aws-sdk-s3 | 2.28.0 | S3 兼容存储 |

### 13.3 前端 NPM 依赖

#### 核心框架

| 依赖 | 版本 | 用途 |
|------|------|------|
| react | ^19.2.7 | UI 框架 |
| react-dom | ^19.2.7 | DOM 渲染 |
| react-router-dom | ^7.18.1 | SPA 路由 |

#### UI 组件

| 依赖 | 版本 | 用途 |
|------|------|------|
| radix-ui/react-avatar | ^1.2.0 | 头像组件 |
| radix-ui/react-dropdown-menu | ^2.1.18 | 下拉菜单 |
| radix-ui/react-popover | ^1.1.17 | 弹出框 |
| radix-ui/react-scroll-area | ^1.2.12 | 自定义滚动条 |
| radix-ui/react-select | ^2.3.1 | 选择器 |
| radix-ui/react-separator | ^1.1.10 | 分隔线 |
| radix-ui/react-slot | ^1.3.0 | 插槽 |
| radix-ui/react-switch | ^1.3.1 | 开关 |
| radix-ui/react-tooltip | ^1.2.10 | 提示框 |
| lucide-react | ^1.21.0 | 图标库 |

#### 样式/工具

| 依赖 | 版本 | 用途 |
|------|------|------|
| tailwindcss | ^3.4.19 | CSS 工具类框架 |
| class-variance-authority | ^0.7.1 | 组件变体管理 |
| clsx | ^2.1.1 | className 合并 |
| tailwind-merge | ^3.6.0 | Tailwind 类名合并去重 |
| motion | ^12.42.0 | 动画库 |

#### 构建工具

| 依赖 | 版本 | 用途 |
|------|------|------|
| vite | ^8.1.0 | 构建工具 |
| typescript | ~6.0.2 | 类型检查 |
| @vitejs/plugin-react | ^6.0.2 | React Fast Refresh |
| postcss | ^8.5.15 | CSS 后处理 |
| autoprefixer | ^10.5.2 | CSS 浏览器前缀 |
| oxlint | ^1.69.0 | Linter |

### 13.4 外部 API 依赖

| 服务 | API 端点 | 用途 | 认证方式 | 配置项 |
|------|---------|------|---------|--------|
| DeepSeek | `https://api.deepseek.com/v1/chat/completions` | 聊天 LLM | Bearer Token | `config.apiKey` |
| SiliconFlow Embedding | `config.embeddingApiUrl` | 文本向量化 | Bearer Token | `SILICONFLOW_API_KEY` |
| SiliconFlow Rerank | `config.rerankApiUrl` | Cross-Encoder 精排 | Bearer Token | `SILICONFLOW_API_KEY` |
| SiliconFlow Vision | `config.imageApiUrl` | 视觉模型 (图片分析) | Bearer Token | `IMAGE_API_KEY` |
| 千帆搜索 | `config.qianfanApiUrl` | 百度搜索 (千帆渠道) | Bearer Token | `QIANFAN_API_KEY` |
| Tavily Search | `TAVILY_API_URL` | AI 搜索 | Bearer Token | `TAVILY_API_KEY` |
| S3/MinIO | `config.s3UrlPrefix` | 对象存储 | AK/SK | `S3_ACCESS_KEY` / `S3_SECRET_KEY` |
| SMTP | `config.mailHost` | 邮件发送 | 用户名/密码 | `MAIL_USERNAME` / `MAIL_PASSWORD` |
| Tesseract OCR | 本地 `/usr/share/tessdata` | PDF OCR 回退 | 无 | 本机文件系统 |

---

## 14. 前后端接口契约

### 14.1 通用约定

| 项目 | 约定 |
|------|------|
| 基础路径 | `/api` |
| 认证方式 | `Authorization: Bearer <JWT>` |
| 内容类型 | `application/json`（文件除外） |
| 时间格式 | ISO-8601 (`2026-07-26T10:30:00`) |
| 分页参数 | `?page=0&size=20` (0-based) |
| 排序参数 | `?sortBy=createdAt&order=desc` |
| 流式响应 | `text/event-stream` (SSE) |

### 14.2 认证与用户 API

| 方法 | 路径 | 认证 | 说明 | 请求体 |
|------|------|------|------|--------|
| POST | `/api/auth/send-code` | - | 发送注册验证码 | `{email}` |
| POST | `/api/auth/register` | - | 注册 | `{username, password, email, code}` |
| POST | `/api/auth/login` | - | 登录 | `{username, password}` |
| POST | `/api/auth/send-reset-code` | - | 发送重置验证码 | `{email}` |
| POST | `/api/auth/reset-password` | - | 重置密码 | `{email, code, newPassword}` |
| POST | `/api/auth/change-password` | JWT | 修改密码 | `{oldPassword, newPassword}` |
| POST | `/api/auth/update-profile` | JWT | 更新签名 | `{signature}` |
| POST | `/api/auth/upload-avatar` | JWT | 上传头像 | `multipart/form-data` |
| GET | `/api/auth/profile/{id}` | JWT | 用户信息 | - |

### 14.3 聊天 API

| 方法 | 路径 | 认证 | 说明 | 请求体 |
|------|------|------|------|--------|
| POST | `/api/chat/{conversationId}/stream` | JWT | SSE 流式聊天 | `ChatRequest` |
| GET | `/api/chat/{conversationId}/history` | JWT | 历史消息 | - |
| POST | `/api/chat/conversations` | JWT | 创建对话 | `{name}` |
| GET | `/api/chat/conversations` | JWT | 对话列表 | - |
| DELETE | `/api/chat/conversations/{id}` | JWT | 删除对话 | - |

**ChatRequest 结构**:
```json
{
  "message": "string (required)",
  "modelConfigId": "long (required)",
  "promptId": "long (optional)",
  "knowledgeBaseId": "long (optional)",
  "webSearchEnabled": "boolean (optional)",
  "longMemoryEnabled": "boolean (optional)",
  "imageUrl": "string (optional)",
  "imageDescription": "string (optional)",
  "fileUrl": "string (optional)"
}
```

### 14.4 提示词 API

| 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|
| GET | `/api/prompts` | JWT | 我的提示词列表 |
| POST | `/api/prompts` | JWT | 创建提示词 |
| PUT | `/api/prompts/{id}` | JWT | 更新提示词 |
| DELETE | `/api/prompts/{id}` | JWT | 删除提示词 |

### 14.5 提示词广场 API

| 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|
| GET | `/api/prompts-hub` | JWT | 广场列表 (热度递减) |
| GET | `/api/prompts-hub/{id}` | JWT | 提示词详情 |
| POST | `/api/prompts-hub` | JWT | 创建分享 |
| GET | `/api/prompts-hub/search` | JWT | `?q=keyword` 全文搜索 |
| POST | `/api/prompts-hub/{id}/like` | JWT | 切换点赞 |
| POST | `/api/prompts-hub/{id}/dislike` | JWT | 点踩 |
| POST | `/api/prompts-hub/{id}/favorite` | JWT | 切换收藏 |
| POST | `/api/prompts-hub/{id}/rate` | JWT | 评分 `{score: 1-5}` |
| POST | `/api/prompts-hub/{id}/copy` | JWT | 复制到我的提示词 |
| POST | `/api/prompts-hub/upload` | JWT | 上传提示词 (含图片) |
| POST | `/api/prompts-hub/upload-image` | JWT | 上传封面图 |
| PUT | `/api/prompts-hub/{id}/image` | JWT | 更新封面图 |
| GET | `/api/prompts-hub/{id}/comments` | JWT | 评论列表 |
| POST | `/api/prompts-hub/{id}/comments` | JWT | 发表评论 |

### 14.6 知识库 API

| 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|
| POST | `/api/kb/create` | JWT | 创建知识库 |
| GET | `/api/kb/list` | JWT | 知识库列表 |
| PUT | `/api/kb/{id}` | JWT | 编辑知识库 |
| DELETE | `/api/kb/{id}` | JWT | 删除知识库 |
| POST | `/api/kb/{kbId}/docs/upload` | JWT | 上传文档 |
| GET | `/api/kb/{kbId}/docs` | JWT | 文档列表 |
| DELETE | `/api/kb/docs/{docId}` | JWT | 删除文档 |
| POST | `/api/kb/docs/{docId}/reindex` | JWT | 重新索引 |

### 14.7 记忆 API

| 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|
| GET | `/api/memory/list` | JWT | 所有记忆 |
| GET | `/api/memory/enabled` | JWT | 已启用记忆 |
| POST | `/api/memory/add` | JWT | 手动添加 |
| PUT | `/api/memory/{id}` | JWT | 编辑记忆 |
| PUT | `/api/memory/{id}/toggle` | JWT | 启用/禁用 |
| DELETE | `/api/memory/{id}` | JWT | 删除单条 |
| DELETE | `/api/memory/clear` | JWT | 清空全部 |
| POST | `/api/memory/search` | JWT | 搜索记忆 |
| GET | `/api/memory/entities/merge-suggestions` | JWT | 实体消歧建议 |
| POST | `/api/memory/entities/merge` | JWT | 执行实体合并 |

### 14.8 计费 API

| 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|
| GET | `/api/billing/balance` | JWT | 余额查询 |
| GET | `/api/billing/usage-records` | JWT | 消费记录 |
| POST | `/api/billing/sponsor-create` | JWT | 赞助审核提交 |
| POST | `/api/billing/sponsor-query` | JWT | 审核状态查询 |
| POST | `/api/billing/checkin` | JWT | 每日签到 |
| GET | `/api/billing/checkin-status` | JWT | 签到状态 |

### 14.9 搜索 API

| 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|
| POST | `/api/search/web` | JWT | 千帆搜索 `{query, count}` |
| GET | `/api/search/web` | JWT | `?query=&count=` |
| POST | `/api/search/tavily` | JWT | Tavily 搜索 |
| GET | `/api/search/tavily` | JWT | `?query=&maxResults=&searchDepth=` |

### 14.10 好友 API

| 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|
| GET | `/api/friends/search` | JWT | `?q=keyword` |
| POST | `/api/friends/request` | JWT | 发送好友请求 |
| POST | `/api/friends/accept` | JWT | 接受请求 |
| POST | `/api/friends/reject` | JWT | 拒绝请求 |
| GET | `/api/friends/list` | JWT | 好友列表 |
| GET | `/api/friends/pending` | JWT | 待处理请求 |
| POST | `/api/friends/message` | JWT | 发送消息 |
| GET | `/api/friends/chat/{friendUserId}` | JWT | 聊天记录 |
| POST | `/api/friends/read/{senderId}` | JWT | 标记已读 |

### 14.11 关注 API

| 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|
| POST | `/api/follows/{userId}` | JWT | 关注 |
| DELETE | `/api/follows/{userId}` | JWT | 取消关注 |
| GET | `/api/follows/{userId}/status` | JWT | 关注状态 |
| GET | `/api/follows/following` | JWT | 我关注的 |
| GET | `/api/follows/followers` | JWT | 关注我的 |
| GET | `/api/follows/stats` | JWT | 统计 |

### 14.12 通知 API

| 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|
| GET | `/api/notifications` | JWT | 通知列表 |
| GET | `/api/notifications/unread-count` | JWT | 未读数 |
| POST | `/api/notifications/read-all` | JWT | 全部已读 |
| POST | `/api/notifications/{id}/read` | JWT | 单条已读 |
| DELETE | `/api/notifications/{id}` | JWT | 删除通知 |

### 14.13 模型配置 API

| 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|
| GET | `/api/model-configs` | JWT | 公开模型列表 (不含 API Key) |
| GET | `/api/model-configs/{id}` | JWT | 模型详情 (不含 API Key) |

### 14.14 管理后台 API (`/api/admin/**` — `ROLE_ADMIN`)

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/admin/login` | 管理员登录 |
| POST | `/api/admin/logout` | 管理员登出 |
| GET | `/api/admin/dashboard` | 仪表盘 |
| GET | `/api/admin/dashboard/charts` | 图表 |
| GET | `/api/admin/users` | 用户列表 |
| GET | `/api/admin/users/{id}` | 用户详情 |
| PUT | `/api/admin/users/{id}/balance` | 修改余额 |
| PUT | `/api/admin/users/{id}/role` | 修改角色 |
| PUT | `/api/admin/users/{id}/status` | 启/禁用 |
| GET | `/api/admin/model-configs` | 模型配置列表 |
| POST | `/api/admin/model-configs` | 新建模型配置 |
| PUT | `/api/admin/model-configs/{id}` | 更新 |
| DELETE | `/api/admin/model-configs/{id}` | 删除 |
| GET | `/api/admin/sponsor-reviews` | 赞助审核列表 |
| PUT | `/api/admin/sponsor-reviews/{id}/approve` | 通过 |
| PUT | `/api/admin/sponsor-reviews/{id}/reject` | 拒绝 |
| GET | `/api/admin/conversations` | 对话列表 |
| GET | `/api/admin/conversations/{id}/messages` | 消息记录 |
| GET | `/api/admin/usage-records` | 消费记录 |
| GET | `/api/admin/revenue-stats` | 收入统计 |
| GET | `/api/admin/prompts-hub` | 提示词管理 |
| DELETE | `/api/admin/prompts-hub/{id}` | 删除提示词 |
| PUT | `/api/admin/prompts-hub/{id}/feature` | 精选 |
| GET | `/api/admin/prompts-hub/audit` | 审核队列 |
| POST | `/api/admin/prompts-hub/{id}/approve` | 审核通过 |
| POST | `/api/admin/prompts-hub/{id}/reject` | 审核拒绝 |
| GET | `/api/admin/system-rules` | 系统规则列表 |
| POST | `/api/admin/system-rules` | 新建规则 |
| PUT | `/api/admin/system-rules/{id}` | 更新规则 |
| DELETE | `/api/admin/system-rules/{id}` | 删除规则 |
| POST | `/api/admin/system-rules/{id}/toggle` | 切换启用 |
| PUT | `/api/admin/system-rules/sort` | 排序 |
| GET | `/api/admin/audit-logs` | 审计日志 |
| POST | `/api/admin/api-test` | API 健康检查 |

### 14.15 文件上传 API

| 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|
| POST | `/api/image/upload` | JWT | 聊天图片上传 |
| POST | `/api/file/upload` | JWT | 聊天文件上传 |

### 14.16 端点数量统计

| 分类 | 公开 | 需认证 (USER) | 管理员 (ADMIN) | 合计 |
|------|------|--------------|---------------|------|
| 认证 | 5 | 4 | - | 9 |
| 聊天 | - | 5 | - | 5 |
| 提示词 | - | 4 | - | 4 |
| 提示词广场 | - | 14 | - | 14 |
| 知识库 | - | 8 | - | 8 |
| 记忆 | - | 10 | - | 10 |
| 计费 | - | 6 | - | 6 |
| 搜索 | - | 4 | - | 4 |
| 好友 | - | 9 | - | 9 |
| 关注 | - | 6 | - | 6 |
| 通知 | - | 5 | - | 5 |
| 模型配置 | - | 2 | - | 2 |
| 文件上传 | - | 2 | - | 2 |
| 管理后台 | 1 | - | 29 | 30 |
| **合计** | **6** | **79** | **29** | **114** |

---

## 15. QPS 预测与资源需求

### 15.1 业务模型假设

| 场景 | 小型社区 | 中型社区 | 大型社区 |
|------|---------|---------|---------|
| DAU (日活用户) | 100 | 1,000 | 10,000 |
| 并发用户 (峰值) | 10 | 100 | 500 |
| 日聊天请求 | 500 | 5,000 | 50,000 |
| 日均消息 | 2,500 | 25,000 | 250,000 |

### 15.2 各接口 QPS 估算

| 接口类别 | 小型 (QPS) | 中型 (QPS) | 大型 (QPS) | 瓶颈点 |
|---------|-----------|-----------|-----------|--------|
| **聊天 SSE** | 0.5-1 | 5-10 | 50-100 | LLM API 延迟 + Tomcat 线程 |
| **聊天 (非流式)** | 0.2 | 2 | 20 | LLM API |
| **提示词广场列表** | 1 | 5-10 | 50 | MySQL + Cache 命中 |
| **提示词搜索** | 0.5 | 3 | 30 | MySQL FULLTEXT |
| **知识库检索** | 0.5 | 3-5 | 30 | ChromaDB + Rerank |
| **知识库上传** | 0.01 | 0.1 | 1 | 文档解析 + Embedding |
| **记忆操作** | 0.5 | 3 | 20 | MySQL + ChromaDB |
| **好友/关注** | 0.2 | 2 | 15 | MySQL |
| **认证 (登录/注册)** | 0.1 | 1 | 5 | MySQL + JWT 生成 |
| **管理后台** | 0.05 | 0.5 | 3 | MySQL 复杂查询 |
| **总 QPS** | **~5** | **~35** | **~250** | - |

### 15.3 资源需求

#### 小型社区 (DAU 100, 总 QPS ~5)

| 资源 | 规格 | 用途 |
|------|------|------|
| App 容器 | 1 核 / 512MB | Spring Boot + ChromaDB Launcher |
| MySQL | 1 核 / 1GB | 主数据库 |
| ChromaDB | 0.5 核 / 512MB | 向量存储 |
| 带宽 | 10 Mbps | API 流量 + LLM 流式回传 |
| 磁盘 | 20 GB | 数据库 + 向量 + 文件上传 |

**预估月费 (国内云)**: ~150-300 元/月

#### 中型社区 (DAU 1,000, 总 QPS ~35)

| 资源 | 规格 | 用途 |
|------|------|------|
| App 容器 | 2 核 / 2GB x2 | 2 实例 + Nginx 负载均衡 |
| MySQL | 2 核 / 4GB | 增加 buffer pool、慢查询日志 |
| ChromaDB | 1 核 / 1GB | 增大 cache_size |
| Redis | 1 核 / 512MB | Session / 分布式缓存 |
| 带宽 | 30 Mbps | LLM SSE 流量占比高 |
| 磁盘 | 50 GB SSD | 文件增多 |

**预估月费**: ~800-1,500 元/月

#### 大型社区 (DAU 10,000, 总 QPS ~250)

| 资源 | 规格 | 用途 |
|------|------|------|
| App 容器 | 4 核 / 4GB x4 | K8s HPA 弹性伸缩 |
| MySQL | 4 核 / 8GB (主从) | 读写分离 |
| ChromaDB | 2 核 / 4GB x2 | 分布式向量库 |
| Redis | 2 核 / 4GB (哨兵) | 高可用缓存 |
| 消息队列 | RabbitMQ / Kafka | 知识库处理异步化 |
| Nginx | 2 核 / 2GB | 反代 + SSL 终结 + 限速 |
| 带宽 | 100 Mbps | 大量 SSE 长连接 |
| 磁盘 | 200 GB SSD | 增量备份 |

**预估月费**: ~5,000-10,000 元/月

### 15.4 关键优化点

| 优化项 | 当前状态 | 建议 |
|--------|---------|------|
| 连接池配置 | 默认 | 调整 HikariCP pool-size = CPU × 2 + 磁盘数 |
| Tomcat 线程 | 默认 200 | 峰值的 1.5 倍 |
| JPA 查询 | 多数使用 Spring Data | 监控 N+1 查询 |
| 缓存命中 | Caffeine + Spring Cache | Redis 分布式缓存替代 Caffeine |
| 数据库索引 | Flyway 管理 | 定期 explain 慢查询 |
| SSE 连接数 | Tomcat 线程数限制 | 中型以上考虑 WebFlux |
| ChromaDB | 单实例 | 大型时考虑集群部署 |
| 日志级别 | 生产环境 INFO | 关闭 debug/trace |
| JVM 参数 | 默认 | `-XX:+UseG1GC -Xms=heap/2 -Xmx=heap` |

### 15.5 可扩展性分析

```
水平扩展瓶颈评估:

  App Server: ✅ 无状态，直接横向扩展
        └── 注意: ChromaDBLauncher 在 K8s 下应改为 sidecar 模式或独立部署

  MySQL:     ⚠️ 读多可加从库，写仍有单点
        └── 大型规模考虑分库 (user / chat / billing)

  ChromaDB:  ⚠️ 当前单实例
        └── ChromaDB 0.6.x 分布式能力有限，大型考虑 Milvus/Qdrant 替代

  SSE 长连接: ⚠️ 每连接占用 1 线程
        └── 当前使用 SseEmitter (WebFlux 可支持事件驱动)

  搜索竞速:   ⚠️ 每次对话可能触发双引擎搜索
        └── 搜索缓存 (TTL 5分钟) 可大幅降低重复搜索 API 消耗
```

---

*本文档基于源码自动生成，覆盖全部 60+ Java 文件的核心逻辑，最后更新 2026-07-26。*
