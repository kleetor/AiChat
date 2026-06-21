-- 长期记忆 SQL 建表脚本
-- 执行前确保数据库 ai_chat_db 已存在

-- 对话摘要表
CREATE TABLE IF NOT EXISTS conversation_summaries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT NOT NULL UNIQUE,
    summary TEXT NOT NULL,
    message_count_at_generation INT NOT NULL,
    version INT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE
);

-- 长期记忆表（MySQL 存放生命周期状态，ChromaDB 存放全文向量）
CREATE TABLE IF NOT EXISTS memory_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    chroma_id VARCHAR(100) NOT NULL COMMENT 'ChromaDB 中对应的文档ID',

    -- 记忆内容
    `value` TEXT NOT NULL COMMENT '当前记忆文本(经阶梯压缩后的版本)',
    original_value TEXT NULL COMMENT '首次提取的原文，永不压缩',

    -- 生命周期状态
    detail_level VARCHAR(20) NOT NULL DEFAULT 'FULL'
        COMMENT 'FULL(原文)/BRIEF(200字摘要)/TITLE(一行50字)',
    source VARCHAR(30) NOT NULL DEFAULT 'AUTO' COMMENT 'AUTO/MANUAL',

    -- 人类记忆核心: 时间轴
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记忆出生时间',
    last_accessed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后被访问/查询/注入的时间',
    access_count INT NOT NULL DEFAULT 0 COMMENT '被访问次数',

    -- 元数据
    conversation_id BIGINT NULL COMMENT '来源对话',
    enabled TINYINT(1) NOT NULL DEFAULT 1,

    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE SET NULL,
    INDEX idx_user_dl_time (user_id, detail_level, last_accessed_at DESC),
    INDEX idx_user_enabled (user_id, enabled),
    INDEX idx_decay_check (detail_level, last_accessed_at),
    UNIQUE KEY uk_chroma_id (chroma_id)
);
