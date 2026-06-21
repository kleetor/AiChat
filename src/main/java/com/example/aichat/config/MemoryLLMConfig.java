package com.example.aichat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 记忆提取/压缩用的独立 LLM 配置。
 * 通过环境变量注入，不依赖数据库 ModelConfig 表。
 */
@Component
@ConfigurationProperties(prefix = "memory.llm")
public class MemoryLLMConfig {

    /** API Key (环境变量 MEMORY_LLM_API_KEY) */
    private String apiKey = "";

    /** API URL (环境变量 MEMORY_LLM_API_URL) */
    private String apiUrl = "https://api.deepseek.com/v1/chat/completions";

    /** 模型名称 (环境变量 MEMORY_LLM_MODEL_NAME) */
    private String modelName = "deepseek-chat";

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getApiUrl() { return apiUrl; }
    public void setApiUrl(String apiUrl) { this.apiUrl = apiUrl; }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
}
