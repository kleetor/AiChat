-- ============================================================
-- Flyway 基线迁移 V1
-- 基于实际数据库 snapshot (2026-07-02)
-- ============================================================

CREATE TABLE IF NOT EXISTS users (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    password          VARCHAR(255)    NOT NULL,
    username          VARCHAR(255)    NOT NULL,
    pid               VARCHAR(6)      NOT NULL,
    failed_attempts   INT             NULL DEFAULT 0,
    email             VARCHAR(255)    NOT NULL,
    password_encrypted VARCHAR(255)   NULL,
    balance           DECIMAL(12,4)   NOT NULL,
    reserved_balance  DECIMAL(12,4)   NOT NULL DEFAULT 0.0000,
    enabled           BIT(1)          NOT NULL,
    role              VARCHAR(20)     NOT NULL,
    created_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version           BIGINT          NULL,
    signature         VARCHAR(200)    NULL,
    avatar_url        VARCHAR(500)    NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS conversations (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    created_at DATETIME        NOT NULL,
    title      VARCHAR(255)    NULL,
    user_id    BIGINT          NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS chat_messages (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    ai_reply        TEXT            NOT NULL,
    timestamp       DATETIME        NOT NULL,
    user_message    TEXT            NOT NULL,
    conversation_id BIGINT          NOT NULL,
    user_id         BIGINT          NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (conversation_id) REFERENCES conversations(id),
    INDEX idx_conv_time (conversation_id, timestamp)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS conversation_summaries (
    id                            BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id               BIGINT          NOT NULL,
    summary                       TEXT            NOT NULL,
    message_count_at_generation   INT             NOT NULL,
    version                       INT             NOT NULL DEFAULT 1,
    created_at                    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY (conversation_id),
    FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS token_usages (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    balance_after    DECIMAL(12,4)   NOT NULL,
    balance_before   DECIMAL(12,4)   NOT NULL,
    conversation_id  BIGINT          NOT NULL,
    cost_amount      DECIMAL(12,4)   NOT NULL,
    created_at       DATETIME(6)     NOT NULL,
    input_tokens     BIGINT          NOT NULL,
    model_config_id  BIGINT          NOT NULL,
    model_name       VARCHAR(255)    NOT NULL,
    output_tokens    BIGINT          NOT NULL,
    total_tokens     BIGINT          NOT NULL,
    user_id          BIGINT          NOT NULL,
    INDEX idx_user_time (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS recharge_orders (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    amount              DECIMAL(12,2)   NOT NULL,
    created_at          DATETIME(6)     NOT NULL,
    order_no            VARCHAR(32)     NOT NULL UNIQUE,
    paid_at             DATETIME(6)     NULL,
    pay_channel         VARCHAR(20)     NULL,
    status              VARCHAR(20)     NOT NULL,
    third_party_order_id VARCHAR(64)    NULL,
    user_id             BIGINT          NOT NULL,
    review_comment      VARCHAR(500)    NULL,
    review_status       VARCHAR(20)     NULL,
    reviewed_at         DATETIME(6)     NULL,
    reviewer_id         BIGINT          NULL,
    sponsor_image_path  VARCHAR(500)    NULL,
    user_name           VARCHAR(50)     NULL,
    user_pid            VARCHAR(10)     NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS model_configs (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    api_key             VARCHAR(255)    NOT NULL,
    api_url             VARCHAR(255)    NOT NULL,
    model_name          VARCHAR(255)    NOT NULL,
    user_id             BIGINT          NOT NULL,
    display_name        VARCHAR(100)    NULL,
    input_token_price   DECIMAL(12,6)   NOT NULL,
    output_token_price  DECIMAL(12,6)   NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS prompts (
    id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    content TEXT            NOT NULL,
    name    VARCHAR(100)    NOT NULL,
    user_id BIGINT          NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS prompts_hub (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    content      TEXT            NOT NULL,
    created_at   DATETIME(6)     NULL,
    image_url    VARCHAR(500)    NULL,
    likes_count  INT             NULL,
    name         VARCHAR(100)    NOT NULL,
    user_id      BIGINT          NOT NULL,
    user_message VARCHAR(500)    NULL,
    user_name    VARCHAR(50)     NULL,
    featured     BIT(1)          NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS comments (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    content       TEXT            NOT NULL,
    created_at    DATETIME(6)     NULL,
    likes_count   INT             NULL,
    parent_id     BIGINT          NULL,
    prompt_id     BIGINT          NOT NULL,
    reply_to_name VARCHAR(50)     NULL,
    user_id       BIGINT          NOT NULL,
    user_name     VARCHAR(50)     NULL,
    INDEX idx_parent (parent_id),
    INDEX idx_prompt (prompt_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS user_likes (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    created_at  DATETIME(6)     NULL,
    target_id   BIGINT          NOT NULL,
    target_type VARCHAR(20)     NOT NULL,
    user_id     BIGINT          NOT NULL,
    UNIQUE KEY uk_user_target (user_id, target_type, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS notifications (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    content         VARCHAR(500)    NULL,
    created_at      DATETIME(6)     NULL,
    is_read         BIT(1)          NULL,
    prompt_id       BIGINT          NULL,
    target_user_id  BIGINT          NOT NULL,
    type            VARCHAR(30)     NOT NULL,
    comment_id      BIGINT          NULL,
    from_user_name  VARCHAR(50)     NULL,
    title           VARCHAR(200)    NOT NULL,
    INDEX idx_user_read_time (target_user_id, is_read, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS friendships (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    created_at  DATETIME(6)     NULL,
    friend_id   BIGINT          NOT NULL,
    status      VARCHAR(20)     NOT NULL,
    user_id     BIGINT          NOT NULL,
    UNIQUE KEY (user_id, friend_id),
    INDEX idx_friend (friend_id),
    INDEX idx_user_status (user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS friend_messages (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    content       TEXT            NOT NULL,
    created_at    DATETIME(6)     NULL,
    friendship_id BIGINT          NOT NULL,
    is_read       BIT(1)          NULL,
    receiver_id   BIGINT          NOT NULL,
    sender_id     BIGINT          NOT NULL,
    INDEX idx_friendship_time (friendship_id, created_at),
    INDEX idx_receiver_read (receiver_id, is_read)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS knowledge_bases (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    chunk_count INT             NOT NULL,
    created_at  DATETIME(6)     NOT NULL,
    description VARCHAR(500)    NULL,
    doc_count   INT             NOT NULL,
    name        VARCHAR(100)    NOT NULL,
    total_size  BIGINT          NOT NULL,
    updated_at  DATETIME(6)     NOT NULL,
    user_id     BIGINT          NOT NULL,
    visibility  VARCHAR(20)     NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS kb_documents (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    chunk_count INT             NOT NULL,
    created_at  DATETIME(6)     NOT NULL,
    error_msg   TEXT            NULL,
    file_name   VARCHAR(255)    NOT NULL,
    file_size   BIGINT          NOT NULL,
    file_type   VARCHAR(10)     NOT NULL,
    kb_id       BIGINT          NOT NULL,
    s3_key      VARCHAR(500)    NULL,
    status      VARCHAR(20)     NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS memory_items (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id           BIGINT          NOT NULL,
    chroma_id         VARCHAR(100)    NOT NULL,
    `value`           TEXT            NOT NULL,
    original_value    TEXT            NULL,
    detail_level      VARCHAR(20)     NOT NULL,
    source            VARCHAR(30)     NOT NULL,
    created_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_accessed_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    access_count      INT             NOT NULL DEFAULT 0,
    conversation_id   BIGINT          NULL,
    enabled           TINYINT(1)      NOT NULL DEFAULT 1,
    UNIQUE KEY uk_chroma_id (chroma_id),
    INDEX idx_user_dl_time (user_id, detail_level, last_accessed_at DESC),
    INDEX idx_user_enabled (user_id, enabled),
    INDEX idx_decay_check (detail_level, last_accessed_at),
    INDEX idx_conversation (conversation_id),
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
