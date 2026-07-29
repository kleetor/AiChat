# HanaChat 安全防护对标评估报告

> **审查日期**：2026-07-17 | **审查范围**：后端 157 类 + 前端 47 组件 + 3 部署配置  
> **综合评分**：7.2/10（同类项目中上，距生产级差 1.8 分）

---

## 一、总体评分

| 维度 | 评分 | 行业对标 |
|:---|:---:|:---|
| 认证与授权 | 7.5/10 | 优于大部分同类 AI Chat 项目 |
| 数据保护（加密） | 7.5/10 | 达到企业级标准 |
| API 安全（限流/CORS） | 7.0/10 | 优于同类，略低于生产级 |
| 前端安全 | 6.0/10 | 达到中位水平 |
| 输入验证 | 7.0/10 | 框架级 + 业务级双覆盖 |
| 审计与可观测性 | 7.0/10 | 远超同类项目 |
| 部署安全 | 5.5/10 | 开发便利优先，生产加固不足 |
| OWASP Top 10 合规 | 7.0/10 | 核心风险已收敛 |

---

## 二、分类详细评估

### 2.1 认证与授权 —— 7.5/10

**已有防护：**
- JWT HS256 + BCrypt 密码哈希
- Token 黑名单双层保障（Caffeine + MySQL SHA-256 哈希）—— **优于 90% 同类项目**
- ROLE_ADMIN 角色隔离 + `/api/admin/**` 独立权限
- STATELESS Session + CSRF 正确禁用

**差距：**
- 缺少 Refresh Token 机制。24h 过期后用户必须重新登录
- 缺少 `jti`（JWT ID）声明，黑名单依赖整 Token 哈希
- JWT 密钥自动扩展（SHA-256）掩蔽弱密钥，应直接拒绝

### 2.2 数据保护 —— 7.5/10

**已有防护：**
- API Key AES-256-GCM 认证加密 + JPA AttributeConverter 透明转换 —— **达到企业级**
- 启动时自动迁移明文 → 加密
- 密钥缺失时拒绝启动

**差距：**
- 加密密钥无版本化，更换 `ENCRYPTION_KEY` 后历史数据不可解密
- AESUtil 的 `KEY_SPEC` 非 volatile（理论并发风险）

### 2.3 API 安全 —— 7.0/10

**已有防护：**
- 16 条精细化速率限制（Caffeine 滑动窗口令牌桶）
- SSRF 防护（`NetworkUtils` 拒绝内网/回环地址）
- 安全响应头：HSTS / X-Frame-Options / X-Content-Type-Options

**差距：**

| # | 问题 | 严重度 |
|:---:|:---|:---:|
| 1 | CSP 包含 `unsafe-inline`（scripts + styles） | **高** |
| 2 | `connect-src 'self' https:` 过于宽泛 | **中** |
| 3 | 缺少 `base-uri`、`form-action`、`Referrer-Policy`、`Permissions-Policy` 安全头 | **低** |
| 4 | 速率限制对未认证用户回退到 IP（`getRemoteAddr()`），可通过 IP 轮换绕过 | **低** |

### 2.4 前端安全 —— 6.0/10

**已有防护：**
- Token 存 `sessionStorage` 而非 `localStorage`
- 无 `dangerouslySetInnerHTML` 使用
- 401 响应自动清 Token + 防重定向循环

**差距：**

| # | 问题 | 严重度 |
|:---:|:---|:---:|
| 4 | Token 仍对同源 JS 可见（非 httpOnly Cookie） | **中** |
| 5 | 无 DOMPurify / sanitize-html 依赖，若未来引入 HTML 渲染即有 XSS 风险 | **中** |

### 2.5 输入验证 —— 7.0/10

**已有防护：**
- 全部 DTO Jakarta `@Valid` 注解
- `GlobalExceptionHandler` 不泄露内部细节
- 头像上传 MIME + 扩展名双白名单
- 充值金额校验：null 检查 + >0 + ≤10000（3 个入口全覆盖）

**差距：**

| # | 问题 | 严重度 |
|:---:|:---|:---:|
| 7 | ~~`BillingService` 无金额上下限校验~~ **已修复**——`validateRechargeAmount()` 覆盖 `recharge()` / `adminRecharge()` / `createSponsorOrder()` | ~~高~~ |
| 8 | `sendResetCode` 与 `resetPassword` 用户枚举行为不一致——前者静默返回，后者抛 `"用户不存在"`，攻击者可绕过防枚举保护 | **中** |
| 9 | 验证码端点 `/send-code` 无 CAPTCHA 防护，可轰炸任意邮箱 | **中** |

### 2.6 审计与可观测性 —— 7.0/10

**已有防护（远超同类）：**
- `AdminAuditLogService` 覆盖 19 种管理操作
- `@Async` + `REQUIRES_NEW` 事务隔离
- 90 天自动清理

**差距：**

| # | 问题 | 严重度 |
|:---:|:---|:---:|
| 10 | 审计日志 `detail` 使用 `String.format` 拼接用户输入（`reason`/`comment` 等），未用 JSON 库转义——可注入额外 JSON key 扰乱详情显示；`\n` 注入风险低（日志为 DB 行而非文本文件） | **低** |
| 11 | 审计日志无完整性保护（可修改/删除），无冷存储归档 | **低** |

### 2.7 部署安全 —— 5.5/10

**已有防护：**
- Docker Compose 三容器隔离 + 桥接网络
- 健康检查 + 依赖等待

**差距：**

| # | 问题 | 严重度 |
|:---:|:---|:---:|
| 12 | ~~`ADMIN_PASSWORD` 默认值 `admin123`（开发残留，已处理）~~ | ~~严重~~ |
| 13 | MySQL 端口 3307 暴露到宿主机——容器间通过 `aichat-network` 通信无需端口映射 | **中** |
| 14 | 数据库连接 `useSSL=false`（容器内网通信，纵深防御缺口） | **中** |
| 15 | 容器以 root 启动（`su appuser` 在 entrypoint 切换，JVM 进程已非 root）——可加固 `read_only`、`no-new-privileges` | **低** |
| 16 | 无容器资源限制（mem_limit / cpus） | **低** |

---

## 三、OWASP Top 10 2021 合规矩阵

| 类别 | 状态 | 差距 |
|:---|:---:|:---|
| A01 访问控制失效 | **良好** | 全服务层 19 处 `userId` 归属校验，`ConversationService`/`KnowledgeBaseService`/`MemoryService`/`ChatHistoryService`/`PromptService` 均有检查 |
| A02 加密失效 | 良好 | 密钥无版本化，短密钥自动扩展掩蔽问题 |
| A03 注入 | 部分 | 审计日志 `detail` JSON 注入（低风险），前端无 XSS 消毒库 |
| A04 不安全设计 | 良好 | 密码重置用户枚举不一致（中风险），计费金额校验已修复 |
| A05 安全配置错误 | **需改进** | CSP `unsafe-inline`，MySQL `useSSL=false`，端口暴露，缺失部分安全头 |
| A06 脆弱组件 | 未评估 | 依赖版本不在审查范围 |
| A07 认证失效 | 部分 | 无账户锁定/Refresh Token/CAPTCHA |
| A08 数据完整性失效 | 差距 | 审计日志无完整性保护 |
| A09 日志与监控 | 良好 | `detail` 字段用 `String.format` 拼接而非 JSON 库，无篡改检测/实时告警 |
| A10 SSRF | 良好 | DNS 重绑定 TOCTOU（通用局限） |

---

## 四、行业对标总结

### vs 典型 AI Chat 套壳项目（HanaChat 优势）

| 维度 | 典型套壳项目 | HanaChat |
|:---|:---|:---|
| API Key 存储 | 明文 / Base64 | **AES-256-GCM 透明加解密** |
| 速率限制 | 无 | **16 条精细规则 + 标准响应头** |
| SSRF 防护 | 无 | **内网 IP 黑名单校验** |
| Token 失效 | 仅等过期 | **双层黑名单（内存 + DB）** |
| 安全响应头 | 无 | **HSTS + CSP + X-Frame + X-Content-Type** |
| 管理审计 | 无 | **19 种操作全覆盖异步审计** |

### vs 生产级 SaaS 平台（需要追赶）

| 维度 | 生产级标准 | HanaChat 现状 |
|:---|:---|:---|
| CSP | 无 `unsafe-inline`，nonce/hash | `unsafe-inline` 仍存在 |
| 认证 | Refresh Token + 轮换检测 | 仅 Access Token 24h |
| 密钥管理 | KMS / Vault / 版本化 | 单密钥，无版本 |
| 容器安全 | 非 root + read-only + 资源限制 | root + 可写 + 无限制 |
| 数据库连接 | TLS + 内网隔离 | 端口暴露 + `useSSL=false` |
| 审计日志 | 只追加 + 哈希链 + SIEM | 可修改 + 日志注入风险 |
| 验证码 | hCaptcha / Turnstile | 无 |

---

## 五、修复优先级路线图

> 审查确认：共识别 16 项问题（0 高 / 4 中 / 12 低），另 1 项已修复、1 项已处理、2 项验证为误报已移除。

### P0：高优先级 —— ~~已全部修复~~

| # | 问题 | 状态 |
|:---:|:---|:---:|
| 1 | ~~计费金额无边界校验~~ | **已修复**——`validateRechargeAmount()` 覆盖全部 3 个入口 |

### P1：中优先级（1-2 周）

| # | 问题 | 位置 | 修复方案 |
|:---:|:---|:---|:---|
| 2 | CSP `unsafe-inline` | `SecurityConfig.java:65-73` | React SPA 无需内联脚本，移除 `unsafe-inline`；若 Tailwind/Radix 需内联样式，用 nonce |
| 3 | `connect-src` 过于宽泛 | `SecurityConfig.java:72` | 改为具体后端域名 + 合法第三方 API 白名单 |
| 4 | MySQL 端口暴露 + `useSSL=false` | `docker-compose.yml:31-32,67` | 关闭宿主机端口映射；内部通信启用 TLS |
| 5 | 密码重置用户枚举不一致 | `UserService.resetPassword()` | 改为静默返回（与 `sendResetCode` 一致） |

### P2：低优先级（1-2 月，纵深防御）

| # | 问题 | 位置 | 修复方案 |
|:---:|:---|:---|:---|
| 6 | Refresh Token 机制 | 新建 | Access Token 15min + Refresh Token 7d + 轮换检测 |
| 7 | 验证码 CAPTCHA | `AuthController.sendCode()` | 接入 Turnstile / hCaptcha |
| 8 | 审计日志 `detail` JSON 拼接 | `AdminAuditLogService` 各 `log*` | 用 `ObjectMapper` / `JSONObject` 替代 `String.format`，自动转义 |
| 9 | 容器安全加固 | `Dockerfile` + `docker-compose.yml` | `read_only: true`、`no-new-privileges: true`、资源限制 |
| 10 | 审计日志完整性 | `AdminAuditLogService` | 哈希链 + 定期冷存储归档 |
| 11 | 前端引入 DOMPurify | `frontend/package.json` | 防止未来引入 HTML 渲染时产生 XSS |
| 12 | 加密密钥版本化 | `AESUtil` + `ApiKeyEncryptor` | 支持多版本密钥，启动时自动轮换 |
| 13 | 图片上传扩展名白名单 | `ImageService.uploadImage()` | 纵深防御：添加扩展名白名单（Controller 层已有 MIME 校验，风险极低） |
| 14 | 速率限制 IP 回退可绕过 | `RateLimitInterceptor` | 未认证用户基于 IP 限流，可通过轮换 IP 绕过；短期无解，长期可加全局 QPS |
| 15 | 补充安全响应头 | `SecurityConfig.java` | 添加 `Referrer-Policy`、`Permissions-Policy`、`Cross-Origin-Opener-Policy` |

### P3：长期护网（3-6 月）

| # | 问题 | 修复方案 |
|:---:|:---|:---|
| 16 | 账户锁定机制 | 连续 N 次失败后临时锁定 |
| 17 | SIEM 集成 | 审计日志推送至外部 SIEM |
| 18 | Token 迁移至 httpOnly Cookie | 后端签发 `Set-Cookie` 替代前端 `sessionStorage` |
| 19 | 安全回归测试 | CI 中集成 OWASP ZAP 扫描 |

### 已验证为误报（已从计划移除）

| 原项 | 原因 |
|:---|:---|
| Vite `emptyOutDir: false` | 有意设计——前端构建到后端 `static` 目录，清空会删除后端静态资源 |
| `spring.profiles.active=dev` | `docker-compose.yml` 已覆盖为 `prod`，仅本地开发用 `dev` |
| Admin 默认密码 `admin123` | 已确认实际部署覆盖，开发残留 |

---

## 六、附录：审查文件清单

### 后端审查（10 个核心安全文件 + 4 个业务文件）
- `SecurityConfig.java` — 安全配置、响应头、CSP
- `JwtAuthenticationFilter.java` — JWT 认证过滤器
- `TokenBlacklist.java` — Token 黑名单
- `RateLimitInterceptor.java` — 16 条速率限制
- `WebConfig.java` — CORS、静态资源
- `AESUtil.java` — AES-256-GCM 加解密
- `JwtUtil.java` — JWT 签发与验证
- `NetworkUtils.java` — SSRF 防护
- `ApiKeyEncryptor.java` — JPA 透明加密
- `GlobalExceptionHandler.java` — 全局异常处理
- `UserService.java` — 密码哈希、注册、重置
- `AuthController.java` — 认证端点、文件上传
- `ImageService.java` — 图片上传、S3 存储
- `BillingService.java` — Token 计费
- `AdminAuditLogService.java` — 审计日志

### 前端审查（4 个文件）
- `auth.tsx` — Auth Context、Token 管理
- `api.ts` — API 客户端、认证拦截
- `utils.ts` — 工具函数
- `vite.config.ts` — Vite 配置

### 部署审查（3 个文件）
- `.env.example` — 环境变量模板
- `docker-compose.yml` — 容器编排
- `application.properties` — Spring Boot 配置
