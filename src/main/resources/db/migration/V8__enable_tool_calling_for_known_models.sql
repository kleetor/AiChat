-- V8: 为已知支持 Function Calling 的模型启用工具调用
UPDATE model_configs SET supports_tool_calling = 1
WHERE supports_tool_calling = 0
  AND (LOWER(model_name) LIKE '%deepseek%'
    OR LOWER(model_name) LIKE '%gpt%'
    OR LOWER(model_name) LIKE '%claude%'
    OR LOWER(model_name) LIKE '%gemini%'
    OR LOWER(model_name) LIKE '%qwen%'
    OR LOWER(model_name) LIKE '%glm%');
