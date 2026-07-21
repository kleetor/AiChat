ALTER TABLE model_configs
    ADD COLUMN supports_tool_calling TINYINT(1) NOT NULL DEFAULT 0
    COMMENT '是否支持工具调用(Function Calling)';
