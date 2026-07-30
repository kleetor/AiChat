-- 提示词级上下文隔离：会话与消息增加 prompt_id
-- 私有/共享模式：NULL = 共享，非NULL = 仅该提示词可见

ALTER TABLE conversations
    ADD COLUMN prompt_id BIGINT NULL,
    ADD INDEX idx_user_prompt (user_id, prompt_id),
    ADD FOREIGN KEY fk_conv_prompt (prompt_id) REFERENCES prompts(id) ON DELETE SET NULL;

ALTER TABLE chat_messages
    ADD COLUMN prompt_id BIGINT NULL,
    ADD INDEX idx_conv_prompt (conversation_id, prompt_id);
