【计费系统开发计划 V1.0】
===================================

一、需求回顾
-----------------------------------
1. 让大模型返回 token 用量
2. 根据 token 用量，针对用户使用的模型进行计费
3. 用户在设置界面能够查看到自己的余额
4. 预留一个充值按钮，未来进行支付功能实现

二、数据模型设计（数据库变更）
-----------------------------------
【2.1 用户余额 - User 实体扩展】
位置：src/main/java/com/example/aichat/model/User.java
新增字段：
  - BigDecimal balance  // 用户余额（单位：元，保留2位小数），默认值 0.00
说明：新用户注册时初始余额为 0，可通过管理员后台或充值按钮手动调整。

【2.2 模型计费配置 - ModelConfig 实体扩展】
位置：src/main/java/com/example/aichat/model/ModelConfig.java
新增字段：
  - BigDecimal inputTokenPrice   // 输入单价（元 / 1k tokens），默认值可配置
  - BigDecimal outputTokenPrice  // 输出单价（元 / 1k tokens），默认值可配置
  - String displayName           // 模型显示名称（可选，前端展示时更友好）
说明：
  - 当前 ModelConfig 只存 apiKey/apiUrl/modelName，需要在数据库执行 ALTER TABLE 添加新列
  - 不同模型有不同定价（例如 deepseek-chat 便宜、deepseek-reasoner 贵）
  - 定价策略以"每 1000 tokens"为单位，符合业内惯例

【2.3 消费记录 - 新建 TokenUsage 实体】
位置：src/main/java/com/example/aichat/model/TokenUsage.java
字段设计：
  - Long id                    // 主键
  - Long userId                // 关联用户（@ManyToOne 或直接存 ID）
  - Long conversationId        // 关联会话（方便追溯）
  - Long modelConfigId         // 使用的模型配置
  - String modelName           // 快照：模型名（模型被删后仍保留记录）
  - Long inputTokens           // 输入 token 数
  - Long outputTokens          // 输出 token 数
  - Long totalTokens           // 总 token 数
  - BigDecimal costAmount      // 本次消费金额（元）
  - BigDecimal balanceBefore   // 消费前余额（快照）
  - BigDecimal balanceAfter    // 消费后余额（快照）
  - LocalDateTime createdAt    // 创建时间
对应新建：
  - repository/TokenUsageRepository.java
  - 数据库表名：token_usages（JPA 自动建表）

【2.4 充值订单 - 新建 RechargeOrder 实体】
位置：src/main/java/com/example/aichat/model/RechargeOrder.java
字段设计：
  - Long id                   // 主键
  - Long userId               // 关联用户
  - String orderNo            // 业务订单号（唯一，给第三方支付用）
  - BigDecimal amount         // 充值金额（元）
  - String status             // 订单状态：PENDING / SUCCESS / FAILED / CANCELLED
  - String payChannel         // 支付渠道（预留：ALIPAY / WECHAT / MANUAL 等）
  - String thirdPartyOrderId  // 第三方支付订单号（预留）
  - LocalDateTime createdAt
  - LocalDateTime paidAt
对应新建：
  - repository/RechargeOrderRepository.java
  - 数据库表名：recharge_orders

三、后端接口设计
-----------------------------------
【3.1 获取当前用户余额 & 消费记录】
新建 controller/BillingController.java（或在 AuthController 中扩展）

GET  /api/billing/balance
  返回：{ "balance": 19.80, "totalSpent": 12.50, "totalTokens": 52300 }
  权限：已登录用户（JWT 认证）

GET  /api/billing/usage-records?page=0&size=20
  返回：分页的 TokenUsage 记录列表
  包含：时间、模型、输入 tokens、输出 tokens、金额

【3.2 充值接口（预留，当前为"模拟充值"）】
POST /api/billing/recharge
  请求：{ "amount": 100.00, "payChannel": "MANUAL" }
  返回：{ "orderNo": "RC202412010001", "status": "SUCCESS", "balance": 100.00 }
  说明：
    - 当前阶段直接入账（相当于测试模式），未来替换为调用微信/支付宝
    - 创建 RechargeOrder 记录，然后增加 User.balance
    - 返回的 orderNo 用于未来对接真实支付网关时的回调匹配

【3.3 更新 /api/auth/me 返回值】
在 controller/AuthController.java 的 getUserInfo() 中，增加 balance 字段：
  { "username": "...", "email": "...", "pid": "...", "balance": 19.80 }

四、核心计费逻辑（ChatService 集成）
-----------------------------------
位置：src/main/java/com/example/aichat/service/BillingService.java（新建）

【4.1 BillingService 核心方法】
- checkAndReserveBalance(userId, modelConfigId, estimatedInputTokens)
  : 扣费前检查余额是否足够（按预估输入 tokens * 2 倍预扣，避免中间失败）
  : 余额不足时直接抛出异常，阻止调用大模型
  : 这是"保护点 1"——在发起 API 请求前先拦截

- deductTokens(userId, modelConfigId, inputTokens, outputTokens, conversationId)
  : 实际扣费：从 usage 中取出真实 input/output tokens
  : 计算公式：cost = (inputTokens * inputTokenPrice + outputTokens * outputTokenPrice) / 1000
  : 使用 @Transactional 保证"扣余额 + 写消费记录"原子性
  : 写入 TokenUsage 记录

【4.2 ChatService 改造 - 获取 token 用量】
文件：service/ChatService.java

改造点 A：callDeepSeekAsync（非流式聊天）
  原代码第 375 行：只读取 content 文本
  修改为：同时解析 responseBody 中的 usage 字段：
    JsonNode usage = root.get("usage");
    long promptTokens = usage.get("prompt_tokens").asLong();
    long completionTokens = usage.get("completion_tokens").asLong();
    long totalTokens = usage.get("total_tokens").asLong();
  调用 chatAndSave 后调用 billingService.deductTokens(...)

改造点 B：streamDeepSeek（流式聊天）
  DeepSeek 流式响应中，大多数 chunk 不含 usage，只有末尾会带一个带 usage 的事件
  修改 while 循环：
    - 每个 payload 都尝试读取 root.get("usage")
    - 如果存在 usage，记录下来（累计 prompt_tokens/completion_tokens）
  在流结束（[DONE] 之后）调用 billingService.deductTokens(...)
  注意：如果 API 完全不返回 usage（部分自建模型可能这样），使用"字符数估算"作为兜底
    估算规则：inputTokens ≈ userMessage.length() * 1.3，outputTokens ≈ fullResponse.length() * 1.3

【4.3 对话入口前置检查】
在 ChatController.java 的 chat() / chatStream() 方法中，先调用 billingService.checkAndReserveBalance()
  - 余额不足 → 返回 HTTP 402 + "余额不足，请充值"

五、前端页面改造
-----------------------------------
【5.1 头部 / 设置页余额显示】
文件：src/main/resources/templates/index.html

改造 A：头部 user-info 区域新增余额角标
  <span id="balanceIndicator" class="balance-indicator">💰 <span id="balanceAmount">0.00</span> 元</span>

改造 B：设置页 tab 扩展（新增"钱包"tab）
  <div class="settings-tabs">
    <button class="tab-btn active" data-tab="profile">👤 个人信息</button>
    <button class="tab-btn" data-tab="wallet">💰 我的钱包</button>
  </div>

  <div class="tab-content" id="wallet-tab">
    <div class="wallet-card">
      <h3>当前余额</h3>
      <div class="balance-big">¥ <span id="settingsBalance">0.00</span></div>
      <button id="btnRecharge" class="btn-primary recharge-btn">💳 充值</button>
    </div>
    <h4>消费记录</h4>
    <div id="usageRecords" class="usage-records"></div>
  </div>

【5.2 充值模态框（预留）】
  <div class="modal-overlay" id="rechargeModal">
    <div class="modal" style="max-width: 420px;">
      <h2>💳 充值</h2>
      <p style="color:#6b7280; font-size:13px;">选择充值金额：</p>
      <div class="recharge-options">
        <button class="recharge-amount" data-amount="10">¥ 10</button>
        <button class="recharge-amount" data-amount="50">¥ 50</button>
        <button class="recharge-amount" data-amount="100">¥ 100</button>
        <button class="recharge-amount" data-amount="500">¥ 500</button>
      </div>
      <div class="form-group" style="margin-top:12px;">
        <label>自定义金额</label>
        <input type="number" id="customAmount" placeholder="或输入自定义金额" min="1" step="1">
      </div>
      <p style="color:#9ca3af; font-size:12px; margin-top:8px;">
        ⚠️ 当前为测试阶段，点击"确认充值"将模拟增加余额。<br>
        真实支付通道（微信/支付宝）将在后续版本接入。
      </p>
      <button class="btn-primary" id="btnRechargeConfirm">确认充值</button>
      <div id="rechargeError" class="error-msg"></div>
    </div>
  </div>

【5.3 前端 JS 扩展】
文件：src/main/resources/static/app.js

新增/扩展函数：
- loadUserInfo()：新增 balance 字段读取
- loadBalance()：调 GET /api/billing/balance，刷新头部角标
- loadUsageRecords()：调 GET /api/billing/usage-records，渲染消费记录列表
- showRechargeModal()：弹出充值模态框
- doRecharge(amount)：调 POST /api/billing/recharge，成功后刷新余额
- handleChatInsufficientBalance()：收到 HTTP 402 时弹出"余额不足"提示

六、数据库脚本（建议执行的 SQL）
-----------------------------------
-- （由于 JPA ddl-auto=update，以下迁移由 Hibernate 自动处理，
-- 但生产环境建议使用 Flyway/Liquibase）

ALTER TABLE users ADD COLUMN balance DECIMAL(12,2) NOT NULL DEFAULT 0.00;
ALTER TABLE model_configs
  ADD COLUMN display_name VARCHAR(100),
  ADD COLUMN input_token_price DECIMAL(12,6) NOT NULL DEFAULT 0.001000,
  ADD COLUMN output_token_price DECIMAL(12,6) NOT NULL DEFAULT 0.002000;

CREATE TABLE token_usages ( ... );        -- 由 JPA 自动建
CREATE TABLE recharge_orders ( ... );     -- 由 JPA 自动建

-- 给所有现有用户一个测试初始余额（开发阶段可选）
UPDATE users SET balance = 100.00;

七、接口访问控制
-----------------------------------
文件：src/main/java/com/example/aichat/config/SecurityConfig.java

新增放行的 API：
  /api/billing/**  （仅对已登录用户，JWT filter 已生效）
不需要在 SecurityConfig 的 permitAll() 列表中额外添加，
因为 JwtAuthenticationFilter 会拦截带 Authorization 头的请求；
未登录调用 BillingController → Authentication 为 null → 401。

八、开发步骤优先级
-----------------------------------
Step 1. 数据模型扩展
  - 给 User 加 balance 字段（BigDecimal, default 0）
  - 给 ModelConfig 加 inputTokenPrice / outputTokenPrice / displayName
  - 新建 TokenUsage 实体 + Repository
  - 新建 RechargeOrder 实体 + Repository

Step 2. BillingService 核心
  - checkAndReserveBalance（余额检查）
  - deductTokens（原子扣费 + 写记录）
  - recharge（充值入账 + 写订单）

Step 3. ChatService 集成 token 用量解析
  - callDeepSeekAsync 解析 usage
  - streamDeepSeek 流式追踪 usage
  - 两条路径最终都调用 deductTokens

Step 4. ChatController 前置拦截
  - 聊天接口调用 checkAndReserveBalance
  - 余额不足 → 返回 402

Step 5. BillingController 接口
  - GET /api/billing/balance
  - GET /api/billing/usage-records
  - POST /api/billing/recharge

Step 6. 前端改造
  - 头部余额角标
  - 设置页"钱包" tab + 充值按钮
  - 充值模态框
  - app.js 中新增 API 调用逻辑

Step 7. 测试验证
  - 新用户注册 → 余额为 0
  - 手动充值 100 → 余额 100
  - 发送一条消息 → token 被记录 + 余额减少
  - 余额为 0 → 聊天被拒绝 + 提示充值
  - 查看消费记录 → 数据正确显示

九、未来扩展（不在本期范围）
-----------------------------------
- 真实支付：对接微信/支付宝 SDK
  : RechargeOrder 的 payChannel / thirdPartyOrderId 已预留
  : 充值成功由支付回调（webhook）触发余额增加
- 套餐卡 / 月卡：独立的 CreditPackage 实体
- 管理员后台：给用户手动加余额、查看全站消费
- 消费告警：余额低于阈值时发邮件提醒
- Token 使用图表：按日/周/月统计
- 模型定价配置页面：管理员可在后台修改各模型的单价

十、关键注意事项
-----------------------------------
【10.1 计费精度】
  - 所有金额使用 java.math.BigDecimal，禁止使用 float/double
  - 保留 6 位小数存储单价（如 ¥0.001500 / 1k tokens），最终展示时四舍五入到 2 位
  - 计算公式中除法最后执行，避免误差累积

【10.2 事务安全】
  - deductTokens 必须 @Transactional，确保扣余额 + 写 TokenUsage 同成功或同失败
  - 余额扣除使用乐观锁或"先读后写但在数据库行加锁"，避免并发请求下余额变负

【10.3 用户余额为 0】
  - 新用户初始余额为 0，必须在 UI 中明显提示"请充值后使用"
  - 聊天按钮在余额不足时显示禁用 + "请充值" tooltip

【10.4 异常兜底】
  - 大模型 API 返回但不含 usage：用字符数估算 tokens 并记录日志告警
  - 扣费失败：聊天失败但不扣余额，提示"计费失败"
  - 扣费成功但聊天失败：不应该发生（先检查余额再调用模型），但需日志记录
