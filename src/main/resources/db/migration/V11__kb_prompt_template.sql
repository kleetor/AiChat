-- V11: 知识库添加可自定义 Prompt 模板
ALTER TABLE knowledge_bases
    ADD COLUMN prompt_template VARCHAR(2000) DEFAULT NULL
    COMMENT '回答风格模板，支持 {context} / {query} 占位符。NULL 时使用默认模板。';
