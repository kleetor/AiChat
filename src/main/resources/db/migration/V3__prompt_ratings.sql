-- ============================================================
-- Flyway 迁移 V3 — 评分记录表
-- 日期：2026-07-08
-- ============================================================

CREATE TABLE IF NOT EXISTS prompt_ratings (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT          NOT NULL,
    prompt_id   BIGINT          NOT NULL,
    rating      TINYINT         NOT NULL COMMENT '评分 1-5',
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_prompt (user_id, prompt_id),
    FOREIGN KEY (user_id)   REFERENCES users(id),
    FOREIGN KEY (prompt_id) REFERENCES prompts_hub(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
