知识库（RAG）技术方案
======================

> 项目: AI Chat (Spring Boot 4.0.6 + Java 17 + MySQL 8.0 + ChromaDB)
> 向量数据库: chromadb-java-client 0.1.7
> 嵌入模型: 硅基流动 bge-large-zh-v1.5 (OpenAI 兼容 API)
> 基于现有架构纯增量开发，不破坏任何现有功能


## 一、为什么选 ChromaDB + chromadb-java-client

| 方案 | 优点 | 缺点 |
|------|------|------|
| **ChromaDB + SDK** | 类型安全、自动向量化、零维护 | 仅支持 ChromaDB |
| pgvector (PostgreSQL) | 与现有 MySQL 同库 | 需要 PostgreSQL、手写向量逻辑 |
| Spring AI Chroma Starter | Spring 风格统一 | 底层仍依赖 ChromaDB，增加一层抽象 |
| 手写 HTTP API | 无额外依赖 | 大量样板代码、弱类型 |

**结论：chromadb-java-client SDK** — 一个 Maven 坐标，collection.add(docs) / collection.query(texts) 自动帮你调用 EmbeddingFunction 完成向量化和检索，代码量最少。


## 二、现有代码分析

### 2.1 核心消息构建流程（ChatService.buildMessagesArray 行 104-178）

```
用户消息 → buildMessagesArray(...)
  ├─ 注入 System Prompt (Prompt 表)              ← 已有，行 112-121
  ├─ 注入最近 30 条历史消息 (ChatMessage 表)       ← 已有，行 124-132
  ├─ 注入联网搜索结果 (Tavily/千帆)               ← 已有，行 135-160
  ├─ 注入图片识别描述                            ← 已有，行 163-168
  └─ 注入当前用户消息                            ← 已有，行 171-174
```

### 2.2 调用链路（ChatController → ChatService）

```
ChatController.chatStream()
  └─ ChatService.chatStream()
       ├─ buildMessagesArray()  → 构建消息数组
       └─ streamDeepSeek()      → 流式调用 LLM，解析 SSE，保存，扣费
```

### 2.3 ChatRequest 现有字段

```java
// src/.../dto/ChatRequest.java
message, promptId, modelConfigId, webSearchEnabled, imageDescription
```

### 2.4 已有基础设施（可复用）

| 组件 | 用途 |
|------|------|
| Mysql + JPA/Hibernate | 业务元数据存储 |
| Docker Compose (mysql + app) | 追加 chromadb 服务 |
| 雨云 S3 (software.amazon.awssdk:s3) | 文档原始文件存储 |
| BillingService | Token 计费集成 |
| Prompt 表 + PromptService | 可注入知识库上下文 |
| ModelConfig 表 | 多模型配置 (apiUrl, apiKey, modelName, 单价) |
| Apache HttpClient5 (CloseableHttpClient) | 流式请求 |
| RestTemplate | 非流式请求 |


## 三、RAG 架构

### 3.1 数据流

```
上传文档 (TXT/MD/PDF)
  → S3 存储原始文件
  → ChunkingService 分块 (按段落, 最大500字, 重叠50字)
  → SiliconFlowEmbeddingFunction 向量化
  → ChromaDB Collection: kb_{知识库ID} 存储向量+原文+元数据

用户提问 "考勤制度是什么?"
  → ChromaDBService.query(kbId, "考勤制度是什么?")
  → SDK 自动向量化查询文本 → 余弦相似度 Top-K 检索
  → 检索结果注入 buildMessagesArray() 的 system prompt
```

### 3.2 上下文注入顺序

```
ChatService.buildMessagesArray() 按顺序注入:
  1. Prompt (个人提示词)                    ← 已有
  2. 知识库检索结果 (ChromaDB QueryResult)   ← 新增 <--- 在这里注入
  3. 联网搜索结果                           ← 已有
  4. 图片识别描述                           ← 已有
  5. 最近 30 条历史消息                     ← 已有
  6. 当前用户消息                           ← 已有
```


## 四、Maven 依赖

```xml
<!-- pom.xml 追加: -->
<dependency>
    <groupId>io.github.amikos-tech</groupId>
    <artifactId>chromadb-java-client</artifactId>
    <version>0.1.7</version>
</dependency>
<dependency>
    <groupId>org.apache.pdfbox</groupId>
    <artifactId>pdfbox</artifactId>
    <version>3.0.3</version>
</dependency>
```


## 五、Docker Compose — 追加 ChromaDB

```yaml
# docker-compose.yml 追加:
  chromadb:
    image: chromadb/chroma:0.6.3
    container_name: aichat-chromadb
    restart: always
    ports:
      - "8000:8000"
    volumes:
      - chroma_data:/chroma/chroma
    environment:
      - IS_PERSISTENT=TRUE
      - ANONYMIZED_TELEMETRY=FALSE
      - PERSIST_DIRECTORY=/chroma/chroma
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8000/api/v2/heartbeat"]
      interval: 10s
      timeout: 5s
      retries: 3

volumes:
  chroma_data:
```


## 六、application.properties 追加

```properties
# ChromaDB
chromadb.url=http://localhost:8000

# 嵌入模型 (硅基流动 bge-large-zh-v1.5)
embedding.api.url=https://api.siliconflow.cn/v1/embeddings
embedding.api.key=${SILICONFLOW_API_KEY}
embedding.model=BAAI/bge-large-zh-v1.5
embedding.batch.size=32

# 分块配置
rag.chunk.size=500
rag.chunk.overlap=50

# 检索配置
rag.retrieve.top-k=5
```


## 七、MySQL 新增表

```sql
-- 知识库元数据
CREATE TABLE knowledge_bases (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    user_id BIGINT NOT NULL,
    visibility VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
    doc_count INT NOT NULL DEFAULT 0,
    chunk_count INT NOT NULL DEFAULT 0,
    total_size BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id),
    INDEX idx_user (user_id)
);

-- 文档元数据（向量在 ChromaDB）
CREATE TABLE kb_documents (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    kb_id BIGINT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_type VARCHAR(10) NOT NULL,
    file_size BIGINT NOT NULL,
    s3_key VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'PROCESSING',
    chunk_count INT NOT NULL DEFAULT 0,
    error_msg TEXT,
    created_at DATETIME NOT NULL,
    FOREIGN KEY (kb_id) REFERENCES knowledge_bases(id) ON DELETE CASCADE,
    INDEX idx_kb (kb_id)
);
```


## 八、核心代码设计

### 8.1 ChromaDBConfig — SDK Client Bean

```java
@Configuration
public class ChromaDBConfig {
    @Value("${chromadb.url}")
    private String chromaUrl;

    @Bean
    public Client chromaClient() {
        return new Client(chromaUrl);
    }
}
```

### 8.2 SiliconFlowEmbeddingFunction — 中文嵌入函数

实现 `tech.amikos.chromadb.embeddings.EmbeddingFunction` 接口，对接硅基流动 bge-large-zh-v1.5。

SDK 的 `collection.add(documents)` 和 `collection.query(texts)` 会自动调用此函数做向量转换，
**不需要手动调 Embedding API**。

关键方法：
- `embedQuery(String)` — `collection.query()` 内部自动调用
- `embedDocuments(List<String>)` — `collection.add()` 内部自动调用

### 8.3 ChromaDBService — Collection 管理

每个知识库一个 Collection（命名: `kb_{知识库ID}`），天然权限隔离。

核心方法：
- `createCollection(kbId)` — 建知识库时调用
- `getCollection(kbId)` — 用 ConcurrentHashMap 缓存，避免重复 create
- `addChunks(kbId, List<ChunkData>)` — 自动向量化并写入
- `query(kbId, queryText, topK)` — 自动向量化查询文本并检索
- `deleteCollection(kbId)` — 删除知识库时调用
- `deleteByDocument(kbId, docId)` — 按元数据条件删除指定文档向量

### 8.4 ChunkingService — 文本分块

递归字符分割：`\n\n → \n → 。 → ； → ，`，最后按 chunk_size=500 / overlap=50 硬切。

### 8.5 PdfParser — PDF 解析

使用 Apache PDFBox，提取纯文本。

### 8.6 KnowledgeBaseService — 知识库核心逻辑

```java
@Service
public class KnowledgeBaseService {
    // create(name, desc, userId) → 建 KnowledgeBase + chromaDBService.createCollection()
    // uploadDocument(kbId, file) → S3 上传 → 异步处理(解析→分块→向量化)
    // deleteKnowledgeBase(kbId) → 删 Collection + MySQL 记录
    // deleteDocument(docId) → 删 ChromaDB 向量 + MySQL 记录
}
```

文档处理流程：
```
uploadDocument()
  → s3Service.upload()           # 存原始文件
  → 入库 KbDocument (status=PROCESSING)
  → CompletableFuture.runAsync()
      ├─ 解析: txt/md → 直接读文字, pdf → PdfParser
      ├─ 分块: ChunkingService.split()
      └─ 向量化存储: ChromaDBService.addChunks()
  → 更新 KbDocument (status=READY, chunkCount)
```

### 8.7 ChatService 改造 — 注入知识库检索

在 `buildMessagesArray()` 中插入知识库检索（不改变原有逻辑）：

```
buildMessagesArray(conversationId, promptId, userMessage, webSearchEnabled, imageDescription)
                                                                    ↓
                                         新增参数: knowledgeBaseId
                                                                    ↓
  // ===== 第1步: 已有 - 注入 Prompt =====

  // ===== 第2步: 新增 - 注入知识库检索 =====
  if (knowledgeBaseId != null) {
      QueryResponse qr = chromaDBService.query(knowledgeBaseId, userMessage, 5);
      if (qr != null && hasResults(qr)) {
          add system("以下是与用户问题相关的知识库内容，请基于这些内容回答：\n" +
                     formatChunks(qr) + "\n回答时请注明引用来源（文件名+段落号）。");
      }
  }

  // ===== 第3~6步: 已有 - 历史消息、搜索结果、图片描述、当前消息 =====
```

### 8.8 ChatRequest 扩展

```java
@Data
public class ChatRequest {
    // 现有字段不变
    private String message;
    private Long promptId;
    private Long modelConfigId;
    private Boolean webSearchEnabled;
    private String imageDescription;

    // 新增字段
    private Long knowledgeBaseId;      // 选中的知识库 ID (null=不使用)
}
```


## 九、API 设计

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/kb/create` | 创建知识库 |
| GET | `/api/kb/list` | 用户知识库列表 |
| PUT | `/api/kb/{id}` | 编辑知识库 |
| DELETE | `/api/kb/{id}` | 删除知识库 (含 ChromaDB Collection) |
| POST | `/api/kb/{kbId}/docs/upload` | 上传文档 (multipart) |
| GET | `/api/kb/{kbId}/docs` | 文档列表 |
| DELETE | `/api/kb/docs/{docId}` | 删除文档 (含 ChromaDB 向量) |
| POST | `/api/kb/docs/{docId}/reindex` | 重新索引 |


## 十、文件清单

```
新增 (12 个文件):
  src/main/java/com/example/aichat/
  ├── config/ChromaDBConfig.java
  ├── model/KnowledgeBase.java
  ├── model/KbDocument.java
  ├── repository/KnowledgeBaseRepository.java
  ├── repository/KbDocumentRepository.java
  ├── service/
  │   ├── ChromaDBService.java
  │   ├── SiliconFlowEmbeddingFunction.java
  │   ├── ChunkingService.java
  │   ├── PdfParser.java
  │   └── KnowledgeBaseService.java
  ├── controller/KnowledgeBaseController.java
  └── dto/KnowledgeBaseRequest.java

修改 (4 个文件):
  pom.xml                                    ← 添加 chromadb-java-client + pdfbox
  docker-compose.yml                         ← 追加 chromadb 服务
  application.properties                     ← 追加 ChromaDB/Embedding/分块配置
  service/ChatService.java                   ← buildMessagesArray() 注入知识库检索
  dto/ChatRequest.java                       ← 新增 knowledgeBaseId
```


## 十一、实施步骤

1. `docker-compose.yml` 追加 chromadb 服务，`docker compose up -d chromadb`
2. `pom.xml` 追加 `chromadb-java-client` 和 `pdfbox` 依赖
3. `application.properties` 追加配置
4. 编写 `ChromaDBConfig`、`SiliconFlowEmbeddingFunction`
5. 编写 `ChromaDBService` 基础封装
6. 执行 SQL 建表 `knowledge_bases`、`kb_documents`
7. 编写 JPA 实体和 Repository
8. 编写 `ChunkingService`、`PdfParser`
9. 编写 `KnowledgeBaseService` (CRUD + 异步文档处理)
10. 编写 `KnowledgeBaseController`
11. **改造 `ChatService.buildMessagesArray()`** — 注入知识库检索结果
12. `ChatRequest` 新增 `knowledgeBaseId` 字段
13. 前端：知识库选择下拉框、知识库管理页面
14. Token 计费集成（检索消耗 Embedding Token 计入 BillingService）
15. 权限校验（知识库只能作者访问 / PUBLIC 可读）
