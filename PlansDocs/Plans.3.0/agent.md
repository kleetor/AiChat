# HanaChat - Agent 重要事务提醒

> 最后更新：2026-06-30
> 基于项目全面评估、技术债务清理、数据库诊断的结果

---

## 🔴 必须在任何新功能之前修复

- [ ] **AESUtil** — 密钥截断为 16 字节，AES-256 降级为 AES-128，修复并兼容旧密文
- [ ] **BillingService** — 预扣使用 ConcurrentHashMap，服务重启导致用户余额永久丢失，改为 DB 行锁
- [ ] **Flyway 引入** — 当前仅依赖 `ddl-auto=update`，无变更历史，无法回滚
- [ ] **CORS** — `allowedOriginPatterns("*")` 过于宽松，改为读取 `.env` 中 `ALLOWED_ORIGINS`

---

## 🟠 功能开发前应完成

- [ ] **社区 API 速率限制** — 评论 5次/分钟、点赞 10次/分钟、上传 20次/天
- [ ] **DTO 输入验证** — 添加 `@NotBlank/@Email/@Size`，Controller 加 `@Valid`
- [ ] **JWT Secret 校验** — 不足 32 字节时自动 SHA-256 填充并 WARN，防止启动 crash
- [ ] **ChatService 拆分** — 抽取 MessageContextBuilder / ChatStreamService / ChatPostProcessor
- [ ] **ChromaDB 服务统一** — 合并 ChromaDBService 和 MemoryChromaService 重复代码
- [ ] **缓存层引入** — Spring Cache + Caffeine（modelConfigs/prompts/kbList/billingInfo）
- [ ] **前端 Zustand 状态管理** — App.tsx 已近 600 行，拆分为独立 store（auth/chat/prompt/kb/billing/friend）

---

## 🟡 可与功能开发并行

- [ ] **邮件验证码持久化** — 从 ConcurrentHashMap 迁移到 email_codes 表
- [ ] **@Autowired → 构造器注入** — 优先改造需要写单元测试的 Service
- [ ] **异常吞没消除** — 空 catch 块至少添加 `log.warn()`，前端静默失败加 toast
- [ ] **API 版本化** — 新功能统一 `/api/v1/` 前缀
- [ ] **SSE 流优化** — 动态 sleep 替代硬编码 50ms，minFlushSize 从 4 调至 10

---

## 🟢 后续处理

- [ ] **JWT → httpOnly Cookie** — 当前存 localStorage 有 XSS 风险，需 CORS+credentials 配合
- [ ] **KbDocument.s3Key → filePath** — 字段命名修正（实际存本地路径非 S3 key）
- [ ] **字符集统一** — 5 表 utf8mb4_unicode_ci → utf8mb4_0900_ai_ci（与其余 12 表一致）
- [ ] **移动端适配基础** — Sidebar 滑出式、最小触摸 44px（[详见](file:///c:/Users/makot/Desktop/aichat/Plans.2.0/mobile-adaptation-plan.md)）

---

## ✅ 已完成 — 数据库优化（2026-06-30）

| 项目 | 内容 |
|------|------|
| 新增索引 | 10 个（含 `user_likes` 1 个 UNIQUE），全表扫描从 6→0 |
| 服务器参数 | `long_query_time` 10s→1s、`binlog_expire` 0→7天、`flush_log` 1→2 |

> 详细：[DBOptimization](file:///c:/Users/makot/Desktop/aichat/Plans.3.0/DBOptimization)

---

## 📋 相关文档

| 文件 | 内容 |
|------|------|
| [PromptHUB](file:///c:/Users/makot/Desktop/aichat/Plans.3.0/PromptHUB) | 三层提示词 + PromptHUB 社区完整计划 |
| [TechDebtCleanup](file:///c:/Users/makot/Desktop/aichat/Plans.3.0/TechDebtCleanup) | 24 项技术债务清理（3 轮执行） |
| [DBOptimization](file:///c:/Users/makot/Desktop/aichat/Plans.3.0/DBOptimization) | 数据库诊断 & 优化记录 |
| [HanaChat.txt](file:///c:/Users/makot/Desktop/aichat/HanaChat.txt) | 项目架构概述 |
| [code-review-issues.md](file:///c:/Users/makot/Desktop/aichat/Plans.2.0/code-review-issues.md) | 2026-06-15 代码审查报告 |

---

## 📊 项目健康分（2026-06-30 评估）

| 维度 | 得分 |
|------|------|
| 功能完整性 | 85 |
| 性能优化 | 62 → **68** (DB索引优化) |
| 可维护性 | 55 |
| 安全性 | 65 |
| 用户体验 | 72 |
