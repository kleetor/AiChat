-- RAG 知识库 - 新增表
-- 执行方式: mysql -u root -p ai_chat_db < kb_tables.sql

CREATE TABLE IF NOT EXISTS knowledge_bases (
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
    INDEX idx_kb_user (user_id)
);

CREATE TABLE IF NOT EXISTS kb_documents (
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
    INDEX idx_kb_doc (kb_id)
);
