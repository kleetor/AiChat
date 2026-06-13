# AI Chat 智能聊天平台

基于 Spring Boot + Java 构建的 AI 聊天应用，支持多模型配置、多会话管理、智能提示词和联网搜索功能。

## 功能特性

- **用户认证**：注册登录，自动生成6位PID标识
- **多会话管理**：支持创建、查看、删除多个对话会话
- **模型配置**：动态配置多个AI模型，支持API Key管理
- **上下文对话**：自动保留最近30条历史消息作为上下文
- **提示词管理**：自定义System Prompt，塑造AI角色
- **流式响应**：支持SSE实时流式输出，提升用户体验
- **联网搜索**：集成百度千帆搜索API，获取最新信息

## 技术栈

| 分类 | 技术 | 版本 |
| :--- | :--- | :--- |
| 后端框架 | Spring Boot | 4.0.6 |
| 数据库 | MySQL | 8.0+ |
| 认证 | JWT | 0.12.6 |
| 安全 | Spring Security | - |
| ORM | Spring Data JPA | - |
| 前端 | HTML/CSS/JavaScript | - |
| 流式响应 | SSE (Server-Sent Events) | - |

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.8+
- MySQL 8.0+

### 启动步骤

1. **创建数据库**

```sql
CREATE DATABASE ai_chat_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

2. **配置数据库连接**

编辑 `src/main/resources/application.properties`：

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/ai_chat_db?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
spring.datasource.username=root
spring.datasource.password=your_password
```

3. **启动应用**

```bash
mvn spring-boot:run
```

4. **访问应用**

打开浏览器访问 `http://localhost:8080`

## API 接口

### 认证接口

| 方法 | 路径 | 描述 |
| :--- | :--- | :--- |
| POST | `/api/auth/register` | 用户注册 |
| POST | `/api/auth/login` | 用户登录 |
| GET | `/api/auth/me` | 获取当前用户信息 |
| POST | `/api/auth/change-password` | 修改密码 |
| POST | `/api/auth/verify-password` | 验证密码 |

### 会话接口

| 方法 | 路径 | 描述 |
| :--- | :--- | :--- |
| GET | `/api/conversations` | 获取会话列表 |
| POST | `/api/conversations` | 创建新会话 |
| DELETE | `/api/conversations/{id}` | 删除会话 |

### 聊天接口

| 方法 | 路径 | 描述 |
| :--- | :--- | :--- |
| POST | `/api/chat/{conversationId}` | 发送消息（同步） |
| POST | `/api/chat/{conversationId}/stream` | 发送消息（流式） |
| GET | `/api/chat/{conversationId}/history` | 获取聊天历史 |

### 提示词接口

| 方法 | 路径 | 描述 |
| :--- | :--- | :--- |
| GET | `/api/prompts` | 获取提示词列表 |
| POST | `/api/prompts` | 创建提示词 |
| PUT | `/api/prompts/{id}` | 更新提示词 |
| DELETE | `/api/prompts/{id}` | 删除提示词 |

### 模型配置接口

| 方法 | 路径 | 描述 |
| :--- | :--- | :--- |
| GET | `/api/model-configs` | 获取所有模型配置 |
| GET | `/api/model-configs/{id}` | 获取单个模型配置 |

### 搜索接口

| 方法 | 路径 | 描述 |
| :--- | :--- | :--- |
| POST/GET | `/api/search/web` | 联网搜索 |

## 数据库表结构

### users（用户表）
- id - 用户ID（主键）
- username - 用户名
- password - 密码（加密存储）
- pid - 6位用户标识
- created_at - 创建时间
- updated_at - 更新时间

### conversations（会话表）
- id - 会话ID（主键）
- user_id - 用户ID（外键）
- title - 会话标题
- created_at - 创建时间
- updated_at - 更新时间

### chat_messages（消息表）
- id - 消息ID（主键）
- conversation_id - 会话ID（外键）
- user_message - 用户消息
- ai_reply - AI回复
- timestamp - 时间戳

### prompts（提示词表）
- id - 提示词ID（主键）
- user_id - 用户ID（外键）
- name - 提示词名称
- content - 提示词内容
- created_at - 创建时间

### model_configs（模型配置表）
- id - 配置ID（主键）
- api_url - API地址
- api_key - API密钥
- model_name - 模型名称
- created_at - 创建时间

## 项目结构

```plaintext
src/
├── main/
│   ├── java/com/example/aichat/
│   │   ├── config/          # 配置类
│   │   ├── controller/      # REST控制器
│   │   ├── dto/             # 数据传输对象
│   │   ├── model/           # 实体模型
│   │   ├── repository/      # 数据访问层
│   │   ├── service/         # 业务逻辑层
│   │   ├── util/            # 工具类
│   │   └── AichatApplication.java
│   └── resources/
│       ├── static/          # 静态资源
│       ├── templates/       # HTML模板
│       └── application.properties
└── test/                    # 测试类
```

## 配置说明

### JWT配置

```properties
jwt.secret=aichatSecretKeyForJWTTokenGenerationThatIsAtLeast32CharactersLong2024
jwt.expiration=86400000  # 24小时
```

### 百度千帆搜索API配置

```properties
qianfan.api.key=your_api_key
qianfan.api.url=https://qianfan.baidubce.com/v2/ai_search/web_search
```

## 使用示例

### 注册用户

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username": "testuser", "password": "123456"}'
```

### 发送消息（流式）

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

## 开发指南

### 添加新模型配置

在数据库 `model_configs` 表中插入新记录：

```sql
INSERT INTO model_configs (api_url, api_key, model_name) 
VALUES ('https://api.example.com/v1/chat/completions', 'your_api_key', 'model-name');
```

### 启用联网搜索

在发送消息时设置 `webSearchEnabled: true`，系统会自动调用百度千帆搜索API获取相关信息。

## 许可证

MIT License

## 贡献

欢迎提交 Issue 和 Pull Request！