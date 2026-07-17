# HanaChat

基于 Spring Boot + React 的多模型 AI 聊天应用，集成流式对话、RAG 知识库、长期记忆、图像识别、好友系统、Token 计费及管理后台。

## 主要功能

### 智能对话
- 支持 DeepSeek / GPT / Gemini / Grok 等多模型动态切换，API Key 加密存储
- SSE 流式输出，支持中途停止，自动保留上下文
- 自定义 System Prompt，上传图片多模态识别
- 集成 Tavily 联网搜索

### 前端（React + TypeScript）
- 基于 Vite + React + Tailwind CSS 构建的现代化 SPA
- 侧边栏会话管理、模型选择、Web 搜索开关
- 实时流式消息、自动滚动、loading 动画
- 图片上传、提示词管理、好友私聊

### RAG 知识库
- TXT / Markdown 文件上传，自动分块 → 向量化 → 存入 ChromaDB
- 对话中可启用知识库，AI 基于上传文档检索回答

### 长期记忆（仿人类记忆模型）
- AI 自动从对话中提取关键事实，语义检索历史记忆
- 分层衰减：清晰期(全文) → 模糊期(摘要) → 轮廓期(关键词) → 遗忘
- 懒衰减机制，无需定时任务；手动添加的记忆永不过期

### 好友系统 & 提示词社区
- 按 PID/用户名搜索用户，发送/接受好友申请，私聊
- 提示词社区广场，支持精选、点赞、封面图片

### Token 计费 & 管理后台
- 输入/输出 Token 分别计价，余额预扣
- 赞助充值 + 管理员审核到账
- 管理面板：用户管理、模型配置、消费统计、赞助审核

### 安全设计
- JWT 无状态认证 + Spring Security，ROLE_ADMIN 角色隔离
- 所有按 ID 操作校验数据归属，userId 从认证上下文获取
- 禁用用户 Token 即时失效
- 聊天 API 速率限制（每用户每分钟最多 30 次）

## 技术栈

| 分类 | 技术 |
| :--- | :--- |
| 后端框架 | Spring Boot 4.0.6 |
| JDK | Eclipse Temurin 17 |
| 数据库 | MySQL 8.0 |
| ORM | Spring Data JPA |
| 安全 | Spring Security + JWT 0.12.6 |
| 向量数据库 | ChromaDB |
| 嵌入模型 | 硅基流动 bge-large-zh-v1.5 (1024维) |
| 模板引擎 | Thymeleaf |
| 前端框架 | React 19 + TypeScript |
| 构建 | Maven + Vite |
| 容器化 | Docker / Docker Compose |
| 流式响应 | SSE |

## 快速开始

### 1. 创建数据库
```sql
CREATE DATABASE ai_chat_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 2. 配置环境变量
```env
DB_PASSWORD=your_db_password
JWT_SECRET=your_jwt_secret_key_at_least_32_chars
ENCRYPTION_KEY=your-16-char-key
SILICONFLOW_API_KEY=your_siliconflow_key
MEMORY_LLM_API_KEY=sk-xxxxx
QIANFAN_API_KEY=your_qianfan_api_key        # 可选
TAVILY_API_KEY=your_tavily_api_key          # 可选
MAIL_USERNAME=your_email@qq.com             # 可选
MAIL_PASSWORD=your_email_auth_code          # 可选
```

### 3. 启动
```bash
# 启动 ChromaDB
docker compose up -d chromadb

# 初始化数据库表（首次）
mysql -u root -p ai_chat_db < Plans.2.0/LongMemory.sql

# 启动后端
mvn spring-boot:run

# 启动前端开发服务器（另一个终端）
cd frontend
npm install
npm run dev
```

**访问：**
- 后端接口: `http://localhost:8080`
- 前端开发: `http://localhost:5173`
- 管理后台: `http://localhost:8080/admin`
- 提示词社区: `http://localhost:8080/workshop`

### Docker Compose 一键部署
```bash
docker compose up -d
```

## 上下文拼装管线

```
System Prompt → 长期记忆(最近20条) → 对话摘要 → RAG检索结果 → 历史消息(最近30条) → 联网搜索结果 → 图片描述 → 当前消息
```

所有上下文按序注入，形成完整的对话 prompt。

## 项目结构

```
aichat/
├── frontend/               # React 前端
│   ├── src/
│   │   ├── components/     # chat / layout / modals / shared / ui
│   │   ├── lib/            # api / auth / services / toast / utils
│   │   └── App.tsx         # 主应用组件
│   ├── public/             # 静态资源 (favicon 等)
│   └── vite.config.ts
├── src/main/java/com/example/aichat/
│   ├── config/             # Security、JWT、WebConfig、RateLimit 等
│   ├── controller/         # Chat / Auth / Friends / PromptsHub / Admin 等
│   ├── model/              # JPA 实体
│   ├── repository/         # Spring Data JPA
│   └── service/            # ChatService / LLMService / MemoryService 等
├── src/main/resources/
│   ├── static/             # 构建产物 & 静态 JS/CSS
│   └── templates/          # Thymeleaf 模板（登录、管理后台、提示词社区等）
└── docker-compose.yml
```