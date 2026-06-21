package com.example.aichat.service;

import com.example.aichat.model.ChatMessage;
import com.example.aichat.model.Conversation;
import com.example.aichat.model.ConversationSummary;
import com.example.aichat.model.MemoryItem;
import com.example.aichat.model.ModelConfig;
import com.example.aichat.model.Prompt;
import com.example.aichat.model.TokenUsage;
import com.example.aichat.repository.ChatMessageRepository;
import com.example.aichat.repository.ConversationRepository;
import com.example.aichat.repository.ConversationSummaryRepository;
import com.example.aichat.repository.MemoryItemRepository;
import com.example.aichat.repository.ModelConfigRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.io.entity.StringEntity;

@Service
public class ChatService {

    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);
    private static final int MAX_HISTORY_SIZE = 30;
    private static final double TOKEN_ESTIMATE_RATIO = 1.3;

    private final RestTemplate restTemplate;
    private final ChatHistoryService chatHistoryService;
    private final ChatMessageRepository chatMessageRepository;
    private final ConversationRepository conversationRepository;
    private final PromptService promptService;
    private final ModelConfigRepository modelConfigRepository;
    private final SearchService searchService;
    private final TavilySearchService tavilySearchService;
    private final CloseableHttpClient httpClient;
    private final ExecutorService chatExecutorService;
    private final ObjectMapper objectMapper;
    private final BillingService billingService;
    private final ChromaDBService chromaDBService;
    private final MemoryService memoryService;
    private final SummaryService summaryService;
    private final ConversationSummaryRepository summaryRepo;
    private final MemoryItemRepository memoryRepo;
    private final LLMService llmService;

    public ChatService(RestTemplate restTemplate,
                      ChatHistoryService chatHistoryService,
                      ChatMessageRepository chatMessageRepository,
                      ConversationRepository conversationRepository,
                      PromptService promptService,
                      ModelConfigRepository modelConfigRepository,
                      SearchService searchService,
                      TavilySearchService tavilySearchService,
                      CloseableHttpClient httpClient,
                      ExecutorService chatExecutorService,
                      BillingService billingService,
                      ChromaDBService chromaDBService,
                      MemoryService memoryService,
                      SummaryService summaryService,
                      ConversationSummaryRepository summaryRepo,
                      MemoryItemRepository memoryRepo,
                      LLMService llmService) {
        this.restTemplate = restTemplate;
        this.chatHistoryService = chatHistoryService;
        this.chatMessageRepository = chatMessageRepository;
        this.conversationRepository = conversationRepository;
        this.promptService = promptService;
        this.modelConfigRepository = modelConfigRepository;
        this.searchService = searchService;
        this.tavilySearchService = tavilySearchService;
        this.httpClient = httpClient;
        this.chatExecutorService = chatExecutorService;
        this.objectMapper = new ObjectMapper();
        this.billingService = billingService;
        this.chromaDBService = chromaDBService;
        this.memoryService = memoryService;
        this.summaryService = summaryService;
        this.summaryRepo = summaryRepo;
        this.memoryRepo = memoryRepo;
        this.llmService = llmService;
    }

    /**
     * 验证会话和模型配置，返回模型配置对象
     */
    private ModelConfig validateAndGetConfig(Long conversationId, Long modelConfigId) {
        conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("会话不存在"));

        if (modelConfigId == null) {
            throw new RuntimeException("请先选择模型配置");
        }

        return modelConfigRepository.findById(modelConfigId)
                .orElseThrow(() -> new RuntimeException("模型配置不存在"));
    }

    /**
     * 获取最近的历史消息（最多30条）
     */
    private List<ChatMessage> getRecentHistory(Long conversationId) {
        List<ChatMessage> history = chatMessageRepository
                .findByConversationIdOrderByTimestampAsc(conversationId);
        if (history.size() > MAX_HISTORY_SIZE) {
            history = history.subList(history.size() - MAX_HISTORY_SIZE, history.size());
        }
        return history;
    }

    /**
     * 构建消息数组（含 prompt、长期记忆、知识库、摘要、历史、搜索、图片、当前消息）
     */
    private ArrayNode buildMessagesArray(Long conversationId, Long promptId, 
                                          String userMessage, boolean webSearchEnabled,
                                          String imageDescription, Long knowledgeBaseId,
                                          Long userId, Boolean longMemoryEnabled) {
        ArrayNode messagesArray = objectMapper.createArrayNode();
        List<ChatMessage> history = getRecentHistory(conversationId);

        // 1. 注入 Prompt (用户个人提示词)
        if (promptId != null) {
            try {
                Prompt prompt = promptService.getPromptById(promptId);
                ObjectNode systemNode = messagesArray.addObject();
                systemNode.put("role", "system");
                systemNode.put("content", prompt.getContent());
            } catch (Exception e) {
                logger.warn("提示词加载失败: {}", e.getMessage());
            }
        }

        // 2. 注入长期记忆 — 最近N条清晰期/模糊期记忆 (时间倒序)
        if (userId != null && Boolean.TRUE.equals(longMemoryEnabled)) {
            try {
                List<MemoryItem> memories = memoryService.getRecentMemoriesForContext(userId);
                if (!memories.isEmpty()) {
                    StringBuilder sb = new StringBuilder("【关于用户的已知信息（最近）】\n");
                    for (var m : memories) {
                        sb.append("- ").append(m.getValue()).append("\n");
                    }
                    ObjectNode memNode = messagesArray.addObject();
                    memNode.put("role", "system");
                    memNode.put("content", sb.toString());

                    // 注入即访问: 批量刷新 lastAccessedAt
                    List<Long> touchedIds = memories.stream().map(MemoryItem::getId).collect(Collectors.toList());
                    memoryService.touchMemories(touchedIds);
                }
            } catch (Exception e) {
                logger.warn("长期记忆注入失败: {}", e.getMessage());
            }
        }

        // 3. 注入对话摘要
        if (Boolean.TRUE.equals(longMemoryEnabled)) {
            try {
                ConversationSummary summary = summaryRepo.findByConversationId(conversationId);
                if (summary != null && summary.getSummary() != null) {
                    ObjectNode sumNode = messagesArray.addObject();
                    sumNode.put("role", "system");
                    sumNode.put("content", "【历史对话摘要】\n" + summary.getSummary());
                }
            } catch (Exception e) {
                logger.warn("对话摘要注入失败: {}", e.getMessage());
            }
        }

        // 4. 注入知识库检索
        if (knowledgeBaseId != null) {
            try {
                ChromaDBService.QueryResult qr = chromaDBService.query(
                        knowledgeBaseId, userMessage, 5);
                if (qr != null && !qr.isEmpty()) {
                    StringBuilder ctx = new StringBuilder(
                            "以下是与用户问题相关的知识库内容，请基于这些内容回答：\n\n");
                    for (var item : qr.items()) {
                        String fileName = item.metadata() != null
                                ? String.valueOf(item.metadata().getOrDefault("file_name", "未知"))
                                : "未知";
                        ctx.append("【来源: ").append(fileName).append("】\n")
                           .append(item.document()).append("\n\n");
                    }
                    ctx.append("回答时请注明引用来源（文件名）。");
                    ObjectNode kbNode = messagesArray.addObject();
                    kbNode.put("role", "system");
                    kbNode.put("content", ctx.toString());
                    logger.info("知识库检索注入成功: kbId={}, results={}", knowledgeBaseId, qr.size());
                }
            } catch (Exception e) {
                logger.warn("知识库检索失败: kbId={}", knowledgeBaseId, e);
            }
        }

        // 添加历史消息
        for (ChatMessage msg : history) {
            ObjectNode userNode = messagesArray.addObject();
            userNode.put("role", "user");
            userNode.put("content", msg.getUserMessage());

            ObjectNode assistantNode = messagesArray.addObject();
            assistantNode.put("role", "assistant");
            assistantNode.put("content", msg.getAiReply());
        }

        // 添加联网搜索结果（优先使用 Tavily，失败后回退到百度千帆）
        if (webSearchEnabled) {
            try {
                String searchResults = tavilySearchService.searchAsMarkdown(userMessage, 5);
                ObjectNode searchContextNode = messagesArray.addObject();
                searchContextNode.put("role", "system");
                searchContextNode.put("content", "最新搜索信息（Tavily）：\n" + searchResults);
                logger.info("联网搜索(Tavily)成功，查询: {}", userMessage);
            } catch (Exception e) {
                logger.warn("Tavily 搜索失败，回退到百度千帆: {}", e.getMessage());
                try {
                    String searchResults = searchService.searchAsMarkdown(userMessage, 5);
                    ObjectNode searchContextNode = messagesArray.addObject();
                    searchContextNode.put("role", "system");
                    searchContextNode.put("content", "最新搜索信息：\n" + searchResults);
                } catch (Exception e2) {
                    logger.warn("百度千帆搜索也失败: {}", e2.getMessage());
                }
            }
        }

        // 添加图片识别描述（作为系统消息插入，让主模型理解这是图片内容）
        if (imageDescription != null && !imageDescription.isBlank()) {
            ObjectNode imageContextNode = messagesArray.addObject();
            imageContextNode.put("role", "system");
            imageContextNode.put("content", imageDescription);
        }

        // 添加当前用户消息
        ObjectNode currentUserNode = messagesArray.addObject();
        currentUserNode.put("role", "user");
        currentUserNode.put("content", userMessage);

        return messagesArray;
    }

    public LLMService.TokenUsageResult chatAndSave(Long conversationId, String userMessage, Long promptId, 
                              Long modelConfigId, boolean webSearchEnabled, Long userId,
                              String imageDescription, Long knowledgeBaseId,
                              Boolean longMemoryEnabled) {
        ModelConfig config = validateAndGetConfig(conversationId, modelConfigId);
        ArrayNode messagesArray = buildMessagesArray(conversationId, promptId, 
                                                       userMessage, webSearchEnabled,
                                                       imageDescription, knowledgeBaseId,
                                                       userId, longMemoryEnabled);

        LLMService.TokenUsageResult result = llmService.callAsyncWithUsage(messagesArray, config).join();
        chatHistoryService.saveMessage(conversationId, userMessage, result.getReply());
        updateConversationTitleIfNeeded(conversationId, userMessage);

        // 异步: 记忆提取 + 摘要生成
        if (userId != null && Boolean.TRUE.equals(longMemoryEnabled)) {
            memoryService.extractAndStore(userId, conversationId, userMessage, result.getReply());
            summaryService.checkAndGenerate(conversationId);
        }

        if (userId != null) {
            try {
                TokenUsage usage = billingService.deductTokens(userId, modelConfigId, 
                        result.getInputTokens(), result.getOutputTokens(), conversationId);
                if (usage != null) {
                    result.setCostAmount(usage.getCostAmount());
                }
            } catch (Exception e) {
                logger.error("扣费失败: {}", e.getMessage());
            }
        }

        return result;
    }

    /**
     * 流式聊天：使用 Spring 官方 SseEmitter，自动处理缓冲、心跳、客户端断开等
     */
    public SseEmitter chatStream(Long conversationId, String userMessage, Long promptId, 
                                  Long modelConfigId, boolean webSearchEnabled, Long userId,
                                  String imageDescription, Long knowledgeBaseId,
                                  Boolean longMemoryEnabled) {
        ModelConfig config = validateAndGetConfig(conversationId, modelConfigId);
        ArrayNode messagesArray = buildMessagesArray(conversationId, promptId, 
                                                       userMessage, webSearchEnabled,
                                                       imageDescription, knowledgeBaseId,
                                                       userId, longMemoryEnabled);

        return streamDeepSeek(messagesArray, config, conversationId, userMessage, userId, longMemoryEnabled);
    }

    private SseEmitter streamDeepSeek(ArrayNode messages, ModelConfig config, 
                                       Long conversationId, String userMessage, Long userId,
                                       Boolean longMemoryEnabled) {
        SseEmitter emitter = new SseEmitter(120_000L);
        String apiUrl = config.getApiUrl();
        String apiKey = config.getApiKey();
        String modelName = config.getModelName();
        Long modelConfigId = config.getId();

        // 诊断：检测 API Key 是否解密失败（仍以 AES: 开头）
        if (apiKey != null && apiKey.startsWith("AES:")) {
            logger.error("ModelConfig id={} 的 API Key 解密失败！当前加密密钥与创建配置时的密钥不匹配。"
                    + "请检查 ENCRYPTION_KEY 环境变量是否与当初一致，或在后台重新保存模型配置以使用当前密钥重新加密。",
                    modelConfigId);
        }
        logger.debug("ChatService 使用 ModelConfig: id={}, displayName={}, apiUrl={}, apiKey前6位={}, model={}",
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
                                .data(errorMsg, MediaType.TEXT_PLAIN));
                        emitter.complete();
                        return;
                    }

                    if (response.getEntity() == null) {
                        emitter.send("AI 服务无返回内容");
                        emitter.complete();
                        return;
                    }

                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(response.getEntity().getContent(), StandardCharsets.UTF_8));

                    StringBuilder fullResponse = new StringBuilder();
                    StringBuilder chunkBuf = new StringBuilder();
                    String line;
                    int eventCount = 0;
                    final int flushEvery = 4;
                    final long sleepMs = 50;
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
                                                .data(toSend, MediaType.TEXT_PLAIN));
                                        try {
                                            Thread.sleep(sleepMs);
                                        } catch (InterruptedException ie) {
                                            Thread.currentThread().interrupt();
                                            break;
                                        }
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
                                    .data(chunkBuf.toString(), MediaType.TEXT_PLAIN));
                        } catch (Exception e) {
                            logger.warn("发送剩余数据失败", e);
                        }
                    }
                    reader.close();

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

                    if (userId != null && (promptTokens > 0 || completionTokens > 0)) {
                        TokenUsage usage = null;
                        try {
                            usage = billingService.deductTokens(userId, modelConfigId, promptTokens, completionTokens, conversationId);
                        } catch (Exception e) {
                            logger.error("流式聊天扣费失败: {}", e.getMessage());
                        }

                        if (usage != null) {
                            ObjectNode tokenInfo = objectMapper.createObjectNode();
                            tokenInfo.put("inputTokens", usage.getInputTokens());
                            tokenInfo.put("outputTokens", usage.getOutputTokens());
                            tokenInfo.put("costAmount", usage.getCostAmount());
                            emitter.send(SseEmitter.event()
                                    .name("token_usage")
                                    .data(tokenInfo.toString(), MediaType.APPLICATION_JSON));
                        }
                    }

                    // 异步: 记忆提取 + 摘要生成 (不阻塞 SSE done 信号)
                    if (userId != null && Boolean.TRUE.equals(longMemoryEnabled) && !completeResponse.isEmpty()) {
                        memoryService.extractAndStore(userId, conversationId, userMessage, completeResponse);
                        summaryService.checkAndGenerate(conversationId);
                    }

                    String doneData = savedMessageId != null
                            ? "{\"done\":true,\"messageId\":" + savedMessageId + "}"
                            : "[DONE]";
                    emitter.send(SseEmitter.event()
                            .name("done")
                            .data(doneData, MediaType.APPLICATION_JSON));
                    emitter.complete();
                }
            } catch (Exception e) {
                logger.error("流式聊天异常", e);
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data("AI回复失败：" + e.getMessage(), MediaType.TEXT_PLAIN));
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