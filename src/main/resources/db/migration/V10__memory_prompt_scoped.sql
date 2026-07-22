-- ============================================================
-- Flyway 迁移 V10: 记忆系统 - 提示词级记忆隔离
-- ============================================================

ALTER TABLE memory_items
    ADD COLUMN prompt_id BIGINT NULL COMMENT '提示词角色ID（NULL=共享记忆，所有角色可见）';

ALTER TABLE memory_items
    ADD INDEX idx_user_prompt (user_id, prompt_id, last_accessed_at DESC),
    ADD CONSTRAINT fk_memory_prompt
        FOREIGN KEY (prompt_id) REFERENCES prompts(id) ON DELETE SET NULL;
