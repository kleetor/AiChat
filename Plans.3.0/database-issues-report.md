# 数据库问题诊断报告

> 生成时间：2026-07-02  
> 数据库：MySQL 8.0.46 | `jdbc:mysql://localhost:3306/ai_chat_db`  
> Flyway 版本：`flyway-mysql`（Spring Boot 托管）

---

## 一、配置概览

| 配置项 | 值 |
|---|---|
| 连接 URL | `jdbc:mysql://localhost:3306/ai_chat_db?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true` |
| 用户名 | `root` |
| JPA ddl-auto | `validate`（仅校验，不自动建表） |
| Flyway enabled | `true` |
| Flyway baseline-on-migrate | `true` |
| Flyway baseline-version | `1` |
| 迁移目录 | `classpath:db/migration` |
| 仅有的迁移文件 | `V1__baseline.sql` |

---

## 二、问题清单

### 问题 1：【严重】`flyway_schema_history` 表不存在

- **现象**：数据库中完全没有 Flyway 迁移历史表
- **原因**：当前 17 张业务表是由 JPA `ddl-auto=update`（或手动）创建的，Flyway 从未在此数据库上执行过迁移
- **影响**：Flyway 无法判断当前数据库处于哪个版本
- **当前行为**：由于 `baseline-on-migrate=true` 且 `baseline-version=1`，下次启动时 Flyway 会创建 `flyway_schema_history` 表并插入一条 `version=1` 的基线记录，然后查找 `>1` 版本的迁移文件执行

---

### 问题 2：【严重】V1 迁移脚本缺少数据库实际存在的列（3 处）

| 表 | 缺失列 | 实际类型 | JPA 实体中是否存在 |
|---|---|---|---|
| `users` | `failed_attempts` | `int NULL DEFAULT 0` | 否 |
| `users` | `password_encrypted` | `varchar(255) NULL` | 否 |
| `model_configs` | `user_id` | `bigint NOT NULL` | 否 |

- **风险**：这 3 列是"幽灵列"——JPA 实体中不存在，但数据库中有。可能是历史遗留或手动添加。需要用新环境从迁移脚本建库时会缺少这 3 列。

---

### 问题 3：【严重】V1 迁移脚本列类型与数据库实际类型不一致（4 处）

| 表 | 列 | V1 迁移脚本 | 实际 DB | JPA 实体 |
|---|---|---|---|---|
| `memory_items` | `access_count` | `BIGINT` | `int` | `Integer` |
| `conversation_summaries` | `message_count_at_generation` | `BIGINT` | `int` | `Integer` |
| `conversation_summaries` | `version` | `BIGINT` | `int` | `Integer` |
| `prompts_hub` | `likes_count` | `BIGINT` | `int` | `Integer` |

- **影响**：迁移脚本用了 `BIGINT`，但 JPA 实体使用 `Integer`（映射为 `int`）。以迁移脚本建库会产生不必要的 8 字节整型列。

---

### 问题 4：【中等】DATETIME 精度不一致（大量表）

迁移脚本统一使用 `DATETIME`，但数据库中以下表使用 `datetime(6)`（微秒精度）：

`recharge_orders`、`knowledge_bases`、`kb_documents`、`token_usages`、`prompts_hub`、`friendships`、`comments`、`notifications`、`user_likes`、`friend_messages`

这是 JPA `ddl-auto=update` 的默认行为。JPA 对 `LocalDateTime` 字段默认生成 `datetime(6)` 类型。

- **影响**：以迁移脚本建库得到的 DATETIME 无微秒精度，与 JPA 期望的 `datetime(6)` 不一致。虽然功能上可能不会出错（JPA `validate` 模式下 `datetime` 兼容 `LocalDateTime`），但仍是不一致。

---

### 问题 5：【中等】NULL 约束与默认值差异（大量表）

数据库中有大量列缺乏 `NOT NULL` 约束和 `DEFAULT` 值（由 JPA `ddl-auto=update` 生成，JPA 在 Java 层管理默认值），而迁移脚本定义了它们。以下列出差异显著的列：

| 表 | 列 | 迁移脚本 | 实际 DB |
|---|---|---|---|
| `comments` | `created_at` | `NOT NULL DEFAULT CURRENT_TIMESTAMP` | `datetime(6) NULL` |
| `comments` | `likes_count` | `NOT NULL DEFAULT 0` | `int NULL` |
| `notifications` | `is_read` | `NOT NULL DEFAULT 0` | `bit(1) NULL` |
| `notifications` | `created_at` | `NOT NULL DEFAULT CURRENT_TIMESTAMP` | `datetime(6) NULL` |
| `prompts_hub` | `created_at` | `NOT NULL DEFAULT CURRENT_TIMESTAMP` | `datetime(6) NULL` |
| `prompts_hub` | `likes_count` | `NOT NULL DEFAULT 0` | `int NULL` |
| `prompts_hub` | `featured` | `NOT NULL DEFAULT 0` | `bit(1) NULL` |
| `user_likes` | `created_at` | `NOT NULL DEFAULT CURRENT_TIMESTAMP` | `datetime(6) NULL` |
| `friend_messages` | `is_read` | `NOT NULL DEFAULT 0` | `bit(1) NULL` |
| `friend_messages` | `created_at` | `NOT NULL DEFAULT CURRENT_TIMESTAMP` | `datetime(6) NULL` |
| `friendships` | `created_at` | `NOT NULL DEFAULT CURRENT_TIMESTAMP` | `datetime(6) NULL` |
| `users` | `version` | `NOT NULL DEFAULT 0` | `bigint NULL` |

- **影响**：JPA `validate` 模式不会因数据库缺少 NOT NULL/DEFAULT 而报错。但从数据库层面看，约束不完整。

---

### 问题 6：【严重】V1 迁移脚本缺少业务索引（14 个索引）

以下索引存在于实际数据库中，但 V1 迁移脚本完全没有包含：

| 表 | 索引名 | 列 | 类型 |
|---|---|---|---|
| `chat_messages` | `idx_conv_time` | `(conversation_id, timestamp)` | 普通 |
| `comments` | `idx_parent` | `(parent_id)` | 普通 |
| `comments` | `idx_prompt` | `(prompt_id)` | 普通 |
| `friend_messages` | `idx_friendship_time` | `(friendship_id, created_at)` | 普通 |
| `friend_messages` | `idx_receiver_read` | `(receiver_id, is_read)` | 普通 |
| `friendships` | `idx_friend` | `(friend_id)` | 普通 |
| `friendships` | `idx_user_status` | `(user_id, status)` | 普通 |
| `friendships` | `UKjwaac0iw9d1fu58mx7afwf9f4` | `(user_id, friend_id)` | **UNIQUE** |
| `memory_items` | `idx_decay_check` | `(detail_level, last_accessed_at)` | 普通 |
| `memory_items` | `idx_user_dl_time` | `(user_id, detail_level, last_accessed_at)` | 普通 |
| `memory_items` | `idx_user_enabled` | `(user_id, enabled)` | 普通 |
| `notifications` | `idx_user_read_time` | `(target_user_id, is_read, created_at)` | 普通 |
| `token_usages` | `idx_user_time` | `(user_id, created_at)` | 普通 |
| `user_likes` | `uk_user_target` | `(user_id, target_type, target_id)` | **UNIQUE** |

- **影响**：用迁移脚本搭建的新环境将缺少这些性能关键索引，查询性能会严重下降。尤其是缺乏 `friendships` 唯一约束会导致重复好友关系。

---

### 问题 7：【低】BIT / bit(1) / tinyint(1) 类型不一致

迁移脚本用 `BIT`，但实际数据库表现不一致：

| 表 | 列 | 迁移脚本 | 实际 DB |
|---|---|---|---|
| `users` | `enabled` | `BIT NOT NULL DEFAULT 1` | `bit(1) NO` |
| `prompts_hub` | `featured` | `BIT NOT NULL DEFAULT 0` | `bit(1) YES` |
| `memory_items` | `enabled` | `BIT NOT NULL DEFAULT 1` | `tinyint(1) NO` |

---

### 问题 8：【低】`access_count` 列使用 Integer 而非 Java 实体中定义的 Long

- `memory_items.access_count`：JPA 实体 `MemoryItem.java` 中定义为 `Integer`（`int`），但迁移脚本写成 `BIGINT`，实际数据库为 `int`

---

## 三、Flyway 相关配置问题

### 问题 9：【中等】仅有一个迁移文件，且与实际状况不符

项目中只有 `V1__baseline.sql` 一个迁移文件，其注释说明"基于 JPA ddl-auto=update 生成的实际表结构 (2026-07-01)"。但实际上：
- 缺少 3 列
- 4 处列类型错误
- 缺少 14 个索引
- DATETIME 精度不匹配
- 大量 NULL 约束和默认值差异

---

### 问题 10：【低】JPA `ddl-auto=validate` 与 Flyway 的冲突风险

当前配置中 JPA 是 `validate` 模式，仅校验实体对应的列是否存在。数据库中的额外列（`failed_attempts`、`password_encrypted`、`model_configs.user_id`）不会导致启动失败，但这些列不受任何框架管理，属于数据库技术债务。

---

## 四、总结

| 严重程度 | 数量 | 问题 |
|---|---|---|
| 严重 | 4 | Flyway 历史缺失、V1 脚本缺列、类型错误、缺索引 |
| 中等 | 3 | DATETIME 精度不一致、NULL 约束差异、配置冲突风险 |
| 低 | 3 | BIT 类型不一致、access_count 类型、JPA 幽灵列 |

**核心结论**：`V1__baseline.sql` 不是当前数据库的准确快照。建议以实际数据库结构为准，完全重写 `V1__baseline.sql`，将缺失的列、修正的类型、以及所有 14 个业务索引一并纳入。
