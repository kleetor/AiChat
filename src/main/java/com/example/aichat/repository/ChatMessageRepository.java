package com.example.aichat.repository;

import com.example.aichat.model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

// repository/ChatMessageRepository.java（追加方法）
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByUserIdOrderByTimestampAsc(Long userId);

    // 新增：按会话查询，正序排列
    List<ChatMessage> findByConversationIdOrderByTimestampAsc(Long conversationId);

    // 删除会话下的所有消息
    void deleteByConversationId(Long conversationId);
}
