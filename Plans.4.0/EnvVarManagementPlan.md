# 环境变量高效管理优化计划

## 背景

项目当前有 33 个环境变量，46 处 `@Value` 注入散落在 18 个 Java 文件中，且只使用单一的 `application.properties` 无 dev/prod 分离。

## 现状分析

### 当前文件结构

| 文件 | 作用 | 问题 |
|------|------|------|
| `.env` | 实际密钥和配置 | 明文存储所有密钥在本地磁盘 |
| `.env.example` | 变量模板 | **被误纳入 `.gitignore`**，协作者看不到 |
| `application.properties` | Spring Boot 配置 | 单一文件，❌ 无 dev/prod 分离 |
| `docker-compose.yml` | Docker 部署 | 手动列出 20+ 环境变量 |
| 18 个 Java 文件 | `@Value` 注入 | 46 处散落，拼写错误运行时才发现 |

### 环境变量全量清单

```
类别          变量名                      用途
────────────────────────────────────────────────────────────
数据库        DB_PASSWORD                  MySQL 密码
JWT           JWT_SECRET                  JWT 签名密钥
搜索          QIANFAN_API_KEY             百度千帆搜索
              TAVILY_API_KEY              Tavily 搜索
邮件          MAIL_USERNAME               SMTP 用户名
              MAIL_PASSWORD               SMTP 密码
管理员        ADMIN_USERNAME              默认管理员用户名
              ADMIN_PASSWORD              默认管理员密码
              ADMIN_EMAIL                 默认管理员邮箱
加密          ENCRYPTION_KEY              API Key AES 加密密钥
图片识别      IMAGE_API_KEY               图片识别模型 API Key
S3 存储       S3_ACCESS_KEY               雨云 S3 Access Key
              S3_SECRET_KEY               雨云 S3 Secret Key
              S3_URL_PREFIX               S3 公开访问前缀
嵌入模型      SILICONFLOW_API_KEY          硅基流动 API Key
记忆          MEMORY_LLM_API_KEY           记忆提取 LLM API Key
              MEMORY_LLM_API_URL          记忆提取 LLM URL
              MEMORY_LLM_MODEL_NAME        记忆提取 LLM 模型名
CORS          ALLOWED_ORIGINS             CORS 允许来源
百度云        BAIDU_CLOUD_AK              百度云 Access Key
              BAIDU_CLOUD_SK              百度云 Secret Key
```

---

## 优化方案

### 核心思路：**Profile 分离 + 配置类绑定 + 类型安全 + 启动校验**

```
改造前                              改造后
─────────────────────────────      ─────────────────────────────
application.properties             application.properties      ← 公共配置
  (混合所有配置)                     application-dev.properties  ← 本地开发
                                   application-prod.properties ← Docker 部署

@Value 散落 18 个文件               config/props/ 下 13 个配置类
                                   ├── JwtProperties.java
                                   ├── AdminProperties.java
                                   ├── MailProperties.java
                                   ├── ChromaDbProperties.java
                                   ├── EmbeddingProperties.java
                                   ├── MemoryProperties.java
                                   ├── RagProperties.java
                                   ├── SummaryProperties.java
                                   ├── QianfanProperties.java
                                   ├── TavilyProperties.java
                                   ├── S3Properties.java
                                   ├── ImageProperties.java
                                   └── EncryptionProperties.java
                                   每个类被需要的地方注入一个对象即可
```

---

## 实施步骤

### 步骤 1：修正 `.gitignore`

**文件**：`.gitignore`

**操作**：移除 `.env.example` 这行，使其可以提交到仓库。

```
修改前：
### Environment ###
.env
.env.example

修改后：
### Environment ###
.env
```

> `.env` 继续忽略（含真实密钥），`.env.example` 作为文档提交供协作者参考。

---

### 步骤 2：Profile 分离

#### 2.1 `application.properties` — 精简为公共配置

```properties
spring.application.name=hanachat

# 模板
spring.thymeleaf.cache=false

# JPA
spring.jpa.hibernate.ddl-auto=none
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.format_sql=true
spring.jpa.defer-datasource-initialization=true

# Flyway
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration
spring.flyway.baseline-on-migrate=true
spring.flyway.baseline-version=1

# 缓存
spring.cache.type=caffeine

# 文件上传
spring.servlet.multipart.max-file-size=5MB
spring.servlet.multipart.max-request-size=10MB

# 排除 OpenAI 自动配置
spring.autoconfigure.exclude=\
  org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration,\
  org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration,\
  org.springframework.ai.model.openai.autoconfigure.OpenAiImageAutoConfiguration,\
  org.springframework.ai.model.openai.autoconfigure.OpenAiAudioSpeechAutoConfiguration,\
  org.springframework.ai.model.openai.autoconfigure.OpenAiAudioTranscriptionAutoConfiguration,\
  org.springframework.ai.model.openai.autoconfigure.OpenAiModerationAutoConfiguration

# 日志
logging.level.org.hibernate.SQL=WARN
logging.level.org.hibernate.type.descriptor.sql.BasicBinder=WARN
logging.level.org.flywaydb=INFO
logging.level.org.flywaydb.core.internal.command.DbMigrate=DEBUG

# 默认激活 dev profile
spring.profiles.active=dev

# 公共业务配置（不随环境变化）
jwt.expiration=86400000
qianfan.api.url=https://qianfan.baidubce.com/v2/ai_search/web_search
tavily.api.url=https://api.tavily.com/search
embedding.model=BAAI/bge-large-zh-v1.5
embedding.batch.size=32
rag.chunk.size=500
rag.chunk.overlap=50
rag.retrieve.top-k=5
memory.decay.fresh-days=3
memory.decay.brief-days=7
memory.decay.forget-days=14
memory.inject.recent-count=20
memory.search.top-k=10
summary.trigger.count=20
summary.refresh.interval=10
summary.keep.recent=10
```

#### 2.2 `application-dev.properties` — 本地开发

```properties
# ========== 数据库 ==========
spring.datasource.url=jdbc:mysql://localhost:3306/ai_chat_db?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
spring.datasource.username=root
spring.datasource.password=${DB_PASSWORD}

# ========== 密钥（从 .env 读取） ==========
jwt.secret=${JWT_SECRET}
encryption.key=${ENCRYPTION_KEY}

# ========== 外部 API ==========
qianfan.api.key=${QIANFAN_API_KEY}
tavily.api.key=${TAVILY_API_KEY}
image.api.key=${IMAGE_API_KEY}
image.api.url=https://jeniya.cn/v1/chat/completions
image.model=gemini-3.1-flash-lite
embedding.api.key=${SILICONFLOW_API_KEY}
embedding.api.url=https://api.siliconflow.cn/v1/embeddings
memory.llm.api-key=${MEMORY_LLM_API_KEY}
memory.llm.api-url=${MEMORY_LLM_API_URL:https://api.deepseek.com/v1/chat/completions}
memory.llm.model-name=${MEMORY_LLM_MODEL_NAME:deepseek-chat}

# ========== 邮件 ==========
spring.mail.host=smtp.qq.com
spring.mail.port=587
spring.mail.username=${MAIL_USERNAME}
spring.mail.password=${MAIL_PASSWORD}
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
spring.mail.properties.mail.smtp.starttls.required=true
spring.mail.properties.mail.smtp.connectiontimeout=5000
spring.mail.properties.mail.smtp.timeout=5000
spring.mail.properties.mail.smtp.writetimeout=5000
spring.mail.properties.mail.from=${MAIL_USERNAME}

# ========== ChromaDB ==========
chromadb.url=http://localhost:8000

# ========== S3 ==========
s3.endpoint=https://cn-sy1.rains3.com
s3.access-key=${S3_ACCESS_KEY}
s3.secret-key=${S3_SECRET_KEY}
s3.bucket-name=chatimage
s3.url-prefix=${S3_URL_PREFIX}
s3.region=cn-sy1

# ========== 管理员 ==========
admin.default.username=${ADMIN_USERNAME}
admin.default.password=${ADMIN_PASSWORD}
admin.default.email=${ADMIN_EMAIL}

# ========== 文件上传 ==========
upload.dir=./uploads/images
upload.url-prefix=/uploads/images

# ========== CORS ==========
cors.allowed-origins=${ALLOWED_ORIGINS:http://localhost:5173,http://localhost:8080}
```

#### 2.3 `application-prod.properties` — Docker 生产

```properties
# ========== 数据库（Docker 内部地址） ==========
spring.datasource.url=jdbc:mysql://mysql:3306/ai_chat_db?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
spring.datasource.username=root
spring.datasource.password=${DB_PASSWORD}

# ========== 密钥 ==========
jwt.secret=${JWT_SECRET}
encryption.key=${ENCRYPTION_KEY}

# ========== 外部 API ==========
qianfan.api.key=${QIANFAN_API_KEY}
tavily.api.key=${TAVILY_API_KEY}
image.api.key=${IMAGE_API_KEY}
image.api.url=https://jeniya.cn/v1/chat/completions
image.model=gemini-3.1-flash-lite
embedding.api.key=${SILICONFLOW_API_KEY}
embedding.api.url=https://api.siliconflow.cn/v1/embeddings
memory.llm.api-key=${MEMORY_LLM_API_KEY}
memory.llm.api-url=${MEMORY_LLM_API_URL:https://api.deepseek.com/v1/chat/completions}
memory.llm.model-name=${MEMORY_LLM_MODEL_NAME:deepseek-chat}

# ========== 邮件 ==========
spring.mail.host=smtp.qq.com
spring.mail.port=587
spring.mail.username=${MAIL_USERNAME}
spring.mail.password=${MAIL_PASSWORD}
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
spring.mail.properties.mail.smtp.starttls.required=true
spring.mail.properties.mail.smtp.connectiontimeout=5000
spring.mail.properties.mail.smtp.timeout=5000
spring.mail.properties.mail.smtp.writetimeout=5000
spring.mail.properties.mail.from=${MAIL_USERNAME}

# ========== ChromaDB（Docker 内部地址） ==========
chromadb.url=http://chromadb:8000

# ========== S3 ==========
s3.endpoint=https://cn-sy1.rains3.com
s3.access-key=${S3_ACCESS_KEY}
s3.secret-key=${S3_SECRET_KEY}
s3.bucket-name=chatimage
s3.url-prefix=${S3_URL_PREFIX}
s3.region=cn-sy1

# ========== 管理员 ==========
admin.default.username=${ADMIN_USERNAME}
admin.default.password=${ADMIN_PASSWORD}
admin.default.email=${ADMIN_EMAIL}

# ========== 文件上传 ==========
upload.dir=./uploads/images
upload.url-prefix=/uploads/images

# ========== CORS ==========
cors.allowed-origins=${ALLOWED_ORIGINS:http://localhost:5173,http://localhost:8080}
```

> ⚠️ **Docker 部署注意**：必须在 `docker-compose.yml` 的 `app` 服务环境变量中增加 `SPRING_PROFILES_ACTIVE=prod`，否则容器内默认激活 `dev` profile，会使用 `localhost` 地址导致找不到 ChromaDB/MySQL。

---

### 步骤 3：配置属性类（替代 46 处 `@Value`）

创建 `src/main/java/com/example/aichat/config/props/` 目录，建立以下类：

#### 3.1 `JwtProperties.java`

```java
package com.example.aichat.config.props;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@Component
@Validated
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {
    @NotBlank
    private String secret;
    @Positive
    private long expiration;
}
```

#### 3.2 `AdminProperties.java`

```java
package com.example.aichat.config.props;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@Component
@Validated
@ConfigurationProperties(prefix = "admin.default")
public class AdminProperties {
    @NotBlank
    private String username;
    @NotBlank
    private String password;
    @NotBlank
    private String email;
}
```

#### 3.3 `MailProperties.java`

```java
package com.example.aichat.config.props;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@Component
@Validated
@ConfigurationProperties(prefix = "spring.mail")
public class MailProperties {
    @NotBlank
    private String username;
    @NotBlank
    private String password;
    private String host;
    private int port;
}
```

#### 3.4 `ChromaDbProperties.java`

```java
package com.example.aichat.config.props;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@Component
@Validated
@ConfigurationProperties(prefix = "chromadb")
public class ChromaDbProperties {
    @NotBlank
    private String url;
}
```

#### 3.5 `EmbeddingProperties.java`

```java
package com.example.aichat.config.props;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@Component
@Validated
@ConfigurationProperties(prefix = "embedding")
public class EmbeddingProperties {
    @NotBlank
    private String apiUrl;
    @NotBlank
    private String apiKey;
    private String model;
    @Positive
    private int batchSize = 32;
}
```

#### 3.6 `MemoryProperties.java`

```java
package com.example.aichat.config.props;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@Component
@Validated
@ConfigurationProperties(prefix = "memory")
public class MemoryProperties {
    private Inject inject = new Inject();
    private int searchTopK = 10;
    private LlmConfig llm = new LlmConfig();
    private DecayConfig decay = new DecayConfig();

    @Data
    public static class Inject {
        private int recentCount = 20;
    }

    @Data
    public static class LlmConfig {
        @NotBlank
        private String apiKey;
        private String apiUrl;
        private String modelName;
    }

    @Data
    public static class DecayConfig {
        private int freshDays = 3;
        private int briefDays = 7;
        private int forgetDays = 14;
    }
}
```

> ⚠️ **注意**：项目已有 `config/MemoryLLMConfig.java`（同样用 `@ConfigurationProperties(prefix = "memory.llm")`），必须删除该文件，将 `memory.llm.*` 全部归入 `MemoryProperties.LlmConfig` 统一管理，否则 Spring Boot 启动会报 bean 冲突。

#### 3.7 `RagProperties.java`

```java
package com.example.aichat.config.props;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "rag")
public class RagProperties {
    private Chunk chunk = new Chunk();
    private Retrieve retrieve = new Retrieve();

    @Data
    public static class Chunk {
        private int size = 500;
        private int overlap = 50;
    }

    @Data
    public static class Retrieve {
        private int topK = 5;
    }
}
```

#### 3.8 `SummaryProperties.java`

```java
package com.example.aichat.config.props;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "summary")
public class SummaryProperties {
    private Trigger trigger = new Trigger();
    private Refresh refresh = new Refresh();
    private Keep keep = new Keep();

    @Data
    public static class Trigger {
        private int count = 20;
    }

    @Data
    public static class Refresh {
        private int interval = 10;
    }

    @Data
    public static class Keep {
        private int recent = 10;
    }
}
```

#### 3.9 `QianfanProperties.java`

```java
package com.example.aichat.config.props;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@Component
@Validated
@ConfigurationProperties(prefix = "qianfan.api")
public class QianfanProperties {
    @NotBlank
    private String key;
    @NotBlank
    private String url;
}
```

#### 3.10 `TavilyProperties.java`

```java
package com.example.aichat.config.props;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@Component
@Validated
@ConfigurationProperties(prefix = "tavily.api")
public class TavilyProperties {
    @NotBlank
    private String key;
    @NotBlank
    private String url;
}
```

#### 3.11 `S3Properties.java`

```java
package com.example.aichat.config.props;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@Component
@Validated
@ConfigurationProperties(prefix = "s3")
public class S3Properties {
    @NotBlank
    private String endpoint;
    @NotBlank
    private String accessKey;
    @NotBlank
    private String secretKey;
    @NotBlank
    private String bucketName;
    @NotBlank
    private String urlPrefix;
    private String region;
}
```

#### 3.12 `ImageProperties.java`

```java
package com.example.aichat.config.props;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@Component
@Validated
@ConfigurationProperties(prefix = "image")
public class ImageProperties {
    @NotBlank
    private String apiKey;
    @NotBlank
    private String apiUrl;
    private String model;
}
```

#### 3.13 `EncryptionProperties.java`

```java
package com.example.aichat.config.props;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@Component
@Validated
@ConfigurationProperties(prefix = "encryption")
public class EncryptionProperties {
    @NotBlank
    private String key;
}
```

### 使用方修改对照

| 文件 | 改造前 | 改造后 |
|------|--------|--------|
| `JwtUtil.java` | `@Value("${jwt.secret}")` + `@Value("${jwt.expiration}")` | `private final JwtProperties jwtProperties` |
| `TokenBlacklist.java` | `@Value("${jwt.expiration}")` | `jwtProperties.getExpiration()` |
| `FlywayConfig.java` | `@Value("${DB_PASSWORD}")` | 保留（DataSource 配置不走属性类） |
| `AichatApplication.java` | 多处 `@Value` | 注入对应属性类 |
| `ImageService.java` | 9 处 `@Value` | `private final ImageProperties imageProperties` |
| `MemoryService.java` | 5 处 `@Value` | `private final MemoryProperties memoryProperties` |
| `MemoryChromaService.java` | `@Value` | `private final ChromaDbProperties chromaDbProperties` |
| `SummaryService.java` | 3 处 `@Value` | `private final SummaryProperties summaryProperties` |
| `PromptsHubService.java` | 2 处 `@Value` | 保留（上传路径不归属性类） |
| `ChunkingService.java` | 2 处 `@Value` | `private final RagProperties ragProperties` |
| `SearchService.java` | 2 处 `@Value` | `private final QianfanProperties qianfanProperties` |
| `TavilySearchService.java` | 2 处 `@Value` | `private final TavilyProperties tavilyProperties` |
| `EmailService.java` | `@Value` | `private final MailProperties mailProperties` |
| `ChromaDBConfig.java` | 4 处 `@Value` | `private final ChromaDbProperties chromaDbProperties` + `EmbeddingProperties` |
| `ChromaDBLauncher.java` | `@Value` | `chromaDbProperties.getUrl()` |
| `WebConfig.java` | 2 处 `@Value` | 保留（文件上传路径不归属性类） |
| `AppConfig.java` | `@Value` | `private final EncryptionProperties encryptionProperties` |
| `AuthController.java` | `@Value` | `private final AdminProperties adminProperties` |

### 步骤 4：启动校验

所有属性类已通过 `@Validated` + Bean Validation 注解，Spring Boot 启动时会自动校验。如果缺少必需的环境变量（如 `${JWT_SECRET}` 未设置），**启动会立即失败**并给出明确错误信息，而不是运行时抛 NPE。

---

## 改造成本估算

| 步骤 | 文件数 | 操作 | 复杂度 | 耗时 |
|------|--------|------|--------|------|
| 1. 修正 .gitignore | 1 | 删除 1 行 | 低 | 1 分钟 |
| 2. Profile 分离 | 3 | 新建 2 个 + 精简 1 个 | 中 | 15 分钟 |
| 3. 配置属性类 | 13 | 新建 | 中 | 30 分钟 |
| 3. 使用方迁移 | 18 | 修改 `@Value` → 属性类注入 | 中 | 30 分钟 |
| 4. 启动校验 | 0 | 属性类自带 | 低 | 0 分钟（已内置） |

---

## 改造收益

| 改造前 | 改造后 |
|--------|--------|
| 46 处 `@Value` 散落 | 13 个集中管理的属性类 |
| 变量名拼写错误运行时才发现 | 启动时校验，立即报错 |
| 切换环境需手动改配置 | `--spring.profiles.active=prod` 一键切换 |
| `.env.example` 不可见 | 提交到仓库，协作者一目了然 |
| 修改配置键名需全局搜索 | IDE 重构属性类字段名自动更新所有引用 |

---

## 实施建议

建议按顺序执行：
1. **先修正 `.gitignore`**（无风险，1 分钟）
2. **再 Profile 分离**（不影响代码逻辑，15 分钟）
3. **最后配置属性类 + 迁移**（需要改动 18 个文件，约 1 小时）

建议逐步提交，每步验证一次启动成功。
