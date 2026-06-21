package com.example.aichat.repository;

import com.example.aichat.model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByConversationIdOrderByTimestampAsc(Long conversationId);

    void deleteByConversationId(Long conversationId);

    int countByConversationId(Long conversationId);
}
