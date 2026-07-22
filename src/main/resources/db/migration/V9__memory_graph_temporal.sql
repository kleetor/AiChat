-- ============================================================
-- Flyway 迁移 V9: 记忆系统 - 知识图谱 + 时态管理
-- ============================================================

-- ==================== Part 1: memory_items 时态管理字段 ====================

ALTER TABLE memory_items
    ADD COLUMN valid_from       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '事实生效时间',
    ADD COLUMN valid_until      DATETIME    NULL     COMMENT '事实失效时间（NULL=当前有效）',
    ADD COLUMN superseded_by_id BIGINT      NULL     COMMENT '被哪条新记忆取代',
    ADD COLUMN status           VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/SUPERSEDED/EXPIRED';

ALTER TABLE memory_items
    ADD INDEX idx_status (user_id, status),
    ADD CONSTRAINT fk_superseded_by
        FOREIGN KEY (superseded_by_id) REFERENCES memory_items(id) ON DELETE SET NULL;

-- ==================== Part 2: 知识图谱 - 实体表 ====================

CREATE TABLE memory_entities (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT        NOT NULL,
    name        VARCHAR(200)  NOT NULL COMMENT '实体名称，如 张三/阿里/杭州',
    type        VARCHAR(30)   NOT NULL COMMENT 'PERSON/ORG/LOCATION/PRODUCT/MISC',
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_entity (user_id, name),
    INDEX idx_user_type (user_id, type),
    FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ==================== Part 3: 知识图谱 - 记忆-实体关联表 ====================

CREATE TABLE memory_item_entities (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    memory_item_id  BIGINT        NOT NULL,
    entity_id       BIGINT        NOT NULL,
    role            VARCHAR(20)   NOT NULL COMMENT 'SUBJECT/OBJECT',
    UNIQUE KEY uk_mem_entity_role (memory_item_id, entity_id, role),
    INDEX idx_entity (entity_id),
    FOREIGN KEY (memory_item_id) REFERENCES memory_items(id) ON DELETE CASCADE,
    FOREIGN KEY (entity_id)      REFERENCES memory_entities(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ==================== Part 4: 知识图谱 - 实体间关系表 ====================

CREATE TABLE memory_relations (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    subject_id      BIGINT        NOT NULL COMMENT '主语实体',
    predicate       VARCHAR(100)  NOT NULL COMMENT '关系谓词，如 工作于/居住在/毕业于',
    object_id       BIGINT        NOT NULL COMMENT '宾语实体',
    source_item_id  BIGINT        NULL     COMMENT '从哪条记忆提取的关系',
    user_id         BIGINT        NOT NULL,
    valid_from      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    valid_until     DATETIME      NULL,
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_subject (subject_id),
    INDEX idx_object  (object_id),
    INDEX idx_user_pred (user_id, predicate),
    FOREIGN KEY (subject_id)     REFERENCES memory_entities(id) ON DELETE CASCADE,
    FOREIGN KEY (object_id)      REFERENCES memory_entities(id) ON DELETE CASCADE,
    FOREIGN KEY (source_item_id) REFERENCES memory_items(id)   ON DELETE SET NULL,
    FOREIGN KEY (user_id)        REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
