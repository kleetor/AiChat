# 代码审查剩余问题清单 (P1-P5)

> 审查时间: 2026-06-20  
> 已修复: P0 安全硬编码 / AES ECB→GCM  
> 以下为待处理问题

---

## P1 — 高危

### 1. `BillingService.reservations` 无过期清理

**位置:** [BillingService.java#L34](src/main/java/com/example/aichat/service/BillingService.java#L34)

**问题:** 预扣 Map 中若请求异常未走到 `deductTokens`（如网络断开、超时），预扣金额永久残留，导致用户余额永远少扣 `estimatedCost`。

**建议:** 定时任务清理超过 N 分钟未消费的预扣记录，或使用 Redis 带 TTL 的缓存替代。

---

### 2. 多处接口将异常消息暴露给前端

**位置:** 遍布多个 Controller 和 Service，例如：
- [UserService.java#L99-L104](src/main/java/com/example/aichat/service/UserService.java#L99-L104)
- [AuthController.java](src/main/java/com/example/aichat/controller/AuthController.java) 多处 `e.getMessage()`
- [FriendController.java](src/main/java/com/example/aichat/controller/FriendController.java) 多处 `e.getMessage()`

**问题:** `RuntimeException` 的 `getMessage()` 直接返回给前端，可能泄露数据库结构、API 地址、文件路径等内部信息。

**建议:** 统一通过 `GlobalExceptionHandler` 处理，Controller 层只抛异常不直接暴露消息。

---

### 3. 全局异常处理器泄露内部信息

**位置:** [GlobalExceptionHandler.java#L35-L41](src/main/java/com/example/aichat/config/GlobalExceptionHandler.java#L35-L41)

**问题:** `RuntimeException` handler 直接返回 `e.getMessage()`。

**建议:** 生产环境返回固定消息 `"服务器内部错误"`，详细信息记日志。

---

## P2 — 中危

### 4. `ChatService` 构造函数参数过多 (18个)

**位置:** [ChatService.java#L68-L93](src/main/java/com/example/aichat/service/ChatService.java#L68-L93)

**问题:** 违反单一职责原则，构造函数注入 18 个依赖。

**建议:** 拆分职责：
- `MessageBuilder` — 负责构建消息数组
- `StreamHandler` — 负责 SSE 流式响应
- 或使用 Facade 模式组合

---

### 5. `LLMService.chatSync()` 阻塞调用线程

**位置:** [LLMService.java#L106](src/main/java/com/example/aichat/service/LLMService.java#L106)

**问题:** `callAsyncWithUsage(...).join()` 先提交到线程池再立即阻塞等待，浪费线程池资源。

**建议:** 同步方法直接同步调用，不必经过线程池中转：

```java
public TokenUsageResult callSync(ArrayNode messages, ModelConfig config) {
    // 直接执行，不用 CompletableFuture
}
```

---

## P3 — 低危

### 6. 流式 SSE 中 `Thread.sleep(50)` 阻塞线程 忽略

**位置:** [ChatService.java#L429](src/main/java/com/example/aichat/service/ChatService.java#L429)

**问题:** 每个 chunk 硬编码 sleep 50ms，增加延迟。

**建议:** 使用 `SseEmitter` 自带的心跳机制或 `Sinks.Many` 流式推送。

---

### 7. `UserService` 中用户查找逻辑重复

**位置:** [UserService.java#L157-L180](src/main/java/com/example/aichat/service/UserService.java#L157-L180)

**问题:** `sendResetCode()` 和 `resetPassword()` 中 "先按用户名查，再按邮箱查" 的逻辑完全重复。

**建议:** 抽取 private 方法 `findUserByUsernameOrEmail(String key)`。

---

### 8. `FriendService.searchUsers()` PID 搜索逻辑混乱

**位置:** [FriendService.java#L40-L42](src/main/java/com/example/aichat/service/FriendService.java#L40-L42)

**问题:** 注释写"精确PID搜索"但实际调用的是 `findByUsername`，PID 字段根本没被搜索。

**建议:** 添加 `findByPid` 查询，或修正注释。

---

## P4 — 建议

### 9. `UserRepository.findByRole()` 死代码

**位置:** [UserRepository.java#L29](src/main/java/com/example/aichat/repository/UserRepository.java#L29)

**说明:** 该方法从未被调用，仅 `existsByRole()` 被使用。可直接删除。

---

### 10. `WebConfig.corsConfigurationSource()` Bean 冗余

**位置:** [WebConfig.java#L77-L83](src/main/java/com/example/aichat/config/WebConfig.java#L77-L83)

**说明:** `addCorsMappings()` 已全局配置 CORS，此 Bean 无引用。可删除。

---

### 11. `migrateModelConfigApiKeys()` 迁移方法残留

**位置:** [AichatApplication.java#L59-L106](src/main/java/com/example/aichat/AichatApplication.java#L59-L106)

**说明:** 代码注释已标明"迁移完成后可删除"。每次启动都会扫描全表检查加密状态，可删除。

---

## P5 — 可忽略

### 12. `PageController` 路由冗余

**位置:** [PageController.java#L10-L13](src/main/java/com/example/aichat/controller/PageController.java#L10-L13)

**说明:** `/` 和 `/chat` 返回相同模板 `index`，无功能影响。
