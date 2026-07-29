package com.example.aichat.service;

import com.example.aichat.config.BusinessException;
import com.example.aichat.model.ModelConfig;
import com.example.aichat.model.TokenUsage;
import com.example.aichat.repository.ConversationRepository;
import com.example.aichat.repository.ModelConfigRepository;
import com.example.aichat.service.tool.ToolDefinition;
import com.example.aichat.service.tool.ToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 聊天服务门面（Facade）。
 * 
 * 拆分后的 ChatService 仅负责编排，具体职责委托给：
 *   - {@link MessageContextBuilder}  消息上下文构建
 *   - {@link ChatStreamService}      SSE 流式响应
 *   - {@link ChatPostProcessor}      后处理编排（记忆/摘要）
 *   - {@link LLMService}             非流式 LLM 调用
 *   - {@link BillingService}         计费扣减
 *   - {@link ToolRegistry}           工具注册与激活
 */
@Service
public class ChatService {

    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);

    private final ConversationRepository conversationRepository;
    private final ModelConfigRepository modelConfigRepository;
    private final MessageContextBuilder messageContextBuilder;
    private final ChatStreamService chatStreamService;
    private final ChatPostProcessor chatPostProcessor;
    private final ChatHistoryService chatHistoryService;
    private final BillingService billingService;
    private final LLMService llmService;
    private final ToolRegistry toolRegistry;
    private final TavilySearchService tavilySearchService;
    private final SearchService searchService;
    private final ObjectMapper objectMapper;

    public ChatService(ConversationRepository conversationRepository,
                       ModelConfigRepository modelConfigRepository,
                       MessageContextBuilder messageContextBuilder,
                       ChatStreamService chatStreamService,
                       ChatPostProcessor chatPostProcessor,
                       ChatHistoryService chatHistoryService,
                       BillingService billingService,
                       LLMService llmService,
                       ToolRegistry toolRegistry,
                       TavilySearchService tavilySearchService,
                       SearchService searchService,
                       ObjectMapper objectMapper) {
        this.conversationRepository = conversationRepository;
        this.modelConfigRepository = modelConfigRepository;
        this.messageContextBuilder = messageContextBuilder;
        this.chatStreamService = chatStreamService;
        this.chatPostProcessor = chatPostProcessor;
        this.chatHistoryService = chatHistoryService;
        this.billingService = billingService;
        this.llmService = llmService;
        this.toolRegistry = toolRegistry;
        this.tavilySearchService = tavilySearchService;
        this.searchService = searchService;
        this.objectMapper = objectMapper;
    }

    /**
     * 验证会话和模型配置，返回模型配置对象
     */
    private ModelConfig validateAndGetConfig(Long conversationId, Long modelConfigId) {
        conversationRepository.findById(conversationId)
                .orElseThrow(() -> BusinessException.notFound("会话不存在"));

        if (modelConfigId == null) {
            throw BusinessException.badRequest("请先选择模型配置");
        }

        return modelConfigRepository.findById(modelConfigId)
                .orElseThrow(() -> BusinessException.notFound("模型配置不存在"));
    }

    /**
     * 非流式聊天入口。
     */
    public LLMService.TokenUsageResult chatAndSave(Long conversationId, String userMessage, Long promptId,
                              Long modelConfigId, boolean webSearchEnabled, Long userId,
                              String imageDescription, Long knowledgeBaseId,
                              Boolean longMemoryEnabled, String imageUrl, String fileUrl) {
        ModelConfig config = validateAndGetConfig(conversationId, modelConfigId);

        boolean useToolCalling = shouldUseToolCalling(config, webSearchEnabled, imageUrl, fileUrl);

        // 构建消息数组（走工具调用时不注入搜索结果，降级路径在下面处理）
        ArrayNode messagesArray = messageContextBuilder.buildMessagesArray(
                conversationId, promptId, userMessage, webSearchEnabled,
                imageDescription, knowledgeBaseId, userId, longMemoryEnabled,
                useToolCalling ? imageUrl : null,
                useToolCalling ? fileUrl : null);

        // 降级路径：不支持工具调用的模型，webSearchEnabled 时预注入搜索结果
        if (webSearchEnabled && !useToolCalling) {
            injectSearchResults(messagesArray, userMessage);
        }

        // 预扣余额
        if (userId != null) {
            long estimatedTokens = messagesArray.toString().length();
            billingService.checkAndReserveBalance(userId, modelConfigId, estimatedTokens);
        }

        try {
            // 非流式路径暂不支持工具调用（仅流式路径支持）
            LLMService.TokenUsageResult result = llmService.callAsyncWithUsage(messagesArray, config).join();

            if (userId != null) {
                TokenUsage usage = billingService.deductTokens(userId, modelConfigId,
                        result.getInputTokens(), result.getOutputTokens(), conversationId);
                if (usage != null) {
                    result.setCostAmount(usage.getCostAmount());
                }
            }

            chatHistoryService.saveMessage(conversationId, userId, userMessage, result.getReply(), fileUrl);
            updateConversationTitleIfNeeded(conversationId, userMessage);

            chatPostProcessor.triggerAsyncProcessing(userId, conversationId, userMessage, result.getReply(), longMemoryEnabled, promptId);

            return result;
        } catch (Exception e) {
            if (userId != null) {
                try {
                    billingService.releaseReservedBalance(userId);
                } catch (Exception releaseEx) {
                    logger.error("释放预留余额失败: userId={}", userId, releaseEx);
                }
            }
            throw e;
        }
    }

    /**
     * 流式聊天入口（SSE）。
     */
    public SseEmitter chatStream(Long conversationId, String userMessage, Long promptId,
                                  Long modelConfigId, boolean webSearchEnabled, Long userId,
                                  String imageDescription, Long knowledgeBaseId,
                                  Boolean longMemoryEnabled, String imageUrl, String fileUrl) {
        ModelConfig config = validateAndGetConfig(conversationId, modelConfigId);

        boolean useToolCalling = shouldUseToolCalling(config, webSearchEnabled, imageUrl, fileUrl);

        ArrayNode messagesArray = messageContextBuilder.buildMessagesArray(
                conversationId, promptId, userMessage, webSearchEnabled,
                imageDescription, knowledgeBaseId, userId, longMemoryEnabled,
                useToolCalling ? imageUrl : null,
                useToolCalling ? fileUrl : null);

        if (webSearchEnabled && !useToolCalling) {
            injectSearchResults(messagesArray, userMessage);
        }

        if (useToolCalling) {
            boolean hasFile = fileUrl != null && !fileUrl.isBlank();
            boolean hasImage = imageUrl != null && !imageUrl.isBlank();
            List<ToolDefinition> tools = toolRegistry.getActiveTools(webSearchEnabled, hasImage || hasFile);
            logger.info("使用工具调用路径: tools={}, imageUrl={}", 
                    tools.stream().map(ToolDefinition::getName).toList(), imageUrl);
            return chatStreamService.streamWithToolLoop(messagesArray, config, conversationId,
                    userMessage, userId, longMemoryEnabled, promptId, tools, 0, fileUrl);
        } else {
            return chatStreamService.streamDeepSeek(messagesArray, config, conversationId,
                    userMessage, userId, longMemoryEnabled, promptId, fileUrl);
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 判断是否应该使用工具调用路径。
     */
    private boolean shouldUseToolCalling(ModelConfig config, boolean webSearchEnabled,
                                          String imageUrl, String fileUrl) {
        boolean supportsToolCalling = Boolean.TRUE.equals(config.getSupportsToolCalling());
        boolean hasImageUrl = imageUrl != null && !imageUrl.isBlank();
        boolean hasFileUrl = fileUrl != null && !fileUrl.isBlank();
        return supportsToolCalling && toolRegistry.hasActiveTools(webSearchEnabled, hasImageUrl || hasFileUrl);
    }

    /**
     * 降级路径：在 messages 数组末尾（用户消息前）注入搜索结果 system 消息。
     */
    private void injectSearchResults(ArrayNode messagesArray, String userMessage) {
        // 双引擎并发竞速，取先返回的结果
        java.util.concurrent.CompletableFuture<String> tavilyFuture =
                java.util.concurrent.CompletableFuture.supplyAsync(
                        () -> tavilySearchService.searchAsMarkdown(userMessage, 5));
        java.util.concurrent.CompletableFuture<String> qianfanFuture =
                java.util.concurrent.CompletableFuture.supplyAsync(
                        () -> searchService.searchAsMarkdown(userMessage, 5));

        String searchResults = null;
        String source = null;
        try {
            searchResults = (String) java.util.concurrent.CompletableFuture
                    .anyOf(tavilyFuture, qianfanFuture)
                    .get(30, java.util.concurrent.TimeUnit.SECONDS);
            source = tavilyFuture.isDone() ? "Tavily" : "千帆";
        } catch (Exception e) {
            logger.warn("降级路径双引擎竞速失败，检查已完成方: {}", e.getMessage());
            if (tavilyFuture.isDone() && !tavilyFuture.isCompletedExceptionally()) {
                try { searchResults = tavilyFuture.get(); source = "Tavily"; } catch (Exception ignored) { }
            }
            if (searchResults == null && qianfanFuture.isDone() && !qianfanFuture.isCompletedExceptionally()) {
                try { searchResults = qianfanFuture.get(); source = "千帆"; } catch (Exception ignored) { }
            }
        }

        if (searchResults != null) {
            ObjectNode searchNode = objectMapper.createObjectNode();
            searchNode.put("role", "system");
            searchNode.put("content", "最新搜索信息（" + source + "）：\n" + searchResults);
            int insertPos = Math.max(0, messagesArray.size() - 1);
            messagesArray.insert(insertPos, searchNode);
            logger.info("降级路径: {}搜索成功，query={}", source, userMessage);
        } else {
            logger.warn("降级路径搜索全部失败: query={}", userMessage);
        }
    }

    private void updateConversationTitleIfNeeded(Long conversationId, String userMessage) {
        var conversation = conversationRepository.findById(conversationId).orElse(null);
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
}
