package com.example.aichat.service;

import com.example.aichat.model.ChatMessage;
import com.example.aichat.model.Conversation;
import com.example.aichat.model.ModelConfig;
import com.example.aichat.model.TokenUsage;
import com.example.aichat.repository.ConversationRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 流式聊天服务 —— 从 ChatService 拆分，负责 SSE 流式响应的核心实现。
 * 封装 HTTP 请求、SSE 解析、缓冲区管理和事件发送。
 */
@Service
public class ChatStreamService {

    private static final Logger logger = LoggerFactory.getLogger(ChatStreamService.class);
    private static final double TOKEN_ESTIMATE_RATIO = 1.3;

    private final CloseableHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ThreadPoolTaskExecutor chatExecutorService;
    private final ChatHistoryService chatHistoryService;
    private final BillingService billingService;
    private final ConversationRepository conversationRepository;
    private final ChatPostProcessor chatPostProcessor;

    public ChatStreamService(CloseableHttpClient httpClient,
                              ThreadPoolTaskExecutor chatExecutorService,
                              ChatHistoryService chatHistoryService,
                              BillingService billingService,
                              ConversationRepository conversationRepository,
                              ChatPostProcessor chatPostProcessor) {
        this.httpClient = httpClient;
        this.objectMapper = new ObjectMapper();
        this.chatExecutorService = chatExecutorService;
        this.chatHistoryService = chatHistoryService;
        this.billingService = billingService;
        this.conversationRepository = conversationRepository;
        this.chatPostProcessor = chatPostProcessor;
    }

    /**
     * 创建 SSE 流式响应的 SseEmitter，在后台线程中执行 LLM API 调用和数据推送。
     */
    public SseEmitter streamDeepSeek(ArrayNode messages, ModelConfig config,
                                      Long conversationId, String userMessage, Long userId,
                                      Boolean longMemoryEnabled) {
        SseEmitter emitter = new SseEmitter(120_000L);
        String apiUrl = config.getApiUrl();
        String apiKey = config.getApiKey();
        String modelName = config.getModelName();
        Long modelConfigId = config.getId();

        // 确保流断开时也能完成扣费
        AtomicBoolean billingDone = new AtomicBoolean(false);
        AtomicLong promptTokensRef = new AtomicLong(0);
        AtomicLong completionTokensRef = new AtomicLong(0);

        Runnable doBilling = () -> {
            if (billingDone.compareAndSet(false, true)) {
                long pt = promptTokensRef.get();
                long ct = completionTokensRef.get();
                if (userId != null && (pt > 0 || ct > 0)) {
                    try {
                        TokenUsage usage = billingService.deductTokens(userId, modelConfigId, pt, ct, conversationId);
                        if (usage != null) {
                            ObjectNode tokenInfo = objectMapper.createObjectNode();
                            tokenInfo.put("inputTokens", usage.getInputTokens());
                            tokenInfo.put("outputTokens", usage.getOutputTokens());
                            tokenInfo.put("costAmount", usage.getCostAmount());
                            try {
                                emitter.send(SseEmitter.event()
                                        .name("token_usage")
                                        .data(tokenInfo.toString(), org.springframework.http.MediaType.APPLICATION_JSON));
                            } catch (Exception ignore) {
                                logger.debug("发送token用量失败(客户端已断开)");
                            }
                        }
                    } catch (Exception e) {
                        logger.error("流式聊天扣费失败: {}", e.getMessage());
                    }
                }
            }
        };

        emitter.onCompletion(doBilling);
        emitter.onTimeout(doBilling);

        // SSRF 防护: 验证 API URL 不指向内网
        com.example.aichat.util.NetworkUtils.validateExternalUrl(apiUrl);

        if (apiKey != null && apiKey.startsWith("AES:")) {
            logger.error("ModelConfig id={} 的 API Key 解密失败！当前加密密钥与创建配置时的密钥不匹配。"
                    + "请检查 ENCRYPTION_KEY 环境变量是否与当初一致，或在后台重新保存模型配置以使用当前密钥重新加密。",
                    modelConfigId);
        }
        logger.debug("ChatStreamService 使用 ModelConfig: id={}, displayName={}, apiUrl={}, apiKey前6位={}, model={}",
                modelConfigId, config.getDisplayName(), apiUrl,
                apiKey != null ? apiKey.substring(0, Math.min(6, apiKey.length())) : "null",
                modelName);

        Runnable task = () -> {
            long promptTokens = 0;
            long completionTokens = 0;
            try {
                ObjectNode requestBody = objectMapper.createObjectNode();
                requestBody.put("model", modelName);
                requestBody.put("stream", true);
                requestBody.set("messages", messages);

                HttpPost postRequest = new HttpPost(apiUrl);
                postRequest.setHeader("Content-Type", "application/json");
                postRequest.setHeader("Authorization", "Bearer " + apiKey);
                postRequest.setEntity(new StringEntity(requestBody.toString(), StandardCharsets.UTF_8));

                try (CloseableHttpResponse response = httpClient.execute(postRequest)) {
                    int statusCode = response.getCode();
                    if (statusCode >= 400) {
                        String errorMsg = "AI 服务返回错误: HTTP " + statusCode;
                        try {
                            if (response.getEntity() != null) {
                                errorMsg += " - " + new String(
                                        response.getEntity().getContent().readAllBytes(),
                                        StandardCharsets.UTF_8);
                            }
                        } catch (Exception e) {
                            logger.warn("读取错误响应内容失败", e);
                        }
                        emitter.send(SseEmitter.event()
                                .name("error")
                                .data(errorMsg, org.springframework.http.MediaType.TEXT_PLAIN));
                        emitter.complete();
                        return;
                    }

                    if (response.getEntity() == null) {
                        emitter.send("AI 服务无返回内容");
                        emitter.complete();
                        return;
                    }

                    StringBuilder fullResponse = new StringBuilder();
                    StringBuilder chunkBuf = new StringBuilder();

                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(response.getEntity().getContent(), StandardCharsets.UTF_8))) {

                    String line;
                    int eventCount = 0;
                    final int flushEvery = 4;
                    int sinceLastFlush = 0;
                    while ((line = reader.readLine()) != null) {
                        if (line.trim().isEmpty()) {
                            continue;
                        }
                        try {
                            String payload = line;
                            if (payload.startsWith("data:")) {
                                payload = payload.substring(5).trim();
                            }
                            if (payload.isEmpty()) {
                                continue;
                            }
                            if ("[DONE]".equals(payload)) {
                                break;
                            }

                            JsonNode root = objectMapper.readTree(payload);

                            JsonNode usage = root.get("usage");
                            if (usage != null) {
                                if (usage.has("prompt_tokens")) {
                                    promptTokens = usage.get("prompt_tokens").asLong();
                                }
                                if (usage.has("completion_tokens")) {
                                    completionTokens = usage.get("completion_tokens").asLong();
                                }
                            }

                            JsonNode choices = root.get("choices");
                            if (choices != null && choices.isArray() && choices.size() > 0) {
                                JsonNode delta = choices.get(0).get("delta");
                                if (delta != null && delta.has("content")
                                        && !delta.get("content").isNull()) {
                                    String content = delta.get("content").asText();
                                    if (content == null) continue;
                                    fullResponse.append(content);
                                    chunkBuf.append(content);
                                    sinceLastFlush += content.length();
                                    if (sinceLastFlush >= flushEvery
                                            || containsSentenceEnd(content)) {
                                        String toSend = chunkBuf.toString();
                                        chunkBuf.setLength(0);
                                        sinceLastFlush = 0;
                                        emitter.send(SseEmitter.event()
                                                .id(String.valueOf(eventCount++))
                                                .data(toSend, org.springframework.http.MediaType.TEXT_PLAIN));
                                    }
                                }
                            }
                        } catch (Exception e) {
                            logger.warn("解析流式响应失败", e);
                        }
                    }
                    if (chunkBuf.length() > 0) {
                        try {
                            emitter.send(SseEmitter.event()
                                    .id(String.valueOf(eventCount++))
                                    .data(chunkBuf.toString(), org.springframework.http.MediaType.TEXT_PLAIN));
                        } catch (Exception e) {
                            logger.warn("发送剩余数据失败", e);
                        }
                    }
                }

                String completeResponse = fullResponse.toString();
                Long savedMessageId = null;
                if (!completeResponse.isEmpty()) {
                    ChatMessage saved = chatHistoryService.saveMessage(conversationId, userMessage, completeResponse);
                    savedMessageId = saved.getId();
                    updateConversationTitleIfNeeded(conversationId, userMessage);
                }

                if (promptTokens == 0 && completionTokens == 0 && !completeResponse.isEmpty()) {
                    promptTokens = Math.round(userMessage.length() * TOKEN_ESTIMATE_RATIO);
                    completionTokens = Math.round(completeResponse.length() * TOKEN_ESTIMATE_RATIO);
                    logger.warn("API未返回token用量，使用估算值: prompt={}, completion={}", promptTokens, completionTokens);
                }

                // 记录 token 用量供 emitter 回调使用
                promptTokensRef.set(promptTokens);
                completionTokensRef.set(completionTokens);
                // 正常流程中主动扣费
                doBilling.run();

                // 异步: 记忆提取 + 摘要生成
                chatPostProcessor.triggerAsyncProcessing(userId, conversationId, userMessage, completeResponse, longMemoryEnabled);

                String doneData = savedMessageId != null
                        ? "{\"done\":true,\"messageId\":" + savedMessageId + "}"
                        : "[DONE]";
                emitter.send(SseEmitter.event()
                        .name("done")
                        .data(doneData, org.springframework.http.MediaType.APPLICATION_JSON));
                emitter.complete();
                }
            } catch (Exception e) {
                logger.error("流式聊天异常", e);
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data("AI回复失败：" + e.getMessage(), org.springframework.http.MediaType.TEXT_PLAIN));
                } catch (Exception ignore) {
                    logger.warn("发送错误消息失败", ignore);
                }
                emitter.completeWithError(e);
            }
        };

        chatExecutorService.submit(task);
        return emitter;
    }

    private void updateConversationTitleIfNeeded(Long conversationId, String userMessage) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElse(null);
        if (conversation == null) return;

        String defaultTitle = "新对话";
        if (conversation.getTitle() == null || conversation.getTitle().equals(defaultTitle)) {
            int maxLen = 15;
            String newTitle = userMessage.length() > maxLen
                    ? userMessage.substring(0, maxLen) + "…"
                    : userMessage;
            conversation.setTitle(newTitle);
            conversationRepository.save(conversation);
        }
    }

    private static boolean containsSentenceEnd(String text) {
        if (text == null) return false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '。' || c == '！' || c == '!' || c == '？' || c == '?'
                    || c == '…' || c == '\n' || c == '；' || c == ';') {
                return true;
            }
        }
        return false;
    }
}
