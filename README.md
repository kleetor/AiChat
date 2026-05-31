# AI Chat 智能聊天平台

基于 Spring Boot + Java 构建的 AI 聊天应用，支持多模型配置、多会话管理和智能提示词功能。

## 技术栈

- **后端**：Spring Boot 4.0.6 + Spring Security + Spring Data JPA
- **数据库**：MySQL 8.x
- **认证**：JWT
- **前端**：原生 HTML + CSS + JavaScript

## 功能特性

- 用户注册登录（自动生成6位PID）
- 多会话管理
- 模型配置管理（数据库存储，动态选择）
- 上下文对话（最近30条历史）
- 提示词管理（System Prompt）

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.8+
- MySQL 8.0+

### 启动步骤

1. 创建数据库
```sql
CREATE DATABASE ai_chat_db;
```

2. 配置 `application.properties` 中的数据库连接

3. 启动应用
```bash
mvn spring-boot:run
```

4. 访问 `http://localhost:8080`

## API 接口

### 认证
- `POST /api/auth/register` - 注册
- `POST /api/auth/login` - 登录
- `GET /api/auth/me` - 获取用户信息

### 会话
- `GET /api/conversations` - 会话列表
- `POST /api/conversations` - 创建会话
- `DELETE /api/conversations/{id}` - 删除会话

### 聊天
- `POST /api/chat/send` - 发送消息
- `GET /api/chat/{id}/history` - 历史记录

### 提示词
- `GET /api/prompts` - 提示词列表
- `POST /api/prompts` - 创建提示词
- `PUT /api/prompts/{id}` - 更新提示词
- `DELETE /api/prompts/{id}` - 删除提示词

### 模型配置
- `GET /api/model-configs` - 配置列表
- `POST /api/model-configs` - 创建配置
- `PUT /api/model-configs/{id}` - 更新配置
- `DELETE /api/model-configs/{id}` - 删除配置

## 数据库表

- `users` - 用户表
- `conversations` - 会话表
- `chat_messages` - 消息表
- `prompts` - 提示词表
- `model_configs` - 模型配置表

## 未来计划

1. 流式响应和输出
2. 后台系统
3. 更多模型支持
4. 计费系统
5. 对话数据导出
6. 前端页面优化
7. 提示词社区
