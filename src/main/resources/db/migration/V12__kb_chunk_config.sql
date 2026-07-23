-- V12: 知识库添加可配置分块参数
ALTER TABLE knowledge_bases
    ADD COLUMN chunk_size INT DEFAULT 500 COMMENT '分块大小（字符数），NULL 使用系统默认',
    ADD COLUMN chunk_overlap INT DEFAULT 50 COMMENT '分块重叠字符数，NULL 使用系统默认';
