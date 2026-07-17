package com.example.aichat.service;

import com.example.aichat.config.BusinessException;
import com.example.aichat.model.ModelConfig;
import com.example.aichat.model.TokenUsage;
import com.example.aichat.repository.ConversationRepository;
import com.example.aichat.repository.ModelConfigRepository;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 聊天服务门面（Facade）。
 * 
 * 拆分后的 ChatService 仅负责编排，具体职责委托给：
 *   - {@link MessageContextBuilder}  消息上下文构建
 *   - {@link ChatStreamService}      SSE 流式响应
 *   - {@link ChatPostProcessor}      后处理编排（记忆/摘要）
 *   - {@link LLMService}             非流式 LLM 调用
 *   - {@link BillingService}         计费扣减
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

    public ChatService(ConversationRepository conversationRepository,
                       ModelConfigRepository modelConfigRepository,
                       MessageContextBuilder messageContextBuilder,
                       ChatStreamService chatStreamService,
                       ChatPostProcessor chatPostProcessor,
                       ChatHistoryService chatHistoryService,
                       BillingService billingService,
                       LLMService llmService) {
        this.conversationRepository = conversationRepository;
        this.modelConfigRepository = modelConfigRepository;
        this.messageContextBuilder = messageContextBuilder;
        this.chatStreamService = chatStreamService;
        this.chatPostProcessor = chatPostProcessor;
        this.chatHistoryService = chatHistoryService;
        this.billingService = billingService;
        this.llmService = llmService;
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
                              Boolean longMemoryEnabled) {
        ModelConfig config = validateAndGetConfig(conversationId, modelConfigId);
        ArrayNode messagesArray = messageContextBuilder.buildMessagesArray(
                conversationId, promptId, userMessage, webSearchEnabled,
                imageDescription, knowledgeBaseId, userId, longMemoryEnabled);

        // 预扣余额，防止扣费失败时用户免费使用
        if (userId != null) {
            long estimatedTokens = messagesArray.toString().length();
            billingService.checkAndReserveBalance(userId, modelConfigId, estimatedTokens);
        }

        try {
            LLMService.TokenUsageResult result = llmService.callAsyncWithUsage(messagesArray, config).join();

            // 先实际扣费，成功后再保存消息
            if (userId != null) {
                TokenUsage usage = billingService.deductTokens(userId, modelConfigId,
                        result.getInputTokens(), result.getOutputTokens(), conversationId);
                if (usage != null) {
                    result.setCostAmount(usage.getCostAmount());
                }
            }

            chatHistoryService.saveMessage(conversationId, userMessage, result.getReply());
            updateConversationTitleIfNeeded(conversationId, userMessage);

            // 异步后处理
            chatPostProcessor.triggerAsyncProcessing(userId, conversationId, userMessage, result.getReply(), longMemoryEnabled);

            return result;
        } catch (Exception e) {
            // 扣费失败时释放预留余额
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
                                  Boolean longMemoryEnabled) {
        ModelConfig config = validateAndGetConfig(conversationId, modelConfigId);
        ArrayNode messagesArray = messageContextBuilder.buildMessagesArray(
                conversationId, promptId, userMessage, webSearchEnabled,
                imageDescription, knowledgeBaseId, userId, longMemoryEnabled);

        return chatStreamService.streamDeepSeek(messagesArray, config, conversationId, userMessage, userId, longMemoryEnabled);
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
