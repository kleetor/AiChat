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
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 消息上下文构建器 —— 从 ChatService 拆分，负责构建 LLM API 的 messages 数组。
 * 按顺序拼接：系统规则 → 自定义提示词 → 长期记忆 → 对话摘要 → 知识库检索 → 历史对话 → 图片引用/描述 → 当前消息
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
    private final SystemRuleRepository systemRuleRepository;
    private final ObjectMapper objectMapper;

    public MessageContextBuilder(ChatMessageRepository chatMessageRepository,
                                  PromptService promptService,
                                  MemoryService memoryService,
                                  ConversationSummaryRepository summaryRepo,
                                  ChromaDBService chromaDBService,
                                  SystemRuleRepository systemRuleRepository) {
        this.chatMessageRepository = chatMessageRepository;
        this.promptService = promptService;
        this.memoryService = memoryService;
        this.summaryRepo = summaryRepo;
        this.chromaDBService = chromaDBService;
        this.systemRuleRepository = systemRuleRepository;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 构建消息数组（含 prompt、长期记忆、知识库、摘要、历史、图片引用、当前消息）。
     *
     * 搜索结果不再在此处注入，改为由 ChatService 根据 modelConfig.supportsToolCalling 决定：
     * - 支持工具调用：通过 tools 参数激活 search_web，LLM 自主决定调用
     * - 不支持工具调用：ChatService 预先调搜索 API，作为 system 消息注入
     */
    public ArrayNode buildMessagesArray(Long conversationId, Long promptId,
                                         String userMessage, boolean webSearchEnabled,
                                         String imageDescription, Long knowledgeBaseId,
                                         Long userId, Boolean longMemoryEnabled,
                                         String imageUrl, String fileUrl) {
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

        // 联网搜索结果不再在此处注入，由 ChatService 处理工具调用逻辑
        // 保留参数 webSearchEnabled 供调用方参考

        // 添加图片引用（工具调用路径）或图片描述（旧路径兼容）
        if (imageUrl != null && !imageUrl.isBlank()) {
            ObjectNode imageRefNode = messagesArray.addObject();
            imageRefNode.put("role", "system");
            imageRefNode.put("content", "用户上传了一张图片，URL: " + imageUrl
                    + "\n如需分析这张图片，请使用 analyze_image 工具。");
        } else if (imageDescription != null && !imageDescription.isBlank()) {
            ObjectNode imageContextNode = messagesArray.addObject();
            imageContextNode.put("role", "system");
            imageContextNode.put("content", imageDescription);
        }

        // 添加文件引用（工具调用路径，供未来扩展更多文件类型工具）
        if (fileUrl != null && !fileUrl.isBlank()) {
            ObjectNode fileRefNode = messagesArray.addObject();
            fileRefNode.put("role", "system");
            fileRefNode.put("content", "用户上传了一个文件，URL: " + fileUrl
                    + "\n请根据文件类型使用相应的工具进行分析。如果是图片，请使用 analyze_image 工具。");
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
