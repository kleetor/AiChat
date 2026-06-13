# AI Chat 智能聊天平台

基于 Spring Boot 4.0.6 + Java 17 构建的多模型 AI 聊天应用，支持流式对话、多会话管理、Token 计费、提示词社区、联网搜索和完整的管理后台。

## 功能特性

- **用户认证** — 邮箱验证码注册/登录，自动生成 6 位 PID 标识
- **多模型支持** — 动态配置多个 AI 模型（DeepSeek、Grok、Gemini、GPT 等），支持 API Key 管理
- **多会话管理** — 创建、切换、删除多个对话会话
- **上下文对话** — 自动保留最近 30 条历史消息作为上下文
- **流式响应** — 基于 SSE 实时流式输出，体验更流畅
- **提示词管理** — 自定义 System Prompt，塑造 AI 角色行为
- **提示词社区** — 用户可上传/分享提示词，支持精选和图片展示
- **Token 计费系统** — 按输入/输出 Token 分别计价，自动扣费，余额管理
- **联网搜索** — 集成百度千帆搜索 API，对话中可获取实时信息
- **赞助/充值系统** — 用户提交充值订单，管理员审核后到账
- **管理后台** — 仪表盘统计、用户管理、模型配置、会话查看、赞助审核、用量统计、收入分析
- **Docker 部署** — 一键 Docker Compose 启动 MySQL + 应用容器

## 技术栈

| 分类 | 技术 | 版本 |
| :--- | :--- | :--- |
| 后端框架 | Spring Boot | 4.0.6 |
| JDK | Eclipse Temurin | 17 |
| 数据库 | MySQL | 8.0+ |
| ORM | Spring Data JPA | - |
| 安全认证 | Spring Security + JWT | 0.12.6 |
| 模板引擎 | Thymeleaf | - |
| 邮件服务 | Spring Mail (QQ SMTP) | - |
| HTTP 客户端 | Apache HttpClient 5 | 5.3 |
| 构建工具 | Maven | 3.8+ |
| 前端 | HTML / CSS / JavaScript | - |
| 流式响应 | SSE (Server-Sent Events) | - |
| 容器化 | Docker / Docker Compose | - |

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.8+
- MySQL 8.0+
- Docker & Docker Compose（可选）

### 方式一：本地启动

#### 1. 创建数据库

```sql
CREATE DATABASE ai_chat_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

#### 2. 配置环境变量

创建 `.env` 文件（或在系统环境变量中设置）：

```env
DB_PASSWORD=your_db_password

# JWT 密钥（至少 32 位）
JWT_SECRET=your_jwt_secret_key_here_at_least_32_chars

# 百度千帆搜索 API（可选）
QIANFAN_API_KEY=your_qianfan_api_key

# QQ 邮箱配置（用于发送验证码）
MAIL_USERNAME=your_email@qq.com
MAIL_PASSWORD=your_email_auth_code

# 管理员默认账号
ADMIN_USERNAME=admin
ADMIN_PASSWORD=admin123
ADMIN_EMAIL=admin@aichat.com
```

#### 3. 编译运行

```bash
mvn spring-boot:run
```

#### 4. 访问应用

- **用户端**: http://localhost:8080
- **管理后台**: http://localhost:8080/admin

### 方式二：Docker Compose 部署

```bash
# 创建 .env 文件（同上）后执行
docker compose up -d
```

Docker Compose 会自动创建 MySQL 8.0 容器和应用容器，MySQL 数据持久化存储在 Docker Volume 中。

## 页面路由

| 路径 | 页面 | 说明 |
| :--- | :--- | :--- |
| `/` | 聊天主页面 | 对话、提示词管理、模型切换 |
| `/chat` | 聊天主页面 | 同上 |
| `/prompt-hub` | 提示词社区 | 浏览、搜索、上传提示词 |
| `/admin` | 管理后台 | 仪表盘、用户、模型、赞助审核等 |

## API 接口

### 认证接口 (`/api/auth`)

| 方法 | 路径 | 说明 |
| :--- | :--- | :--- |
| POST | `/api/auth/send-code` | 发送邮箱验证码 |
| POST | `/api/auth/register` | 注册（需要验证码） |
| POST | `/api/auth/login` | 登录 |
| GET | `/api/auth/me` | 获取当前用户信息 |
| POST | `/api/auth/change-password` | 修改密码 |
| POST | `/api/auth/verify-password` | 验证密码 |

### 会话接口 (`/api/conversations`)

| 方法 | 路径 | 说明 |
| :--- | :--- | :--- |
| GET | `/api/conversations` | 获取会话列表 |
| POST | `/api/conversations` | 创建新会话 |
| DELETE | `/api/conversations/{id}` | 删除会话 |

### 聊天接口 (`/api`)

| 方法 | 路径 | 说明 |
| :--- | :--- | :--- |
| POST | `/api/chat/{conversationId}` | 发送消息（同步返回） |
| POST | `/api/chat/{conversationId}/stream` | 发送消息（SSE 流式返回） |
| GET | `/api/chat/{conversationId}/history` | 获取聊天历史 |

请求体示例：

```json
{
  "message": "你好",
  "promptId": 1,
  "modelConfigId": 1,
  "webSearchEnabled": false
}
```

### 提示词接口 (`/api/prompts`)

| 方法 | 路径 | 说明 |
| :--- | :--- | :--- |
| GET | `/api/prompts` | 获取当前用户的提示词列表 |
| POST | `/api/prompts` | 创建提示词 |
| PUT | `/api/prompts/{id}` | 更新提示词 |
| DELETE | `/api/prompts/{id}` | 删除提示词 |

### 提示词社区 (`/api/prompts-hub`)

| 方法 | 路径 | 说明 |
| :--- | :--- | :--- |
| GET | `/api/prompts-hub` | 获取所有社区提示词 |
| GET | `/api/prompts-hub/{id}` | 获取单个提示词详情 |
| GET | `/api/prompts-hub/user` | 获取当前用户上传的提示词 |
| POST | `/api/prompts-hub/upload` | 上传提示词（含图片） |
| POST | `/api/prompts-hub/{id}/image` | 上传提示词封面图片 |

### 模型配置接口 (`/api/model-configs`)

| 方法 | 路径 | 说明 |
| :--- | :--- | :--- |
| GET | `/api/model-configs` | 获取所有可用模型 |
| GET | `/api/model-configs/{id}` | 获取单个模型配置 |

### 联网搜索 (`/api/search`)

| 方法 | 路径 | 说明 |
| :--- | :--- | :--- |
| POST | `/api/search/web` | 联网搜索（JSON 请求体） |
| GET | `/api/search/web` | 联网搜索（Query 参数） |

参数：`query`（搜索词）、`summary`（是否摘要，默认 true）、`freshness`（时效性，默认 noLimit）、`count`（结果数，默认 10）

### 计费接口 (`/api/billing`)

| 方法 | 路径 | 说明 |
| :--- | :--- | :--- |
| GET | `/api/billing/balance` | 获取余额、总消费、总 Token |
| GET | `/api/billing/usage-records` | 获取用量记录（分页） |
| POST | `/api/billing/submit-order` | 提交赞助/充值订单（含付款截图） |
| GET | `/api/billing/orders` | 获取当前用户的订单列表 |

### 管理后台 (`/api/admin`)

| 方法 | 路径 | 说明 |
| :--- | :--- | :--- |
| POST | `/api/admin/login` | 管理员登录 |
| GET | `/api/admin/dashboard` | 仪表盘统计数据 |
| GET | `/api/admin/users` | 用户列表（关键词搜索、排序、分页） |
| GET | `/api/admin/users/{id}` | 用户详情及使用统计 |
| PUT | `/api/admin/users/{id}/balance` | 调整用户余额 |
| PUT | `/api/admin/users/{id}/role` | 修改用户角色 |
| PUT | `/api/admin/users/{id}/status` | 启用/禁用用户 |
| GET | `/api/admin/model-configs` | 获取所有模型配置 |
| POST | `/api/admin/model-configs` | 创建模型配置 |
| PUT | `/api/admin/model-configs/{id}` | 更新模型配置 |
| DELETE | `/api/admin/model-configs/{id}` | 删除模型配置 |
| GET | `/api/admin/conversations` | 会话列表（按用户筛选） |
| GET | `/api/admin/conversations/{id}/messages` | 查看会话消息 |
| GET | `/api/admin/prompts-hub` | 社区提示词管理 |
| DELETE | `/api/admin/prompts-hub/{id}` | 删除社区提示词 |
| PUT | `/api/admin/prompts-hub/{id}/feature` | 设置/取消精选 |
| GET | `/api/admin/sponsor-reviews` | 赞助审核列表 |
| PUT | `/api/admin/sponsor-reviews/{id}/approve` | 通过赞助（设置到账 Token 数） |
| PUT | `/api/admin/sponsor-reviews/{id}/reject` | 拒绝赞助 |
| GET | `/api/admin/usage-records` | 用量记录（按用户、时间筛选） |
| GET | `/api/admin/revenue-stats` | 收入统计 |

## 数据库表结构

| 表名 | 说明 | 关键字段 |
| :--- | :--- | :--- |
| `users` | 用户表 | id, username, email, password, pid(6位标识), balance, role, enabled |
| `conversations` | 会话表 | id, user_id, title |
| `chat_messages` | 消息表 | id, conversation_id, user_id, user_message, ai_reply, timestamp |
| `prompts` | 提示词表 | id, user_id, name, content |
| `prompts_hub` | 社区提示词 | id, user_id, name, content, user_message, image_url, featured, likes |
| `model_configs` | 模型配置表 | id, api_url, api_key, model_name, display_name, input_token_price, output_token_price |
| `token_usage` | Token 用量表 | id, user_id, model_name, input_tokens, output_tokens, cost_amount |
| `recharge_orders` | 充值订单表 | id, user_id, amount, tokens, screenshot_url, status, comment |

## 环境变量说明

| 变量 | 必填 | 说明 |
| :--- | :--- | :--- |
| `DB_PASSWORD` | 是 | MySQL 数据库密码 |
| `JWT_SECRET` | 是 | JWT 签名密钥（至少 32 位） |
| `QIANFAN_API_KEY` | 否 | 百度千帆搜索 API Key |
| `MAIL_USERNAME` | 否 | QQ 邮箱地址（验证码发送） |
| `MAIL_PASSWORD` | 否 | QQ 邮箱授权码 |
| `ADMIN_USERNAME` | 否 | 管理员用户名（默认 admin） |
| `ADMIN_PASSWORD` | 否 | 管理员密码（默认 admin123） |
| `ADMIN_EMAIL` | 否 | 管理员邮箱 |

## 项目结构

```plaintext
src/
├── main/
│   ├── java/com/example/aichat/
│   │   ├── config/              # Spring 配置类（安全、JWT、Web、异常处理）
│   │   ├── controller/
│   │   │   ├── admin/           # 管理后台控制器
│   │   │   └── ...              # 用户端控制器
│   │   ├── dto/                 # 数据传输对象
│   │   ├── model/               # JPA 实体
│   │   ├── repository/          # 数据访问层
│   │   ├── service/             # 业务逻辑层
│   │   ├── util/                # 工具类（JWT）
│   │   └── AichatApplication.java
│   └── resources/
│       ├── static/              # CSS / JavaScript
│       ├── templates/           # Thymeleaf HTML 模板
│       └── application.properties
└── test/                        # 单元测试
```

## 计费模型

系统按模型配置中设定的 `input_token_price` 和 `output_token_price` 计算每次对话费用：

- **总费用 = (输入 Token 数 × 输入单价) + (输出 Token 数 × 输出单价)**
- Token 数通过 API 返回的 `usage` 字段获取
- 余额不足时自动拒绝请求，返回 402 状态码

## 使用示例

### 注册用户

```bash
# 1. 发送验证码
curl -X POST http://localhost:8080/api/auth/send-code \
  -H "Content-Type: application/json" \
  -d '{"email": "user@example.com"}'

# 2. 注册
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "123456",
    "email": "user@example.com",
    "code": "123456"
  }'
```

### 流式对话

```bash
curl -X POST http://localhost:8080/api/chat/1/stream \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer your_jwt_token" \
  -d '{
    "message": "你好",
    "promptId": 1,
    "modelConfigId": 1,
    "webSearchEnabled": false
  }'
```

## 许可证

MIT
