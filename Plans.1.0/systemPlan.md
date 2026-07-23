【后台管理系统开发计划 V1.0】
===================================

一、需求概述
-----------------------------------
为 AI Chat 项目构建一套后台管理系统（Admin Panel），实现管理员对用户、赞助审核、
模型配置、提示词社区、计费数据的统一管理。

当前项目已有完整的 C 端功能（用户注册登录、AI 聊天、计费扣费、赞助上传、提示词社区），
但缺少管理后台，所有管理操作（如审核赞助、调整余额、管理模型配置等）均需直接操作数据库，
效率低下且存在安全风险。

二、与现有系统的对接关系
-----------------------------------
【已有模块（不复改，只做管理层封装）】
- User 实体：id, username, email, password, pid, balance ✓
- TokenUsage 实体：消费记录 ✓
- RechargeOrder 实体：充值/赞助订单 ✓
- ModelConfig 实体：模型配置 ✓
- PromptsHub 实体：社区提示词 ✓
- Prompt 实体：个人提示词 ✓
- ChatMessage / Conversation 实体：聊天记录 ✓
- BillingService：计费服务（需新增管理员充值方法） ✓
- SecurityConfig：安全配置（需扩展管理员角色） ⚠️
- JwtUtil：JWT 工具（需扩展角色支持） ⚠️

【需要新增/改造的点】
- User 实体增加 role 字段（USER / ADMIN）
- JWT Token 增加角色信息
- SecurityConfig 增加 /api/admin/** 路径权限控制（仅 ADMIN 可访问）
- 新增 AdminController 系列（用户管理、赞助审核、模型管理、社区管理等）
- 新增管理后台前端页面（admin.html + admin.js + admin.css）
- BillingService 新增管理员手动充值方法

三、数据库变更
-----------------------------------
【3.1 User 表新增角色字段】
ALTER TABLE users ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER';
-- 取值: USER / ADMIN
-- 初始管理员账号通过数据库脚本手动插入，或通过配置文件指定

【3.2 RechargeOrder 表扩展（赞助审核用）】
ALTER TABLE recharge_orders ADD COLUMN sponsor_image_path VARCHAR(500);
ALTER TABLE recharge_orders ADD COLUMN review_status VARCHAR(20) DEFAULT 'PENDING';
ALTER TABLE recharge_orders ADD COLUMN review_comment VARCHAR(500);
ALTER TABLE recharge_orders ADD COLUMN reviewer_id BIGINT;
ALTER TABLE recharge_orders ADD COLUMN reviewed_at DATETIME;
-- review_status: PENDING / APPROVED / REJECTED
-- 参见 tokenPlan_up 第八章预留设计

【3.3 不需要新建独立的审核表】
复用 RechargeOrder 表即可，避免表膨胀。sponsorImagePath 在赞助上传时写入。

四、后端接口设计
-----------------------------------
所有管理接口统一前缀：/api/admin
权限控制：仅 role=ADMIN 的用户可访问

【4.1 管理员认证】
系统启动时检查是否存在管理员账号，若无则从配置文件创建初始管理员：
  application.properties 新增：
    admin.default.username=admin
    admin.default.password=admin123
    admin.default.email=admin@aichat.com

POST /api/admin/login
  请求：{ "username": "admin", "password": "admin123" }
  返回：{ "token": "...", "username": "admin", "role": "ADMIN" }
  说明：复用 UserService.login()，但校验角色是否为 ADMIN

【4.2 仪表盘统计】
GET /api/admin/dashboard
  返回：
  {
    "totalUsers": 1234,
    "totalConversations": 5678,
    "totalMessages": 90123,
    "totalRevenue": 12345.67,
    "todayNewUsers": 12,
    "todayMessages": 345,
    "pendingReviews": 5
  }

【4.3 用户管理】
GET /api/admin/users?page=0&size=20&keyword=&sortBy=id&order=desc
  返回：分页用户列表（含 id, username, email, pid, balance, role, 注册时间）
  支持按用户名/邮箱搜索

GET /api/admin/users/{id}
  返回：单个用户详情（含余额、消费统计、会话数、注册时间）

PUT /api/admin/users/{id}/balance
  请求：{ "amount": 100.00, "reason": "赞助审核通过" }
  说明：管理员手动增减余额（正数增加，负数扣减，记录到 RechargeOrder）
  操作写入 RechargeOrder（payChannel=MANUAL, status=SUCCESS）

PUT /api/admin/users/{id}/role
  请求：{ "role": "ADMIN" }
  说明：变更用户角色（提升/降级管理员）

PUT /api/admin/users/{id}/status
  请求：{ "enabled": false }
  说明：禁用/启用用户账号（需在 User 表添加 enabled 字段）

【4.4 赞助审核管理】
GET /api/admin/sponsor-reviews?page=0&size=20&status=PENDING
  返回：待审核/已审核的赞助订单列表
  包含：订单号、用户名、金额、赞助截图路径、审核状态、上传时间

PUT /api/admin/sponsor-reviews/{orderId}/approve
  请求：{ "tokens": 100.00, "comment": "审核通过" }
  说明：
    1. 更新 RechargeOrder.reviewStatus = APPROVED
    2. 调用 BillingService 增加用户余额
    3. 记录 reviewerId、reviewedAt、reviewComment

PUT /api/admin/sponsor-reviews/{orderId}/reject
  请求：{ "comment": "截图不清晰，请重新上传" }
  说明：更新 RechargeOrder.reviewStatus = REJECTED，记录拒绝原因

【4.5 模型配置管理】
GET /api/admin/model-configs
  返回：全部模型配置列表（含 apiKey 脱敏，仅显示前后各4位）

POST /api/admin/model-configs
  请求：{ "apiKey": "...", "apiUrl": "...", "modelName": "...", "displayName": "...",
          "inputTokenPrice": 0.001, "outputTokenPrice": 0.002 }
  返回：新建的模型配置

PUT /api/admin/model-configs/{id}
  请求：同 POST（全部字段可选更新）

DELETE /api/admin/model-configs/{id}
  说明：删除模型配置（需校验是否有活跃会话正在使用）

【4.6 提示词社区管理】
GET /api/admin/prompts-hub?page=0&size=20&keyword=
  返回：分页的社区提示词列表（支持按名称/内容搜索）

DELETE /api/admin/prompts-hub/{id}
  说明：管理员删除违规提示词

PUT /api/admin/prompts-hub/{id}/feature
  请求：{ "featured": true }
  说明：设置精选提示词（需在 PromptsHub 表添加 featured 字段）

【4.7 消费记录管理】
GET /api/admin/usage-records?page=0&size=20&userId=&startDate=&endDate=
  返回：全局消费记录（可按用户、时间范围筛选）

GET /api/admin/revenue-stats?startDate=2024-01-01&endDate=2024-12-31
  返回：{ "totalRevenue": 12345.67, "dailyStats": [...] }

【4.8 聊天记录管理】
GET /api/admin/conversations?page=0&size=20&userId=
  返回：会话列表（管理员可查看任意用户的会话）

GET /api/admin/conversations/{id}/messages?page=0&size=50
  返回：指定会话的聊天记录

五、后端实现细节
-----------------------------------
【5.1 安全改造 - SecurityConfig】
在 authorizeHttpRequests 中新增：
  .requestMatchers("/api/admin/login").permitAll()
  .requestMatchers("/api/admin/**").hasAuthority("ROLE_ADMIN")
  .requestMatchers("/admin", "/admin/**", "/admin.html", "/admin.js", "/admin.css").hasAuthority("ROLE_ADMIN")

【5.2 安全改造 - JwtUtil】
generateToken() 方法增加角色参数：
  public String generateToken(Long userId, String username, String role)
  JWT claims 增加 "role" 字段

getRoleFromToken() 新增方法

【5.3 安全改造 - JwtAuthenticationFilter】
doFilterInternal() 中设置认证时，增加从 JWT 提取角色并设置 GrantedAuthority：
  String role = jwtUtil.getRoleFromToken(token);
  List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));

【5.4 安全改造 - UserService】
login() 方法返回 AuthResponse 时增加 role 字段
register() 方法默认角色为 USER

【5.5 BillingService 新增方法】
@Transactional
public void adminRecharge(Long userId, BigDecimal amount, String reason, Long reviewerId)
  说明：管理员手动充值，创建 RechargeOrder（payChannel=MANUAL, status=SUCCESS），
        增加 User.balance，写入 TokenUsage（可选）

【5.6 新建 AdminController】
路径：src/main/java/com/example/aichat/controller/admin/
建议拆分为：
  - AdminAuthController.java      // 管理员登录
  - AdminDashboardController.java  // 仪表盘统计
  - AdminUserController.java       // 用户管理
  - AdminSponsorController.java    // 赞助审核
  - AdminModelConfigController.java // 模型配置管理
  - AdminPromptsHubController.java  // 提示词社区管理
  - AdminUsageController.java       // 消费记录/营收统计
  - AdminConversationController.java // 聊天记录管理
或者合并为一个 AdminController.java（根据项目规模决定）

【5.7 新建 AdminService】
路径：src/main/java/com/example/aichat/service/AdminService.java
  封装管理员业务逻辑：
  - getDashboardStats()
  - getUsers()
  - updateUserBalance()
  - approveSponsor()
  - rejectSponsor()
  - deletePromptHub()

六、前端页面设计
-----------------------------------
【6.1 创建管理后台页面】
文件：
  - src/main/resources/templates/admin.html
  - src/main/resources/static/admin.js
  - src/main/resources/static/admin.css

【6.2 路由配置】
在 PageController 中新增：
  @GetMapping("/admin")
  public String admin() { return "admin"; }

【6.3 页面布局设计】

┌─────────────────────────────────────────────┐
│  AI Chat 管理后台          admin | 退出      │
├──────────┬──────────────────────────────────┤
│ 📊 仪表盘 │                                  │
│ 👥 用户管理│        内容区域                   │
│ 💰 赞助审核│                                  │
│ 🤖 模型管理│                                  │
│ 📝 社区管理│                                  │
│ 📈 消费记录│                                  │
│ 💬 聊天记录│                                  │
├──────────┴──────────────────────────────────┤
│              页脚信息                         │
└─────────────────────────────────────────────┘

【6.4 仪表盘页面】
- 顶部4个统计卡片：总用户数、今日新增、待审核赞助、总营收
- 下方折线图/柱状图：近7天消息量、营收趋势（可选用 Chart.js 轻量图表库）

【6.5 用户管理页面】
- 搜索栏：搜索用户名/邮箱
- 表格：用户名 | 邮箱 | PID | 余额 | 角色 | 状态 | 操作
- 操作按钮：编辑余额、变更角色、禁用/启用
- 余额编辑弹窗：输入金额、填写原因

【6.6 赞助审核页面】
- 状态筛选：全部 / 待审核 / 已通过 / 已拒绝
- 表格：用户名 | 订单号 | 金额 | 截图 | 状态 | 上传时间 | 操作
- 截图列：缩略图，点击可放大查看
- 操作：通过（输入发放金额 + 备注）/ 拒绝（输入拒绝原因）

【6.7 模型管理页面】
- 表格：显示名称 | 模型名 | API URL | 输入价格 | 输出价格 | 操作
- 新增/编辑弹窗：配置所有字段
- API Key 脱敏显示

【6.8 社区管理页面】
- 搜索栏
- 表格/卡片：名称 | 内容摘要 | 上传用户 | 点赞数 | 图片 | 操作
- 操作：删除违规提示词、设置精选

【6.9 消费记录页面】
- 筛选栏：用户ID、开始日期、结束日期
- 表格：用户名 | 模型 | 输入Tokens | 输出Tokens | 消费金额 | 时间
- 汇总行：总消费金额

【6.10 聊天记录页面】
- 筛选栏：用户ID
- 会话列表（点击展开）
- 聊天消息详情

七、样式设计
-----------------------------------
管理后台整体风格：
- 侧边栏：深色背景（#1f2937），白色文字，宽度 220px
- 顶部栏：白色背景，浅灰边框，高度 56px
- 内容区：浅灰背景（#f3f4f6），白色卡片
- 表格：斑马条纹，hover 高亮
- 按钮：主色蓝色（#3b82f6），危险红色（#ef4444），成功绿色（#22c55e）
- 模态框：居中弹出，半透明黑色遮罩
- 响应式：最小宽度 1024px，移动端可折叠侧边栏

八、开发步骤
-----------------------------------
Step 1.  数据库变更：User 表增加 role、enabled 字段；RechargeOrder 表增加审核字段
Step 2.  安全改造：JwtUtil 增加角色支持，SecurityConfig 增加 ADMIN 权限控制
Step 3.  后端：AdminAuthController 管理员登录
Step 4.  后端：AdminDashboardController 仪表盘统计
Step 5.  后端：AdminUserController 用户管理 CRUD
Step 6.  后端：AdminSponsorController 赞助审核（通过/拒绝）
Step 7.  后端：AdminModelConfigController 模型配置管理
Step 8.  后端：AdminPromptsHubController 社区提示词管理
Step 9.  后端：AdminUsageController 消费记录查询
Step 10. 后端：AdminConversationController 聊天记录查询
Step 11. 后端：AdminService 统一管理业务逻辑
Step 12. 前端：admin.html 页面结构 + 侧边栏路由
Step 13. 前端：admin.js 仪表盘 / 用户管理 / 赞助审核 / 模型管理
Step 14. 前端：admin.css 管理后台样式
Step 15. 前端：admin.js 社区管理 / 消费记录 / 聊天记录
Step 16. 初始管理员创建脚本 + 全流程测试

九、关键技术要点
-----------------------------------
【9.1 安全性】
- 管理员登录独立接口，不混用 C 端登录
- API 请求双重校验：JWT 角色 + SecurityConfig 路径权限
- API Key 脱敏：前后各显示4位，中间用 **** 替代
- 管理员操作日志（可选，后续版本添加 @AuditLog 注解记录关键操作）

【9.2 数据一致性】
- 手动充值使用 @Transactional 保证余额变更 + 订单创建原子性
- 赞助审核通过后，同时更新 RechargeOrder 状态 + 增加用户余额

【9.3 前端技术选型】
- 不做前后端分离，复用 Thymeleaf + 原生 JS（保持与现有项目一致）
- 图表可选 Chart.js CDN 引入（轻量、无需构建工具）
- 不做 SPA 路由，用 Tab 切换实现页面内导航

【9.4 扩展预留】
- 管理员操作日志表（admin_audit_logs）预留
- 系统配置表（system_configs）预留（可配置注册开关、全局公告等）
- 多级管理员角色（SUPER_ADMIN / ADMIN / MODERATOR）预留
- 数据导出（CSV/Excel）功能预留接口

十、与现有 Plans 的关联
-----------------------------------
- tokenPlan：消费记录查询（4.7）基于 TokenUsage 表，计费数据在此展示
- tokenPlan_up：赞助审核（4.4）直接对接赞助上传功能，完成审核闭环
- PrompthubPlan：社区管理（4.6）管理提示词社区内容，删除违规项
- Plan：整体项目计划中"三、后台系统"部分的具体实现
