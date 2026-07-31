package com.example.aichat.service;

import com.example.aichat.config.props.SummaryProperties;
import com.example.aichat.model.Conversation;
import com.example.aichat.model.ConversationSummary;
import com.example.aichat.repository.ChatMessageRepository;
import com.example.aichat.repository.ConversationRepository;
import com.example.aichat.repository.ConversationSummaryRepository;
import com.example.aichat.repository.PromptRepository;
import com.example.aichat.model.ChatMessage;
import com.example.aichat.model.Prompt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 对话摘要服务 (第二层记忆)。
 * 当消息超过阈值时，用 LLM 生成增量摘要。
 */
@Service
public class SummaryService {

    private static final Logger log = LoggerFactory.getLogger(SummaryService.class);

    private final ChatMessageRepository messageRepo;
    private final ConversationSummaryRepository summaryRepo;
    private final ConversationRepository conversationRepo;
    private final PromptRepository promptRepo;
    private final LLMService llmService;
    private final SummaryProperties summaryProperties;

    public SummaryService(ChatMessageRepository messageRepo,
                          ConversationSummaryRepository summaryRepo,
                          ConversationRepository conversationRepo,
                          PromptRepository promptRepo,
                          LLMService llmService,
                          SummaryProperties summaryProperties) {
        this.messageRepo = messageRepo;
        this.summaryRepo = summaryRepo;
        this.conversationRepo = conversationRepo;
        this.promptRepo = promptRepo;
        this.llmService = llmService;
        this.summaryProperties = summaryProperties;
    }

    /** 检查并异步生成摘要 */
    @Async
    public void checkAndGenerate(Long conversationId) {
        try {
            int msgCount = messageRepo.countByConversationId(conversationId);
            ConversationSummary latest = summaryRepo.findByConversationId(conversationId);

            boolean shouldGenerate = (latest == null && msgCount >= summaryProperties.getTrigger().getCount())
                    || (latest != null && msgCount - latest.getMessageCountAtGeneration() >= summaryProperties.getRefresh().getInterval());

            if (shouldGenerate) {
                generate(conversationId, msgCount);
            }
        } catch (Exception e) {
            log.warn("摘要生成检查失败: conversationId={}", conversationId, e);
        }
    }

    private void generate(Long conversationId, int msgCount) {
        try {
            List<ChatMessage> all = messageRepo.findByConversationIdOrderByTimestampAsc(conversationId);
            int end = Math.max(0, all.size() - summaryProperties.getKeep().getRecent());
            if (end <= 0) return;

            List<ChatMessage> toSummarize = all.subList(0, end);
            ConversationSummary prev = summaryRepo.findByConversationId(conversationId);

            // 获取角色名称，用于角色化摘要
            String roleName = conversationRepo.findById(conversationId)
                    .map(Conversation::getPromptId)
                    .flatMap(promptRepo::findById)
                    .map(Prompt::getName)
                    .orElse(null);

            StringBuilder prompt = new StringBuilder();
            if (roleName != null) {
                prompt.append(String.format(
                    "你正在扮演：%s\n\n" +
                    "请以这个角色的视角，用角色的语气总结以下对话的要点：\n" +
                    "- 你们聊了什么？\n" +
                    "- 你对用户的印象有什么变化？\n" +
                    "- 你们之间发生了什么值得记住的事？\n\n" +
                    "保持角色自身的语气和语言风格。不超过300字。\n\n",
                    roleName));
            } else {
                prompt.append("请总结以下对话的关键要点，忽略闲聊，不超过500字。\n" +
                    "- 保留用户的关键信息（姓名、偏好、事实等）\n" +
                    "- 概括 AI 的主要回答要点\n\n");
            }

            if (prev != null) {
                prompt.append("【已有摘要】\n").append(prev.getSummary())
                     .append("\n\n【新增内容】\n");
            }

            for (var m : toSummarize) {
                prompt.append("用户: ").append(m.getUserMessage()).append("\n");
                prompt.append("AI: ").append(m.getAiReply()).append("\n\n");
            }

            String summary = llmService.chatSync(prompt.toString());
            if (summary == null || summary.isBlank()) return;

            summaryRepo.save(ConversationSummary.builder()
                    .conversationId(conversationId)
                    .summary(summary.trim())
                    .messageCountAtGeneration(msgCount)
                    .version(prev != null ? prev.getVersion() + 1 : 1)
                    .updatedAt(LocalDateTime.now())
                    .build());

            log.info("摘要已生成: conversationId={}, msgCount={}, version={}",
                    conversationId, msgCount, prev != null ? prev.getVersion() + 1 : 1);
        } catch (Exception e) {
            log.warn("摘要生成失败: conversationId={}", conversationId, e);
        }
    }
}
