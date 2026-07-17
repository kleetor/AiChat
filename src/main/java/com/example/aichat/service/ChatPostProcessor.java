package com.example.aichat.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 聊天后处理器 —— 从 ChatService 拆分，负责异步编排记忆提取、摘要生成。
 * 计费扣减由各调用方（ChatService / ChatStreamService）自行处理，不在此处。
 */
@Component
public class ChatPostProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ChatPostProcessor.class);

    private final MemoryService memoryService;
    private final SummaryService summaryService;

    public ChatPostProcessor(MemoryService memoryService,
                              SummaryService summaryService) {
        this.memoryService = memoryService;
        this.summaryService = summaryService;
    }

    /**
     * 触发异步后处理：记忆提取 + 摘要生成。
     * 非阻塞调用，不等待结果。
     */
    public void triggerAsyncProcessing(Long userId, Long conversationId,
                                        String userMessage, String aiReply,
                                        Boolean longMemoryEnabled) {
        if (userId == null || !Boolean.TRUE.equals(longMemoryEnabled)) {
            return;
        }
        if (aiReply == null || aiReply.isEmpty()) {
            return;
        }
        try {
            memoryService.extractAndStore(userId, conversationId, userMessage, aiReply);
            summaryService.checkAndGenerate(conversationId);
        } catch (Exception e) {
            logger.warn("后处理触发失败: userId={}, conversationId={}", userId, conversationId, e);
        }
    }
}
