package com.example.aichat.service;

import com.example.aichat.model.ConversationSummary;
import com.example.aichat.repository.ChatMessageRepository;
import com.example.aichat.repository.ConversationSummaryRepository;
import com.example.aichat.model.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 对话摘要服务 (第二层记忆)。
 * 当消息超过阈值时，用 LLM 生成增量摘要。
 */
@Service
public class SummaryService {

    private static final Logger log = LoggerFactory.getLogger(SummaryService.class);

    private final ChatMessageRepository messageRepo;
    private final ConversationSummaryRepository summaryRepo;
    private final LLMService llmService;

    @Value("${summary.trigger.count:20}")
    private int triggerCount;

    @Value("${summary.refresh.interval:10}")
    private int refreshInterval;

    @Value("${summary.keep.recent:10}")
    private int keepRecent;

    public SummaryService(ChatMessageRepository messageRepo,
                          ConversationSummaryRepository summaryRepo,
                          LLMService llmService) {
        this.messageRepo = messageRepo;
        this.summaryRepo = summaryRepo;
        this.llmService = llmService;
    }

    /** 检查并异步生成摘要 */
    @Async
    public void checkAndGenerate(Long conversationId) {
        try {
            int msgCount = messageRepo.countByConversationId(conversationId);
            ConversationSummary latest = summaryRepo.findByConversationId(conversationId);

            boolean shouldGenerate = (latest == null && msgCount >= triggerCount)
                    || (latest != null && msgCount - latest.getMessageCountAtGeneration() >= refreshInterval);

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
            int end = Math.max(0, all.size() - keepRecent);
            if (end <= 0) return;

            List<ChatMessage> toSummarize = all.subList(0, end);
            ConversationSummary prev = summaryRepo.findByConversationId(conversationId);

            StringBuilder prompt = new StringBuilder(
                    "请总结以下对话的关键要点，忽略闲聊，不超过500字。\n" +
                    "- 保留用户的关键信息（姓名、偏好、事实等）\n" +
                    "- 概括 AI 的主要回答要点\n\n");

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
