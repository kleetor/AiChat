package com.example.aichat.service;

import com.example.aichat.model.ChatMessage;
import com.example.aichat.model.ConversationSummary;
import com.example.aichat.model.MemoryItem;
import com.example.aichat.model.Prompt;
import com.example.aichat.model.SystemRule;
import com.example.aichat.repository.ChatMessageRepository;
import com.example.aichat.repository.ConversationSummaryRepository;
import com.example.aichat.repository.SystemRuleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 消息上下文构建器 —— 从 ChatService 拆分，负责构建 LLM API 的 messages 数组。
 * 按顺序拼接：系统规则 → 自定义提示词 → 长期记忆 → 对话摘要 → 知识库检索 → 历史对话 → 联网搜索 → 图片描述 → 当前消息
 */
@Component
public class MessageContextBuilder {

    private static final Logger logger = LoggerFactory.getLogger(MessageContextBuilder.class);
    private static final int MAX_HISTORY_SIZE = 30;

    private final ChatMessageRepository chatMessageRepository;
    private final PromptService promptService;
    private final MemoryService memoryService;
    private final ConversationSummaryRepository summaryRepo;
    private final ChromaDBService chromaDBService;
    private final TavilySearchService tavilySearchService;
    private final SearchService searchService;
    private final SystemRuleRepository systemRuleRepository;
    private final ObjectMapper objectMapper;

    public MessageContextBuilder(ChatMessageRepository chatMessageRepository,
                                  PromptService promptService,
                                  MemoryService memoryService,
                                  ConversationSummaryRepository summaryRepo,
                                  ChromaDBService chromaDBService,
                                  TavilySearchService tavilySearchService,
                                  SearchService searchService,
                                  SystemRuleRepository systemRuleRepository) {
        this.chatMessageRepository = chatMessageRepository;
        this.promptService = promptService;
        this.memoryService = memoryService;
        this.summaryRepo = summaryRepo;
        this.chromaDBService = chromaDBService;
        this.tavilySearchService = tavilySearchService;
        this.searchService = searchService;
        this.systemRuleRepository = systemRuleRepository;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 构建消息数组（含 prompt、长期记忆、知识库、摘要、历史、搜索、图片、当前消息）
     */
    public ArrayNode buildMessagesArray(Long conversationId, Long promptId,
                                         String userMessage, boolean webSearchEnabled,
                                         String imageDescription, Long knowledgeBaseId,
                                         Long userId, Boolean longMemoryEnabled) {
        ArrayNode messagesArray = objectMapper.createArrayNode();
        List<ChatMessage> history = getRecentHistory(conversationId);

        // 0. 注入系统规则 (全局规则，sort_order 升序)
        try {
            List<SystemRule> rules = systemRuleRepository.findByIsActiveTrueOrderBySortOrderAsc();
            if (!rules.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (SystemRule rule : rules) {
                    sb.append(rule.getContent()).append("\n\n");
                }
                ObjectNode ruleNode = messagesArray.addObject();
                ruleNode.put("role", "system");
                ruleNode.put("content", sb.toString().trim());
            }
        } catch (Exception e) {
            logger.warn("系统规则注入失败: {}", e.getMessage());
        }

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

        // 添加图片识别描述
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

    private List<ChatMessage> getRecentHistory(Long conversationId) {
        List<ChatMessage> history = chatMessageRepository
                .findByConversationIdOrderByTimestampAsc(conversationId);
        if (history.size() > MAX_HISTORY_SIZE) {
            history = history.subList(history.size() - MAX_HISTORY_SIZE, history.size());
        }
        return history;
    }
}
