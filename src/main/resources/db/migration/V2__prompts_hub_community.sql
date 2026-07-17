-- ============================================================
-- Flyway 迁移 V2 — PromptHUB 社区功能 DDL
-- 日期：2026-07-08
-- ============================================================

-- ============================================================
-- 1. 扩展 prompts_hub 表（+10 列）
-- ============================================================
ALTER TABLE prompts_hub
    ADD COLUMN description        VARCHAR(500)    NULL                COMMENT '提示词描述',
    ADD COLUMN category           VARCHAR(50)     NULL                COMMENT '分类',
    ADD COLUMN tags               JSON            NULL                COMMENT '标签 (JSON数组)',
    ADD COLUMN model_support      VARCHAR(200)    NULL                COMMENT '适用模型',
    ADD COLUMN status             VARCHAR(20)     NOT NULL DEFAULT 'published' COMMENT '状态: draft/published/pending_review/removed',
    ADD COLUMN version            VARCHAR(20)     NOT NULL DEFAULT 'v1.0'     COMMENT '版本号',
    ADD COLUMN original_prompt_id BIGINT          NULL                COMMENT 'Fork 来源ID',
    ADD COLUMN view_count         INT             NOT NULL DEFAULT 0  COMMENT '浏览量',
    ADD COLUMN save_count         INT             NOT NULL DEFAULT 0  COMMENT '收藏量',
    ADD COLUMN avg_rating         DECIMAL(3,2)    NOT NULL DEFAULT 0  COMMENT '平均评分',
    ADD COLUMN updated_at         DATETIME        NULL                COMMENT '更新时间';

ALTER TABLE prompts_hub
    ADD INDEX idx_category (category),
    ADD INDEX idx_status (status),
    ADD INDEX idx_user_id (user_id);

-- ============================================================
-- 2. 系统规则表
-- ============================================================
CREATE TABLE system_rules (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(200)    NOT NULL                COMMENT '规则名称',
    content     TEXT            NOT NULL                COMMENT '规则内容 (system prompt)',
    is_active   BIT(1)          NOT NULL DEFAULT 1      COMMENT '是否启用',
    sort_order  INT             NOT NULL DEFAULT 0      COMMENT '排序权重，越小越靠前',
    updated_at  DATETIME        NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 3. 收藏表
-- ============================================================
CREATE TABLE favorites (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT          NOT NULL                COMMENT '用户ID',
    prompt_id   BIGINT          NOT NULL                COMMENT '提示词ID',
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_prompt (user_id, prompt_id),
    INDEX idx_user (user_id),
    INDEX idx_prompt (prompt_id),
    FOREIGN KEY (user_id)   REFERENCES users(id),
    FOREIGN KEY (prompt_id) REFERENCES prompts_hub(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 4. 使用历史表
-- ============================================================
CREATE TABLE usage_history (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT          NOT NULL                COMMENT '用户ID',
    prompt_id   BIGINT          NOT NULL                COMMENT '提示词ID',
    action      VARCHAR(20)     NOT NULL                COMMENT '操作类型: save/copy/use',
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_time (user_id, created_at),
    FOREIGN KEY (user_id)   REFERENCES users(id),
    FOREIGN KEY (prompt_id) REFERENCES prompts_hub(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 5. 关注表
-- ============================================================
CREATE TABLE follows (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    follower_id  BIGINT          NOT NULL                COMMENT '关注者ID',
    followed_id  BIGINT          NOT NULL                COMMENT '被关注者ID',
    created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_follower_followed (follower_id, followed_id),
    INDEX idx_followed (followed_id),
    INDEX idx_follower (follower_id),
    FOREIGN KEY (follower_id) REFERENCES users(id),
    FOREIGN KEY (followed_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 6. 全文搜索索引（中文分词）
-- 注意：需在 MySQL 中先执行 SET GLOBAL ngram_token_size=1 并重启
--      如果 ngram 不可用，索引回退为无解析器的普通 FULLTEXT
-- ============================================================
ALTER TABLE prompts_hub
    ADD FULLTEXT INDEX ft_search (name, description, content) WITH PARSER ngram;
