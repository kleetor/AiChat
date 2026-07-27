-- V13: 性能索引优化 (2026-07-26)
-- 基于 DAU 500-800 量级的实际查询模式分析

-- 1. 用户模糊搜索（当前 users 表仅 PRIMARY，LIKE 导致全表扫描）
ALTER TABLE users ADD FULLTEXT INDEX ft_user (username, email, pid);
ALTER TABLE users ADD INDEX idx_username (username);

-- 2. Token 用量聚合查询（idx_user_time 不覆盖 cost_amount）
ALTER TABLE token_usages ADD INDEX idx_user_date_cost (user_id, created_at, cost_amount);

-- 3. 记忆按状态 + 时间过滤（存量 7 个索引均不覆盖此路径）
ALTER TABLE memory_items ADD INDEX idx_user_status_accessed (user_id, status, last_accessed_at);
