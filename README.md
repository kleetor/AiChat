# AI Chat 聊天网站

基于 Spring Boot + Java 后端构建的 AI 聊天平台，支持多会话管理、自定义提示词（System Prompt）、上下文对话、用户注册登录与 JWT 认证。

## 主要功能

- **用户系统**：注册、登录，基于 JWT 的无状态认证
- **多会话管理**：创建、切换、删除会话，每个会话独立保存上下文
- **上下文对话**：自动携带当前会话的历史消息（最近30轮）发送给 AI 模型，实现连续对话
- **自定义提示词**：以卡片形式管理 System Prompt，支持增删改查，聊天时可选择启用
- **对话历史**：所有对话记录持久化到 MySQL 数据库，支持按会话查看历史
- **AI 模型集成**：通过 REST API 调用 DeepSeek 模型（可扩展其他支持 OpenAI 格式的模型）

## 技术栈

| 组件 | 技术 |
|------|------|
| 后端 | Java 17+, Spring Boot 3.x, Spring Security, Spring Data JPA, Spring Web |
| 数据库 | MySQL 8.x |
| AI API | DeepSeek Chat (兼容 OpenAI API 格式) |
| 前端 | 原生 HTML + CSS + JavaScript (单页应用) |
| 构建 | Maven |
| 身份认证 | JWT (jjwt) |

## 快速启动

### 1. 环境准备

- JDK 17+
- Maven 3.8+
- MySQL 8.x（创建数据库 `ai_chat_db`）

### 2. 配置数据库

修改 `src/main/resources/application.properties`：

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/ai_chat_db?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
spring.datasource.username=root
spring.datasource.password=yourpassword
spring.jpa.hibernate.ddl-auto=update

jwt.secret=your256bitsecretkeyhere...  # 至少256位
jwt.expiration=86400000

deepseek.api.url=https://api.deepseek.com/v1/chat/completions
deepseek.api.key=sk-your-deepseek-api-key
```

### 3. 运行

```bash
mvn spring-boot:run
```

启动后访问 `http://localhost:8080` 即可进入聊天界面。

## 项目结构

```
src/main/java/com/example/aichat/
├── config/        # 安全配置、JWT 过滤器、全局跨域
├── controller/    # 登录注册、对话、会话、提示词接口
├── dto/           # 请求响应对象
├── model/         # 实体类 (User, Conversation, ChatMessage, Prompt)
├── repository/    # JPA 数据访问
├── service/       # 业务逻辑
└── util/          # JWT 工具类
```

## 后续计划

### 🚀 下一阶段：用户计费功能与 UI 优化

1. **用户计费系统**
   - 引入按 token 或按次计费的额度管理
   - 用户充值、余额查询、消费记录
   - 限制免费额度，超出后提示充值

2. **UI 交互优化**
   - 流式响应（SSE），实时显示 AI 回复
   - 夜间模式/主题切换
     
3. **更好的提示词卡片功能**
   - 优化提示词模板

4. **更多聊天模型**
   - 引入更多主流模型
   

欢迎 Star 或提交 Issue/PR 参与改进！
