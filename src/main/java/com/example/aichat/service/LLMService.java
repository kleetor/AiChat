package com.example.aichat.service;

import com.example.aichat.config.MemoryLLMConfig;
import com.example.aichat.model.ModelConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * 底层 LLM 调用服务。
 * 从 ChatService 中抽取，供 ChatService、MemoryService、SummaryService 共同使用，
 * 解除 ChatService ↔ MemoryService 循环依赖。
 */
@Service
public class LLMService {

    private static final Logger logger = LoggerFactory.getLogger(LLMService.class);
    private static final double TOKEN_ESTIMATE_RATIO = 1.3;

    private final RestTemplate restTemplate;
    private final ExecutorService chatExecutorService;
    private final MemoryLLMConfig memoryLLMConfig;
    private final ObjectMapper objectMapper;

    public LLMService(RestTemplate restTemplate,
                      ExecutorService chatExecutorService,
                      MemoryLLMConfig memoryLLMConfig) {
        this.restTemplate = restTemplate;
        this.chatExecutorService = chatExecutorService;
        this.memoryLLMConfig = memoryLLMConfig;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 异步调用 LLM，返回 TokenUsageResult。
     */
    public CompletableFuture<TokenUsageResult> callAsyncWithUsage(ArrayNode messages, ModelConfig config) {
        return CompletableFuture.supplyAsync(() -> {
            String apiUrl = config.getApiUrl();
            String apiKey = config.getApiKey();
            String modelName = config.getModelName();

            if (apiKey != null && apiKey.startsWith("AES:")) {
                logger.error("ModelConfig id={} 的 API Key 解密失败！", config.getId());
            }

            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", modelName);
            requestBody.set("messages", messages);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            HttpEntity<String> entity = new HttpEntity<>(requestBody.toString(), headers);
            try {
                ResponseEntity<String> response = restTemplate.exchange(
                        apiUrl, HttpMethod.POST, entity, String.class);
                JsonNode root = objectMapper.readTree(response.getBody());

                String content = root.get("choices").get(0).get("message").get("content").asText();

                long promptTokens = 0, completionTokens = 0;
                JsonNode usage = root.get("usage");
                if (usage != null) {
                    if (usage.has("prompt_tokens")) promptTokens = usage.get("prompt_tokens").asLong();
                    if (usage.has("completion_tokens")) completionTokens = usage.get("completion_tokens").asLong();
                }

                if (promptTokens == 0 && completionTokens == 0 && content != null && !content.isEmpty()) {
                    String allMessagesText = messages.toString();
                    promptTokens = Math.round(allMessagesText.length() * TOKEN_ESTIMATE_RATIO);
                    completionTokens = Math.round(content.length() * TOKEN_ESTIMATE_RATIO);
                    logger.warn("API未返回token用量，使用估算值: prompt={}, completion={}", promptTokens, completionTokens);
                }

                return new TokenUsageResult(content, promptTokens, completionTokens);
            } catch (Exception e) {
                logger.error("调用AI服务失败", e);
                return new TokenUsageResult("AI回复失败：" + e.getMessage(), 0, 0);
            }
        }, chatExecutorService);
    }

    /**
     * 同步调用 LLM (使用 MemoryLLMConfig 环境变量配置)。
     * 供记忆提取、摘要生成、阶梯压缩等内部场景使用。
     */
    public String chatSync(String prompt) {
        ModelConfig config = new ModelConfig();
        config.setApiKey(memoryLLMConfig.getApiKey());
        config.setApiUrl(memoryLLMConfig.getApiUrl());
        config.setModelName(memoryLLMConfig.getModelName());

        ArrayNode messages = objectMapper.createArrayNode();
        ObjectNode userNode = messages.addObject();
        userNode.put("role", "user");
        userNode.put("content", prompt);

        TokenUsageResult result = callAsyncWithUsage(messages, config).join();
        return result.getReply();
    }

    // ==================== TokenUsageResult ====================

    public static class TokenUsageResult {
        private final String reply;
        private final long inputTokens;
        private final long outputTokens;
        private BigDecimal costAmount;

        public TokenUsageResult(String reply, long inputTokens, long outputTokens) {
            this.reply = reply;
            this.inputTokens = inputTokens;
            this.outputTokens = outputTokens;
        }

        public String getReply() { return reply; }
        public long getInputTokens() { return inputTokens; }
        public long getOutputTokens() { return outputTokens; }
        public BigDecimal getCostAmount() { return costAmount; }
        public void setCostAmount(BigDecimal costAmount) { this.costAmount = costAmount; }
    }
}
