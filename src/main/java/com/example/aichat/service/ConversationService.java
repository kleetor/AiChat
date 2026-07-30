// service/ConversationService.java
package com.example.aichat.service;

import com.example.aichat.config.BusinessException;
import com.example.aichat.dto.ConversationResponse;
import com.example.aichat.model.Conversation;
import com.example.aichat.model.User;
import com.example.aichat.repository.ConversationRepository;
import com.example.aichat.repository.PromptRepository;
import com.example.aichat.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ConversationService {

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PromptRepository promptRepository;

    private static final int MAX_CONVERSATIONS = 10;

    // 创建新会话（自动设置创建时间和用户）
    @Transactional
    public Conversation createConversation(Long userId, String title, Long promptId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("用户不存在"));
        
        long count = conversationRepository.countByUserIdForUpdate(userId);
        if (count >= MAX_CONVERSATIONS) {
            throw BusinessException.badRequest("对话数量已达上限（最多10条）");
        }
        
        Conversation conv = Conversation.builder()
                .user(user)
                .title(title != null && !title.isBlank() ? title : "新对话")
                .promptId(promptId)
                .build();
        return conversationRepository.save(conv);
    }

    // 获取用户的所有会话（按创建时间倒序），含提示词名称
    public List<ConversationResponse> getConversations(Long userId) {
        List<Conversation> convs = conversationRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return convs.stream().map(conv -> {
            String promptName = null;
            if (conv.getPromptId() != null) {
                promptName = promptRepository.findById(conv.getPromptId())
                        .map(p -> p.getName())
                        .orElse(null);
            }
            return ConversationResponse.builder()
                    .id(conv.getId())
                    .title(conv.getTitle())
                    .promptId(conv.getPromptId())
                    .promptName(promptName)
                    .createdAt(conv.getCreatedAt())
                    .build();
        }).collect(Collectors.toList());
    }

    // 删除会话及其所有消息（需传入 userId 做防御深度校验）
    @Transactional
    public void deleteConversation(Long conversationId, Long userId) {
        if (!belongsToUser(conversationId, userId)) {
            throw BusinessException.forbidden("无权删除此会话");
        }
        conversationRepository.deleteById(conversationId);
    }

    // 检查会话是否属于该用户（可选）
    public boolean belongsToUser(Long conversationId, Long userId) {
        Conversation conv = conversationRepository.findById(conversationId).orElse(null);
        return conv != null && conv.getUser().getId().equals(userId);
    }
}
