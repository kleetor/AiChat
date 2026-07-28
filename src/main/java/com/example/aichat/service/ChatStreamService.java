package com.example.aichat.service;

import com.example.aichat.model.ChatMessage;
import com.example.aichat.model.Conversation;
import com.example.aichat.model.ModelConfig;
import com.example.aichat.model.TokenUsage;
import com.example.aichat.repository.ConversationRepository;
import com.example.aichat.service.tool.ToolCall;
import com.example.aichat.service.tool.ToolCallAccumulator;
import com.example.aichat.service.tool.ToolDefinition;
import com.example.aichat.service.tool.ToolRegistry;
import com.example.aichat.service.tool.ToolResult;
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
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.TimeUnit;

/**
 * 流式聊天服务 —— 从 ChatService 拆分，负责 SSE 流式响应的核心实现。
 * 封装 HTTP 请求、SSE 解析、缓冲区管理和事件发送。
 */
@Service
public class ChatStreamService {

    private static final Logger logger = LoggerFactory.getLogger(ChatStreamService.class);

    /** 客户端断开连接时抛出的异常，用于中止读取循环，阻止消息保存 */
    static class ClientDisconnectedException extends RuntimeException {
        ClientDisconnectedException() { super("客户端已断开"); }
    }
    private static final double TOKEN_ESTIMATE_RATIO = 1.3;
    private static final int MAX_ROUNDS = 3;
    private static final long SSE_TIMEOUT_MS = 300_000L; // 5分钟，含工具执行+Phase2生成时间
    private static final long TOOL_TIMEOUT_SECONDS = 60; // 单个工具执行超时

    private final CloseableHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ThreadPoolTaskExecutor chatExecutorService;
    private final ChatHistoryService chatHistoryService;
    private final BillingService billingService;
    private final ConversationRepository conversationRepository;
    private final ChatPostProcessor chatPostProcessor;
    private final ToolRegistry toolRegistry;

    public ChatStreamService(CloseableHttpClient httpClient,
                              ThreadPoolTaskExecutor chatExecutorService,
                              ChatHistoryService chatHistoryService,
                              BillingService billingService,
                              ConversationRepository conversationRepository,
                              ChatPostProcessor chatPostProcessor,
                              ToolRegistry toolRegistry) {
        this.httpClient = httpClient;
        this.objectMapper = new ObjectMapper();
        this.chatExecutorService = chatExecutorService;
        this.chatHistoryService = chatHistoryService;
        this.billingService = billingService;
        this.conversationRepository = conversationRepository;
        this.chatPostProcessor = chatPostProcessor;
        this.toolRegistry = toolRegistry;
    }

    /**
     * 创建 SSE 流式响应的 SseEmitter（无工具调用，保持与改造前完全一致的行为）。
     */
    public SseEmitter streamDeepSeek(ArrayNode messages, ModelConfig config,
                                      Long conversationId, String userMessage, Long userId,
                                      Boolean longMemoryEnabled, Long promptId) {
        return doStream(messages, config, conversationId, userMessage, userId,
                longMemoryEnabled, promptId, Collections.emptyList(), 0);
    }

    /**
     * 工具调用循环入口。
     * 由 ChatService 调用，第一轮带 tools 参数。
     */
    public SseEmitter streamWithToolLoop(ArrayNode messages, ModelConfig config,
                                          Long conversationId, String userMessage, Long userId,
                                          Boolean longMemoryEnabled, Long promptId,
                                          List<ToolDefinition> tools, int round) {
        return doStream(messages, config, conversationId, userMessage, userId,
                longMemoryEnabled, promptId, tools, round);
    }

    // ==================== 核心流式方法 ====================

    /**
     * 流式聊天的统一实现。
     * tools 为空时行为与改造前的 streamDeepSeek 完全一致。
     * tools 非空时检测 tool_calls delta，触发工具执行和递归。
     */
    private SseEmitter doStream(ArrayNode messages, ModelConfig config,
                                 Long conversationId, String userMessage, Long userId,
                                 Boolean longMemoryEnabled, Long promptId,
                                 List<ToolDefinition> tools, int round) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        String apiUrl = config.getApiUrl();
        String apiKey = config.getApiKey();
        String modelName = config.getModelName();
        Long modelConfigId = config.getId();

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
                                        .data(tokenInfo.toString(), MediaType.APPLICATION_JSON));
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

        com.example.aichat.util.NetworkUtils.validateExternalUrl(apiUrl);

        if (apiKey != null && apiKey.startsWith("AES:")) {
            logger.error("ModelConfig id={} 的 API Key 解密失败！", modelConfigId);
        }
        logger.debug("ChatStreamService 使用 ModelConfig: id={}, displayName={}, apiUrl={}, apiKeyConfigured={}, model={}",
                modelConfigId, config.getDisplayName(), apiUrl,
                apiKey != null,
                modelName);

        Runnable task = () -> {
            long promptTokens = 0;
            long completionTokens = 0;
            try {
                // --- Phase 1: 发送请求（带或不带 tools）---
                ObjectNode requestBody = objectMapper.createObjectNode();
                requestBody.put("model", modelName);
                requestBody.put("stream", true);
                requestBody.set("messages", messages);

                boolean hasTools = tools != null && !tools.isEmpty();
                if (hasTools) {
                    requestBody.set("tools", buildToolsArray(tools));
                }

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
                                        response.getEntity().getContent().readAllBytes(), StandardCharsets.UTF_8);
                            }
                        } catch (Exception e) { logger.warn("读取错误响应内容失败", e); }
                        safeSend(emitter, "error", errorMsg, org.springframework.http.MediaType.TEXT_PLAIN);
                        safeComplete(emitter);
                        return;
                    }

                    if (response.getEntity() == null) {
                        safeSend(emitter, null, "AI 服务无返回内容", org.springframework.http.MediaType.TEXT_PLAIN);
                        safeComplete(emitter);
                        return;
                    }

                    StringBuilder fullResponse = new StringBuilder();
                    StringBuilder chunkBuf = new StringBuilder();
                    AtomicInteger eventCountRef = new AtomicInteger(0);

                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(response.getEntity().getContent(), StandardCharsets.UTF_8))) {

                        if (hasTools) {
                            promptTokens = parseStreamWithTools(reader, emitter, messages,
                                    config, conversationId, userMessage, userId, longMemoryEnabled,
                                    round, fullResponse, chunkBuf, eventCountRef);
                        } else {
                            promptTokens = parseContentStream(reader, emitter, fullResponse,
                                    chunkBuf, eventCountRef);
                        }
                    }

                    String completeResponse = fullResponse.toString();
                    Long savedMessageId = null;
                    if (!completeResponse.isEmpty()) {
                        ChatMessage saved = chatHistoryService.saveMessage(conversationId, userId, userMessage, completeResponse);
                        savedMessageId = saved.getId();
                        updateConversationTitleIfNeeded(conversationId, userMessage);
                    }

                    if (promptTokens == 0 && completionTokens == 0 && !completeResponse.isEmpty()) {
                        promptTokens = Math.round(userMessage.length() * TOKEN_ESTIMATE_RATIO);
                        completionTokens = Math.round(completeResponse.length() * TOKEN_ESTIMATE_RATIO);
                        logger.warn("API未返回token用量，使用估算值: prompt={}, completion={}", promptTokens, completionTokens);
                    }

                    promptTokensRef.set(promptTokens);
                    completionTokensRef.set(completionTokens);
                    doBilling.run();

                    chatPostProcessor.triggerAsyncProcessing(userId, conversationId, userMessage, completeResponse, longMemoryEnabled, promptId);

                    String doneData = savedMessageId != null
                            ? "{\"done\":true,\"messageId\":" + savedMessageId + "}"
                            : "[DONE]";
                    safeSend(emitter, "done", doneData, org.springframework.http.MediaType.APPLICATION_JSON);
                    safeComplete(emitter);
                }
            } catch (Exception e) {
                // 客户端主动断开（中止生成）是正常行为，不记录完整堆栈
                if (e instanceof ClientDisconnectedException
                        || (e.getCause() instanceof ClientDisconnectedException)) {
                    logger.info("客户端已断开连接（用户中止生成）");
                } else if (e instanceof java.io.IOException
                        && e.getMessage() != null
                        && e.getMessage().contains("断开的管道")) {
                    logger.info("客户端已断开连接（用户中止生成）");
                } else if (e instanceof java.net.SocketTimeoutException
                        || e.getCause() instanceof java.net.SocketTimeoutException) {
                    logger.error("流式聊天超时", e);
                    safeSend(emitter, "error", "AI 响应超时，请重试", org.springframework.http.MediaType.TEXT_PLAIN);
                } else if (e instanceof java.io.IOException) {
                    logger.error("流式聊天连接失败", e);
                    safeSend(emitter, "error", "AI 服务连接失败，请稍后重试", org.springframework.http.MediaType.TEXT_PLAIN);
                } else {
                    logger.error("流式聊天异常", e);
                    safeSend(emitter, "error", "AI 回复失败，请稍后重试", org.springframework.http.MediaType.TEXT_PLAIN);
                }
                safeComplete(emitter);
            }
        };

        chatExecutorService.submit(task);
        return emitter;
    }

    // ==================== SSE 解析方法 ====================

    /**
     * 解析纯文本 SSE 流（无 tool_calls 场景）。
     */
    private long parseContentStream(BufferedReader reader, SseEmitter emitter,
                                     StringBuilder fullResponse, StringBuilder chunkBuf,
                                     AtomicInteger eventCount) throws Exception {
        String line;
        int flushEvery = 4;
        int sinceLastFlush = 0;
        long promptTokens = 0;

        while ((line = reader.readLine()) != null) {
            if (line.trim().isEmpty()) continue;

            String payload = line;
            if (payload.startsWith("data:")) {
                payload = payload.substring(5).trim();
            }
            if (payload.isEmpty()) continue;
            if ("[DONE]".equals(payload)) break;

            try {
                JsonNode root = objectMapper.readTree(payload);

                JsonNode usage = root.get("usage");
                if (usage != null && usage.has("prompt_tokens")) {
                    promptTokens = usage.get("prompt_tokens").asLong();
                }

                JsonNode choices = root.get("choices");
                if (choices != null && choices.isArray() && choices.size() > 0) {
                    JsonNode delta = choices.get(0).get("delta");
                    if (delta != null && delta.has("content") && !delta.get("content").isNull()) {
                        String content = delta.get("content").asText();
                        if (content == null) continue;
                        fullResponse.append(content);
                        chunkBuf.append(content);
                        sinceLastFlush += content.length();
                        if (sinceLastFlush >= flushEvery || containsSentenceEnd(content)) {
                            String toSend = chunkBuf.toString();
                            chunkBuf.setLength(0);
                            sinceLastFlush = 0;
                            sendContent(emitter, toSend, eventCount);
                        }
                    }
                }
            } catch (ClientDisconnectedException e) {
                throw e; // 中止读取，阻止后续保存
            } catch (Exception e) {
                logger.warn("解析流式响应失败", e);
            }
        }

        if (chunkBuf.length() > 0) {
            try {
                sendContent(emitter, chunkBuf.toString(), eventCount);
            } catch (Exception e) {
                logger.warn("发送剩余数据失败", e);
            }
        }

        return promptTokens;
    }

    /**
     * 解析带 tool_calls 检测的 SSE 流。
     * 检测到 tool_calls → 累积 → 执行工具 → 发送 Phase 2 请求（不带 tools）→ 解析纯文本。
     * 无 tool_calls → 直接走纯文本路径。
     *
     * @return promptTokens
     */
    private long parseStreamWithTools(BufferedReader reader, SseEmitter emitter,
                                       ArrayNode messages, ModelConfig config,
                                       Long conversationId, String userMessage, Long userId,
                                       Boolean longMemoryEnabled, int round,
                                       StringBuilder fullResponse, StringBuilder chunkBuf,
                                       AtomicInteger eventCount) throws Exception {
        String line;
        int flushEvery = 4;
        int sinceLastFlush = 0;
        long promptTokens = 0;

        boolean hasToolCalls = false;
        List<ToolCallAccumulator> toolCallAccumulators = new ArrayList<>();

        while ((line = reader.readLine()) != null) {
            if (line.trim().isEmpty()) continue;

            String payload = line;
            if (payload.startsWith("data:")) {
                payload = payload.substring(5).trim();
            }
            if (payload.isEmpty()) continue;
            if ("[DONE]".equals(payload)) break;

            try {
                JsonNode root = objectMapper.readTree(payload);

                JsonNode usage = root.get("usage");
                if (usage != null && usage.has("prompt_tokens")) {
                    promptTokens = usage.get("prompt_tokens").asLong();
                }

                JsonNode choices = root.get("choices");
                if (choices == null || !choices.isArray() || choices.size() == 0) continue;

                JsonNode delta = choices.get(0).get("delta");

                // --- 检测 tool_calls ---
                if (delta != null && delta.has("tool_calls")) {
                    hasToolCalls = true;
                    ToolCallAccumulator.accumulateDelta(delta.get("tool_calls"), toolCallAccumulators);
                    continue;
                }

                // --- 没有 tool_calls：正常推送 content ---
                if (!hasToolCalls && delta != null && delta.has("content")
                        && !delta.get("content").isNull()) {
                    String content = delta.get("content").asText();
                    if (content == null) continue;
                    fullResponse.append(content);
                    chunkBuf.append(content);
                    sinceLastFlush += content.length();
                    if (sinceLastFlush >= flushEvery || containsSentenceEnd(content)) {
                        String toSend = chunkBuf.toString();
                        chunkBuf.setLength(0);
                        sinceLastFlush = 0;
                        emitter.send(SseEmitter.event()
                                .id(String.valueOf(eventCount.getAndIncrement()))
                                .data(toSend, org.springframework.http.MediaType.TEXT_PLAIN));
                    }
                }

                // --- 检查 finish_reason ---
                String finishReason = null;
                if (choices.get(0).has("finish_reason") && !choices.get(0).get("finish_reason").isNull()) {
                    finishReason = choices.get(0).get("finish_reason").asText();
                }

                if ("tool_calls".equals(finishReason)) {
                    if (chunkBuf.length() > 0 && !hasToolCalls) {
                        sendContent(emitter, chunkBuf.toString(), eventCount);
                        chunkBuf.setLength(0);
                        sinceLastFlush = 0;
                    }

                    List<ToolCall> calls = ToolCallAccumulator.finalize(toolCallAccumulators);
                    if (calls.isEmpty()) {
                        logger.warn("detected tool_calls finish_reason but no valid calls");
                        break;
                    }

                    // 追加 assistant tool_calls 消息
                    appendAssistantToolCallMessage(messages, calls);

                    // 工具执行（多个时并发）
                    executeTools(calls, emitter, messages);

                    // 通知前端进入生成阶段
                    safeSend(emitter, "status", "{\"status\":\"generating\"}", MediaType.APPLICATION_JSON);

                    // --- Phase 2: 发送不带 tools 的请求，纯文本流式生成 ---
                    logger.info("工具调用完成，进入 Phase 2 文本生成 (round={})", round + 1);

                    if (round + 1 >= MAX_ROUNDS) {
                        logger.warn("达到最大轮次限制 MAX_ROUNDS={}，强制生成回复", MAX_ROUNDS);
                    }

                    // 发送 Phase 2 请求到同一 emitter
                    sendPhase2Request(messages, config, emitter, fullResponse, chunkBuf, eventCount);
                    return promptTokens;
                }

            } catch (ClientDisconnectedException e) {
                throw e; // 中止读取，阻止后续保存
            } catch (Exception e) {
                logger.warn("解析流式响应失败", e);
            }
        }

        // 无 tool_calls：flush 剩余
        if (chunkBuf.length() > 0) {
            try {
                sendContent(emitter, chunkBuf.toString(), eventCount);
            } catch (Exception e) {
                logger.warn("发送剩余数据失败", e);
            }
        }

        return promptTokens;
    }

    /**
     * Phase 2: 发送不带 tools 的流式请求，将 content 推送到同一 emitter。
     */

    /**
     * 执行工具调用。单个直接执行，多个并发执行（CompletableFuture.allOf）。
     */
    private void executeTools(List<ToolCall> calls, SseEmitter emitter, ArrayNode messages) {
        if (calls.size() == 1) {
            // 单工具：直接执行，无并发开销
            ToolCall call = calls.get(0);
            logger.info("执行工具: {} id={} args={}", call.getName(), call.getId(), call.getArguments());
            sendToolStatus(emitter, call.getName());
            ToolResult result = toolRegistry.execute(call);
            appendToolResultMessage(messages, result);
        } else {
            // 多工具：并发执行，总耗时受 TOOL_TIMEOUT_SECONDS 限制
            List<java.util.concurrent.CompletableFuture<ToolResult>> futures = new ArrayList<>();
            for (ToolCall call : calls) {
                logger.info("并发执行工具: {} id={} args={}", call.getName(), call.getId(), call.getArguments());
                sendToolStatus(emitter, call.getName());
                futures.add(java.util.concurrent.CompletableFuture.supplyAsync(
                        () -> toolRegistry.execute(call), chatExecutorService));
            }
            try {
                java.util.concurrent.CompletableFuture.allOf(
                        futures.toArray(new java.util.concurrent.CompletableFuture[0]))
                        .get(TOOL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                logger.warn("部分工具执行超时 ({}s)，将用已完成的结果继续", TOOL_TIMEOUT_SECONDS);
            } catch (Exception e) {
                logger.error("并发工具执行异常: {}", e.getMessage());
            }
            // 收集结果：完成的取结果，未完成的返回超时错误
            for (int i = 0; i < calls.size(); i++) {
                ToolCall call = calls.get(i);
                try {
                    ToolResult result = futures.get(i).isDone()
                            ? futures.get(i).get()
                            : new ToolResult(call.getId(), call.getName(),
                                    "工具 '" + call.getName() + "' 执行超时");
                    appendToolResultMessage(messages, result);
                } catch (Exception e) {
                    appendToolResultMessage(messages,
                            new ToolResult(call.getId(), call.getName(),
                                    "工具执行异常: " + e.getMessage()));
                }
            }
        }
    }

    private void sendToolStatus(SseEmitter emitter, String toolName) {
        safeSend(emitter, "status",
                "{\"tool\":\"" + toolName + "\",\"status\":\"running\"}",
                MediaType.APPLICATION_JSON);
    }
    private void sendPhase2Request(ArrayNode messages, ModelConfig config,
                                    SseEmitter emitter, StringBuilder fullResponse,
                                    StringBuilder chunkBuf, AtomicInteger eventCount) {
        String apiUrl = config.getApiUrl();
        String apiKey = config.getApiKey();
        String modelName = config.getModelName();

        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", modelName);
            requestBody.put("stream", true);
            requestBody.set("messages", messages);
            // 不带 tools

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
                                    response.getEntity().getContent().readAllBytes(), StandardCharsets.UTF_8);
                        }
                    } catch (Exception e) { logger.warn("读取错误响应内容失败", e); }
                    safeSend(emitter, "error", errorMsg, org.springframework.http.MediaType.TEXT_PLAIN);
                    return;
                }

                if (response.getEntity() == null) {
                    safeSend(emitter, null, "AI 服务无返回内容", org.springframework.http.MediaType.TEXT_PLAIN);
                    return;
                }

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(response.getEntity().getContent(), StandardCharsets.UTF_8))) {
                    parseContentStream(reader, emitter, fullResponse, chunkBuf, eventCount);
                }
            }
        } catch (ClientDisconnectedException e) {
            logger.debug("Phase 2 客户端已断开");
        } catch (Exception e) {
            logger.error("Phase 2 请求失败", e);
            safeSend(emitter, "error",
                    "工具调用后生成回复失败，请稍后重试",
                    org.springframework.http.MediaType.TEXT_PLAIN);
        }
    }

    // ==================== 消息辅助方法 ====================

    private void appendAssistantToolCallMessage(ArrayNode messages, List<ToolCall> calls) {
        ObjectNode assistantMsg = objectMapper.createObjectNode();
        assistantMsg.put("role", "assistant");
        assistantMsg.putNull("content");

        ArrayNode toolCallsArray = objectMapper.createArrayNode();
        for (ToolCall call : calls) {
            ObjectNode tc = objectMapper.createObjectNode();
            tc.put("id", call.getId());
            tc.put("type", "function");
            ObjectNode func = objectMapper.createObjectNode();
            func.put("name", call.getName());
            func.put("arguments", call.getArguments());
            tc.set("function", func);
            toolCallsArray.add(tc);
        }
        assistantMsg.set("tool_calls", toolCallsArray);
        messages.add(assistantMsg);
    }

    private void appendToolResultMessage(ArrayNode messages, ToolResult result) {
        ObjectNode toolMsg = objectMapper.createObjectNode();
        toolMsg.put("role", "tool");
        toolMsg.put("tool_call_id", result.getToolCallId());
        toolMsg.put("content", result.getContent());
        messages.add(toolMsg);
    }

    private ArrayNode buildToolsArray(List<ToolDefinition> tools) {
        ArrayNode toolsArray = objectMapper.createArrayNode();
        for (ToolDefinition tool : tools) {
            toolsArray.add(tool.toJsonNode(objectMapper));
        }
        return toolsArray;
    }

    // ==================== 其他辅助方法 ====================

    /**
     * 安全发送 SSE 事件，emitter 已关闭时静默忽略。
     */
    private void safeSend(SseEmitter emitter, String eventName, String data,
                          org.springframework.http.MediaType mediaType) {
        try {
            SseEmitter.SseEventBuilder builder = SseEmitter.event();
            if (eventName != null) {
                builder.name(eventName);
            }
            emitter.send(builder.data(data, mediaType));
        } catch (IllegalStateException e) {
            logger.debug("emitter 已关闭，跳过发送: event={}", eventName);
        } catch (Exception e) {
            logger.warn("发送 SSE 事件失败: event={}", eventName, e);
        }
    }

    /**
     * 安全完成 emitter，已关闭时静默忽略。
     */
    private void safeComplete(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (IllegalStateException e) {
            logger.debug("emitter 已关闭，跳过 complete");
        }
    }

    /**
     * 发送 SSE content 事件，包装为前端期望的 JSON 格式：{"content":"..."}
     * 客户端断开时静默忽略，不产生警告日志。
     */
    private void sendContent(SseEmitter emitter, String text, AtomicInteger eventCount) {
        try {
            String json = "{\"content\":" + objectMapper.writeValueAsString(text) + "}";
            emitter.send(SseEmitter.event()
                    .id(String.valueOf(eventCount.getAndIncrement()))
                    .data(json, org.springframework.http.MediaType.APPLICATION_JSON));
        } catch (IllegalStateException e) {
            logger.debug("SSE content 发送失败（客户端已断开）");
            throw new ClientDisconnectedException();
        } catch (Exception e) {
            logger.warn("发送 SSE content 失败", e);
        }
    }

    private void updateConversationTitleIfNeeded(Long conversationId, String userMessage) {
        Conversation conversation = conversationRepository.findById(conversationId).orElse(null);
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
