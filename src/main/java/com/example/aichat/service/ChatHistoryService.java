// service/ChatHistoryService.java（修改后）
package com.example.aichat.service;

import com.example.aichat.dto.ChatHistoryResponse;
import com.example.aichat.model.ChatMessage;
import com.example.aichat.model.Conversation;
import com.example.aichat.repository.ChatMessageRepository;
import com.example.aichat.repository.ConversationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ChatHistoryService {

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    // 保存消息（需要传入 conversationId）
    public ChatMessage saveMessage(Long conversationId, String userMessage, String aiReply) {
        Conversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("会话不存在"));
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
    public void deleteMessage(Long messageId, Long userId) {
        ChatMessage msg = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("消息不存在"));
        if (!msg.getUser().getId().equals(userId)) {
            throw new RuntimeException("无权删除此消息");
        }
        chatMessageRepository.delete(msg);
    }
}

