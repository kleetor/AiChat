// service/ConversationService.java
package com.example.aichat.service;

import com.example.aichat.model.Conversation;
import com.example.aichat.model.User;
import com.example.aichat.repository.ConversationRepository;
import com.example.aichat.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ConversationService {

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private UserRepository userRepository;

    private static final int MAX_CONVERSATIONS = 10;

    // 创建新会话（自动设置创建时间和用户）
    public Conversation createConversation(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        
        long count = conversationRepository.countByUserId(userId);
        if (count >= MAX_CONVERSATIONS) {
            throw new RuntimeException("对话数量已达上限（最多10条）");
        }
        
        Conversation conv = Conversation.builder()
                .user(user)
                .title("新对话")
                .build();
        return conversationRepository.save(conv);
    }

    // 获取用户的所有会话（按创建时间倒序）
    public List<Conversation> getConversations(Long userId) {
        return conversationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    // 删除会话及其所有消息
    @Transactional
    public void deleteConversation(Long conversationId) {
        conversationRepository.deleteById(conversationId);
    }

    // 检查会话是否属于该用户（可选）
    public boolean belongsToUser(Long conversationId, Long userId) {
        Conversation conv = conversationRepository.findById(conversationId).orElse(null);
        return conv != null && conv.getUser().getId().equals(userId);
    }
}
