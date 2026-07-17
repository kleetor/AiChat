CREATE TABLE admin_operation_logs (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    admin_id        BIGINT      NOT NULL,
    admin_username  VARCHAR(50) NOT NULL,
    action          VARCHAR(50) NOT NULL,
    target_type     VARCHAR(50) NOT NULL,
    target_id       BIGINT      NULL,
    detail          TEXT        NULL,
    ip_address      VARCHAR(45) NULL,
    created_at      DATETIME    NOT NULL,
    INDEX idx_admin_id   (admin_id),
    INDEX idx_action     (action),
    INDEX idx_target     (target_type, target_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
