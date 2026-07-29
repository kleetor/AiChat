# AI Chat 性能与架构优化计划 v6.0

> **创建**: 2026-07-26 | **更新**: 2026-07-26  
> **基于**: 全项目安全代码审查 + 核心服务流文档分析  
> **服务器配置**: **2 核 4GB 内存**（单机 docker-compose）  
> **当前承载**: DAU 200-500 健康运行  
> **目标承载**: DAU 500-800

---

## 0. 项目状态核实 (2026-07-26)

### 数据库

| 项目 | 值 |
|------|-----|
| 连接 | `localhost:3306` / `root` / `.env` DB_PASSWORD → **OK** |
| 数据库 | `ai_chat_db` |
| 表数量 | 28 张 |
| Flyway 迁移 | 12 条全部 success (V1 ~ V12) |
| 数据量 | 轻量开发数据 (2 用户, 3 模型, 116 条 token 记录) |

关键发现：
- `chat_messages` 已有 `idx_conv_time` 索引，无需新增
- `users` 表仅 PRIMARY，无业务索引 → V13 必须加
- `token_usages` 缺 cost_amount 覆盖索引 → V13 补
- `memory_items` 有 7 个索引但缺 (status, last_accessed_at) → V13 补

### .env 安全确认

`.env` 文件包含真实密钥（DeepSeek / SiliconFlow / 千帆 / Tavily / S3 / 邮箱），确认 `.gitignore` 已覆盖该文件。部署前需确保生产环境 `.env` 不为默认值。

### Git

- 分支: `master`，超前远程 3 commits
- 已 stage: `.gitignore`
- 已修改: `core-service-flow.md`
- 未跟踪: `Plans.6.0/optimization-plan.md`

---

## 1. 现状基线

### 1.1 硬件约束

| 资源 | 总量 | 分配 |
|------|------|------|
| CPU | 2 核 | OS(~10%) + MySQL(~30%) + App(~40%) + ChromaDB(~20%) |
| 内存 | 4 GB | OS(~400MB) + MySQL(~1.0GB) + App(~1.2GB) + ChromaDB(~512MB) + 缓冲(~900MB) |

**核心原则**: 
- **减法**：加资源限制防 OOM
- **乘法**：虚拟线程复用 CPU，2 核顶几十核
- **不加容器**：当前 3 容器是上限
- **预留缓冲**：内存分配不留满，为后续硬件升级留接口

### 1.2 当前问题

| 维度 | 现状 | 风险 |
|------|------|------|
| 容器资源限制 | 仅 App 设了 mem_limit=1536m | MySQL/ChromaDB 无限制，可能触发 OOM Killer |
| Web 容器 | 嵌入式 Tomcat, 默认 200 线程 | 每次 SSE 长连接占 1 平台线程 30-120s |
| 缓存 | Caffeine 本地缓存 | 重启即丢 |
| 数据库 | 单 MySQL 8.0 | 慢查询无索引 |
| 知识库处理 | 同步阻塞 | 上传文档卡 30-60s |
| 外部 API | 双引擎搜索每次调用 | 浪费配额 |

### 1.3 ChromaDB 部署策略

**环境差异化**：

| 环境 | 部署方式 | 原因 |
|------|---------|------|
| **开发环境 (dev)** | `ChromaDBLauncher` 自动拉起子进程 | 便携启动，`mvn spring-boot:run` 一键即用 |
| **生产环境 (prod)** | docker-compose 独立 chromadb 容器 | 常驻不关闭，重启 App 不中断向量库；独立 mem_limit 管控 |

**现状确认**：
- docker-compose.yml 已有独立 chromadb 服务（端口 8000, healthcheck, persist 目录）
- App 通过 `CHROMADB_URL=http://chromadb:8000` 环境变量连接
- `ChromaDBLauncher.java` 启动时先做 `isChromaAlive()` 心跳检测，已在线则跳过——prod 下自然透明跳过
- **无需改动代码**，两种模式自动适配

---

## 2. 优化路线图

```
  立即                    本周                    1-2 周                   后续

┌─ 精简 ────────────┐  ┌─ 防 OOM ──────────┐  ┌─ 异步化 ────────────┐  ┌─ 治理 ────────────┐
│ 移除 BM25 双索引   │  │ 容器内存限制        │  │ 知识库异步处理       │  │ N+1 查询治理       │
│ (释出 80-120MB)   │  │ DB 索引优化 (V13)  │  │ 搜索缓存 (Caffeine)  │  │ 降级兜底           │
│                    │  │ 虚拟线程 (核心)    │  │                     │  │ 监控端点           │
│                    │  │ HikariCP 收紧     │  │                     │  │                    │
│                    │  │ Tomcat 线程调优    │  │                     │  │                    │
└──────────────────┘  └───────────────────┘  └─────────────────────┘  └────────────────────┘

  禁止项:
    ❌ Redis — 不做，4 个容器在 2C4G 不可能
    ❌ 多实例 — 不做，需要额外机器
    ❌ 读写分离 — 不做，需要额外机器
    ❌ WebFlux 迁移 — 不做，虚拟线程已解决连接模型问题
    ✅ BM25 — 移除，Rerank 模型在小规模下已足够补偿精度损失
```

---

## 3. 第零批：功能精简（立即，释放内存）

### 3.0 精简 BM25 双索引（释出 80-120MB）

**现状**：项目维护了两套独立的 Lucene BM25 索引：

| 组件 | 类 | 索引路径 | 用途 |
|------|-----|---------|------|
| 记忆 BM25 | `Bm25IndexService` | `./data/bm25-index/` | 记忆系统中的关键词检索 |
| 知识库 BM25 | `KbBm25IndexService` | `./data/bm25-kb/{kbId}/` | RAG 知识库的关键词检索 |

每套包括：`SmartChineseAnalyzer`（中文分词词典 ~50MB）+ `IndexWriter` + `IndexReader` + `MMapDirectory` 堆外内存映射。两套合计占用 **80-120MB**，在 2C4G 下是显著的负担。

**价值评估**：

| 检索路径 | 当前方案 | 精简后 | 精度影响 |
|---------|---------|--------|---------|
| 知识库 | 向量 + BM25 → RRF → Rerank | 向量 → Rerank | 极低（Cross-Encoder 本身已是强语义匹配，对小规模知识库足够） |
| 记忆 | 向量 + BM25 + 图谱实体 → RRF → Rerank | 向量 + 图谱实体 → RRF → Rerank | 低（记忆数据量小，向量召回已覆盖大部分场景） |

**结论**：BM25 在 DAU 500-800 量级属于锦上添花。Rerank 模型的 Cross-Encoder 重排序已经能弥补纯向量检索的不足。移除后不影响核心功能——RAG 回答和记忆召回依然正常工作。

**操作**：

```java
// 1. KbRetrievalService.java — 移除 BM25 路径
//    原: CompletableFuture<List<KbBm25Hit>> bm25Future = 
//          supplyAsync(() -> bm25Service.search(kbId, q, candidateSize))
//    新: 删除该 Future，RRF 融合只处理 vector 单一来源

// 2. HybridRetrievalService.java — 移除 BM25 路径  
//    原: bm25Service.search(userId, query, CANDIDATE_SIZE, promptId)
//    新: 删除该调用，RRF 融合从三路径退为二路径（向量+图谱实体）

// 3. MemoryService.java — 移除 BM25 同步写
//    原: extract → bm25Service.index(...)
//        restore → bm25Service.index(...)
//        compress → bm25Service.index(...)
//        delete → bm25Service.remove(...)
//    新: 删除全部 4 处调用

// 4. KnowledgeBaseService.java — 移除 BM25 同步写
//    原: processDocument → bm25Service.indexChunks(...)
//        deleteDocument → bm25Service.removeByDocument(...)
//        deleteKnowledgeBase → bm25Service.deleteIndex(...)
//    新: 删除全部 3 处调用

// 5. 删除文件:
//    Bm25IndexService.java
//    KbBm25IndexService.java
//    application.properties 中 bm25.index-path 配置
//    pom.xml 中 3 个 lucene 依赖 (lucene-core, lucene-queryparser, lucene-analysis-smartcn)
```

**释放内存**: 80-120MB  
**预估工时**: 0.5 天

### 其他可考虑精简项（暂缓决策）

| 功能 | 内存占用 | 影响 | 建议 |
|------|---------|------|------|
| 知识库查询重写 | 无额外内存，但多轮检索吃 CPU | 生成 2-3 个查询变体，各发向量搜索 | 限制变体数 ≤ 2，或仅对长查询启用 |
| 记忆实体图谱 | ~30-50MB（实体关系内存结构） | 三路径召回中的图谱实体路径 | 保留——图谱在记忆系统中价值高 |

---

## 4. 第一批：防 OOM + 核心提速（本周）

### 4.1 容器内存限制

**收益**: 防止 MySQL/ChromaDB 无限制吃内存导致 OOM Killer；预留 ~600MB 缓冲空间应对突发

**文件**: `docker-compose.yml`

```yaml
services:
  mysql:
    mem_limit: 1024m          # 上限 1GB（当前实际 ~600MB，留 400MB 增长空间）
    mem_reservation: 512m     # 软限制 512MB

  chromadb:
    mem_limit: 384m           # 上限 384MB（预留给未来数据增长）
    mem_reservation: 256m

  app:
    mem_limit: 1536m          # 保持
    mem_reservation: 768m
```

**内存分配总览**：

```
总计 4GB = 4096MB

  OS + Docker daemon      ~400MB   (固定)
  MySQL    mem_limit       1024MB   (上限)
  App      mem_limit       1536MB   (上限)
  ChromaDB mem_limit        384MB   (上限)

  ─────────────────────────────────
  容器上限合计             3344MB
  实际稳态使用             ~2200MB
  剩余缓冲                ~1800MB   ← 峰值空间、硬盘缓存、系统预留
```

**JVM 参数**：限制堆大小，避免 Java 预留超过容器配额：

```yaml
# docker-compose.yml app 服务
environment:
  JAVA_TOOL_OPTIONS: "-XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:MaxRAMPercentage=60"
```

**预估工时**: 0.25 天

---

### 4.2 数据库索引优化

**收益**: 零资源开销，大幅降低慢查询 CPU 占用

**核实结果** (2026-07-26 终端登录确认):

| 表 | 现有索引 | 缺索引 | 动作 |
|---|---|------|------|
| `chat_messages` | `idx_conv_time (conversation_id, timestamp)` ✅ | — | ~~移除原计划~~ 已满足 |
| `users` | 仅 PRIMARY (id) | 模糊搜索全表扫描 | **加** FULLTEXT + username 索引 |
| `token_usages` | `idx_user_time (user_id, created_at)` | cost_amount 聚合 | **加** cost_amount 覆盖 |
| `memory_items` | 7 个索引 (Flyway V9) | (status, last_accessed_at) | **加** 复合索引 |
| `notifications` | `idx_user_read_time (target_user_id, is_read, created_at)` | — | **精简为** (target_user_id, is_read) |

```sql
-- V13__performance_indexes.sql

-- 1. 用户模糊搜索（当前 users 表仅 PRIMARY，LIKE 全表扫描）
ALTER TABLE users ADD FULLTEXT INDEX ft_user (username, email, pid);
ALTER TABLE users ADD INDEX idx_username (username);

-- 2. Token 用量聚合查询（idx_user_time 不覆盖 cost_amount）
ALTER TABLE token_usages ADD INDEX idx_user_date_cost (user_id, created_at, cost_amount);

-- 3. 记忆按状态+时间过滤（存量 7 个索引均不覆盖此路径）
ALTER TABLE memory_items ADD INDEX idx_user_status_accessed (user_id, status, last_accessed_at);

-- 4. 通知未读计数（现有 idx_user_read_time 3 列可用但不精简）
--    (target_user_id, is_read) 足以覆盖 COUNT 查询
--    若 idx_user_read_time 未其他场景使用则保留，不修改
```

**执行**: Flyway 新建 V13 迁移脚本  
**预估工时**: 0.5 天

---

### 4.3 虚拟线程 (2C4G 最高性价比优化)

**收益**: 2 核可支撑数百并发 SSE 连接。每个虚拟线程 ~1KB vs 平台线程 ~1MB，4G 内存下彻底解决线程瓶颈。

**原理**: 

```
传统平台线程:  1 个 SSE 连接 = 1 个 Tomcat 线程 = 1 个 OS 线程 (~1MB)
               200 连接 = 200MB 仅线程栈 + CPU 上下文切换剧烈

虚拟线程:      1 个 SSE 连接 = 1 个虚拟线程 (~1KB) → 阻塞时自动 unpin
               200 连接 = ~200KB + 仅在少数平台线程上调遣
```

**文件**: `application-prod.properties`

```properties
spring.threads.virtual.enabled=true
```

**前提验证**（部署前确认）:
- Caffeine Cache 的 `get()` 操作无 `synchronized` 块 → 不会 pin 虚拟线程
- mysql-connector-j 在虚拟线程下兼容（8.x 版本已支持）

**预估工时**: 1 天（含压测验证）

---

### 4.4 HikariCP 连接池收紧

**收益**: 避免过多 DB 连接浪费 MySQL 内存（每个连接 ~2MB）

```properties
# application-prod.properties
spring.datasource.hikari.maximum-pool-size=8        # 2C 环境下 8 足够
spring.datasource.hikari.minimum-idle=2
spring.datasource.hikari.idle-timeout=300000
spring.datasource.hikari.max-lifetime=1200000
spring.datasource.hikari.connection-timeout=5000    # fail-fast
```

**预估工时**: 0.25 天

---

### 4.5 Tomcat 线程池调优

**收益**: 虚拟线程开启后，平台线程只做调度，需要少量即可

```properties
# application-prod.properties
server.tomcat.threads.max=200       # 虚拟线程模式下够用
server.tomcat.accept-count=50
server.tomcat.connection-timeout=120000
```

**预估工时**: 0.25 天

---

## 5. 第二批：异步化 + 成本控制（1-2 周）

### 5.1 知识库异步处理

**收益**: 上传文档不阻塞 Tomcat 线程，秒级返回

**方案**: Spring `@Async` + 独立线程池，无需消息队列

```java
// KnowledgeBaseService.java
@Async("kbProcessExecutor")
public CompletableFuture<Void> processDocumentAsync(KbDocument doc, String content) {
    List<String> chunks = chunkingService.chunk(content, kb.getChunkSize(), kb.getChunkOverlap());
    List<List<Double>> embeddings = embeddingService.embedBatch(chunks);
    chromaDBService.addEmbeddings(collectionName, chunkIds, embeddings, metadatas);
    bm25Service.index(docId, chunks);
    doc.setStatus("COMPLETED");
    docRepo.save(doc);
}
```

```java
// 独立线程池配置
@Bean("kbProcessExecutor")
public Executor kbProcessExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(1);   // 2C 环境改为 1
    executor.setMaxPoolSize(2);
    executor.setQueueCapacity(20);
    executor.setRejectedExecutionHandler(new CallerRunsPolicy());
    return executor;
}
```

**改动**: `KnowledgeBaseService.java` + `KbDocument.status` 字段 + 前端轮询
**预估工时**: 2 天

---

### 5.2 搜索缓存 (Caffeine 本地)

**收益**: 相同查询 5 分钟内不重复调 API，降低月费

```java
// 新增 SearchCacheManager.java
private final Cache<String, String> searchCache = Caffeine.newBuilder()
    .expireAfterWrite(5, TimeUnit.MINUTES)
    .maximumSize(200)              // 2C4G 下 200 条足够
    .build();
```

```java
// SearchWebTool.execute() 开头
String cacheKey = "search:" + DigestUtils.sha256Hex(query);
String cached = searchCache.getIfPresent(cacheKey);
if (cached != null) {
    return new ToolResult(callId, name, cached);
}
// ... 原有竞速逻辑 ...
// 结果写入缓存
searchCache.put(cacheKey, result);
```

**预估工时**: 0.5 天

---

## 6. 第三批：治理 + 稳定性（后续）

### 6.1 JPA N+1 查询治理

**收益**: 数据库 QPS 降低 50%+

| 位置 | 问题 | 方案 |
|------|------|------|
| `MessageContextBuilder` 加载历史 | 逐条查关联 | `@EntityGraph` / JOIN FETCH |
| `AdminService.getConversations` | Conversation → User 懒加载 | `@Query` JOIN FETCH |
| `PromptsHubService.search` | PromptsHub → User 懒加载 | `@EntityGraph` |
| `FriendService.getFriendList` | Friendship → User 双向懒加载 | `@Query` LEFT JOIN FETCH |

**验证**: 

```properties
# application-dev.properties
logging.level.org.hibernate.SQL=DEBUG
logging.level.org.hibernate.orm.jdbc.bind=TRACE
```

**预估工时**: 2 天

---

### 6.2 异常降级兜底

| 场景 | 当前 | 目标 |
|------|------|------|
| LLM API 超时 | 抛异常 | 返回 "AI 响应超时，请重试" |
| 搜索全部失败 | 返回 "搜索暂不可用" | 优先取缓存；无缓存则降级 |
| ChromaDB 不可用 | 抛异常 | 降级为纯 LLM 回答 |
| 内存压力 (85%+) | 无检测 | Actuator 端点告警 |
| SSE 连接中断 | 前端静默 | 前端自动重连 |

**预估工时**: 1.5 天

---

### 6.3 监控端点

```properties
# application-prod.properties
management.endpoints.web.exposure.include=health,metrics
management.endpoint.health.show-details=always
management.endpoint.health.probes.enabled=true
```

**确保 `/actuator/health` 可被外部监控工具轮询，及时发现 OOM。**

**预估工时**: 0.5 天

---

## 7. 实施总览

| 批次 | 编号 | 任务 | 工时 | 为什么 |
|------|------|------|------|--------|
| **第零批** | 3.0 | 移除 BM25 双索引 | 0.5d | 释出 80-120MB |
| **第一批** | 4.1 | 容器内存限制 | 0.25d | 防 OOM |
| **第一批** | 4.2 | DB 索引 (V13) | 0.5d | users+token_usages+memory_items |
| **第一批** | 4.3 | **虚拟线程** | 1d | 2C 最佳实践 |
| **第一批** | 4.4 | HikariCP 收紧 | 0.25d | 省 MySQL 连接 |
| **第一批** | 4.5 | Tomcat 调优 | 0.25d | 配合虚拟线程 |
| **小计** | | | **2.75 天** | |
| **第二批** | 5.1 | 知识库异步 | 2d | 上传不卡 |
| **第二批** | 5.2 | 搜索缓存 | 0.5d | 省 API 费 |
| **小计** | | | **2.5 天** | |
| **第三批** | 6.1 | N+1 治理 | 2d | 降 DB 压力 |
| **第三批** | 6.2 | 降级兜底 | 1.5d | 可用性 |
| **第三批** | 6.3 | 监控端点 | 0.5d | 发现问题 |
| **小计** | | | **4 天** | |
| **总计** | | | **9.25 天** | |

---

## 8. 预期效果

| 指标 | 优化前 | 第零批后 | 第一批后 |
|------|--------|---------|---------|
| JVM 堆外内存 | BM25 占 80-120MB | **释出** | 释出 |
| OOM 风险 | 高 (MySQL/ChromaDB 无限制) | 高 | **消除** |
| 并发 SSE 连接 | ~10 (平台线程瓶颈) | ~10 | **~60** (虚拟线程) |
| 健康 DAU | 200-500 | 200-500 | **500-800** |
| 文档上传体验 | 同步等待 30-60s | 同步等待 | **异步秒返** (第二批) |
| 外部 API 成本 | 每次调双引擎 | 不变 | **缓存命中时不调** (第二批) |
| MySQL QPS 瓶颈 | 全表扫描 | 全表扫描 | **索引覆盖** |

---

## 9. 禁止项清单

以下原通用优化计划中的项目在 2C4G 场景下明确不做：

| 原编号 | 任务 | 原因 |
|--------|------|------|
| P1-1 | Redis 引入 | 4 个容器在 2C4G 不可能，Redis 本身占 300-500MB |
| P3-1 | 多实例 + Nginx | 2C 跑两个 App 实例不如一个虚拟线程 |
| P3-2 | MySQL 读写分离 | 需要额外机器 |
| P3-4 | WebFlux 迁移 | 虚拟线程已解决连接模型问题，迁移成本远大于收益 |
| P2-3 | 扩大连接池 | 2C 环境应收紧（8 个），不应扩大（原建议 20） |

---

## 10. 扩展预留与升级路径

### 10.1 当硬件升级时的过渡方案

当前配置已预留接口，升级硬件后仅需改数字：

| 升级目标 | 改动位置 | 改动内容 |
|---------|---------|---------|
| **4C8G** | `docker-compose.yml` | App mem_limit 1536m→2560m, MySQL 1024m→2048m, ChromaDB 384m→768m；可选 `deploy.replicas: 2` + 追加 Nginx |
| **8C16G** | `docker-compose.yml` | App mem_limit→4096m, MySQL→4096m, ChromaDB→1536m；追加 Redis 容器 |
| **上 K8s** | 新建 `k8s/` 目录 | docker-compose → Deployment + StatefulSet；HPA min=2 max=8 |

### 10.2 当前预留的扩展点

| 扩展能力 | 当前状态 | 升级后启用 |
|---------|---------|-----------|
| App 水平扩展 | 代码无状态，虚拟线程开启后单实例够 | 硬件 ≥4C8G → Nginx + App × N |
| Redis 插入点 | `CacheConfig.java` + Spring Cache 抽象层已就绪 | ≥4C8G 时引入 |
| 数据库读写分离 | HikariCP 已配置连接池 | 有从库后追加 `spring.datasource.readonly` 数据源 |
| 对象存储切换 | S3 兼容接口 | 换 MinIO / 阿里云 OSS 仅改配置 |
| LLM 多厂商 | OpenAI 兼容 API + Spring AI | 新增 ModelConfig 记录即可切换 |
| 监控告警 | Actuator `/actuator/health` 就绪 | 接入 Prometheus + Grafana |

### 10.3 硬件升级路径

```
当前 2C4G:
  3 容器 (App + MySQL + ChromaDB)
  虚拟线程
  无 Redis
  DAU 500-800 ✓

    ↓ 硬件升级

4C8G:
  4 容器 (+ Redis)
  2 个 App 实例 + Nginx LB
  Redis 分布式缓存 + 限流
  DAU 1,500-3,000 ✓

    ↓ 硬件升级

8C16G:
  5 容器 (+ Redis + 从库 MySQL)
  4 个 App 实例 + Nginx
  MySQL 读写分离
  DAU 5,000-10,000 ✓
```

---

*本文档与 [code-security-review-report-20260724.md](file:///c:/Users/makot/Desktop/aichat/Plans.6.0/code-security-review-report-20260724.md) 和 [core-service-flow.md](file:///c:/Users/makot/Desktop/aichat/core-service-flow.md) 构成项目 v6.0 的完整技术基线。*
