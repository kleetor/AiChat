// service/ChatHistoryService.java（修改后）
package com.example.aichat.service;

import com.example.aichat.config.BusinessException;
import com.example.aichat.dto.ChatHistoryResponse;
import com.example.aichat.model.ChatMessage;
import com.example.aichat.model.Conversation;
import com.example.aichat.repository.ChatMessageRepository;
import com.example.aichat.repository.ConversationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ChatHistoryService {

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    // 保存消息（需传入 conversationId，内部验证会话存在）
    public ChatMessage saveMessage(Long conversationId, Long userId, String userMessage, String aiReply) {
        Conversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> BusinessException.notFound("会话不存在"));
        if (!conv.getUser().getId().equals(userId)) {
            throw BusinessException.forbidden("无权在此会话中保存消息");
        }
        ChatMessage msg = ChatMessage.builder()
                .user(conv.getUser())
                .conversation(conv)
                .userMessage(userMessage)
                .aiReply(aiReply)
                .build();
        return chatMessageRepository.save(msg);
    }

    // 获取指定会话的消息历史
    public ChatHistoryResponse getChatHistoryByConversation(Long conversationId) {
        List<ChatMessage> messages = chatMessageRepository
                .findByConversationIdOrderByTimestampAsc(conversationId);
        List<ChatHistoryResponse.MessageRecord> records = messages.stream()
                .map(m -> {
                    ChatHistoryResponse.MessageRecord record = new ChatHistoryResponse.MessageRecord();
                    record.setId(m.getId());
                    record.setUserMessage(m.getUserMessage());
                    record.setAiReply(m.getAiReply());
                    record.setTimestamp(m.getTimestamp());
                    return record;
                })
                .collect(Collectors.toList());
        ChatHistoryResponse response = new ChatHistoryResponse();
        response.setMessages(records);
        return response;
    }

    // 删除单条消息（需权限校验）
    @Transactional
    public void deleteMessage(Long messageId, Long userId) {
        if (chatMessageRepository.countByIdAndUserId(messageId, userId) == 0) {
            throw BusinessException.forbidden("无权删除此消息");
        }
        chatMessageRepository.deleteById(messageId);
    }
}

