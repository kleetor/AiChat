-- Token 黑名单表 — 登出 JWT 持久化存储，防止服务重启后已登出 Token 恢复有效
CREATE TABLE IF NOT EXISTS token_blacklist (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at DATETIME NOT NULL,
    INDEX idx_expires_at (expires_at)
);
